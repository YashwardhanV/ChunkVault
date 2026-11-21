#!/usr/bin/env python3
"""Reproducible local CAS benchmark for ChunkVault datasets.

This deliberately exercises the same chunk-boundary, SHA-256 addressing,
manifest reconstruction, and one-file-per-hash semantics without HTTP or SQL.
It reports algorithm/storage throughput, not production capacity.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import platform
import random
import resource
import shutil
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable


@dataclass(frozen=True)
class Result:
    dataset: str
    strategy: str
    logical: int
    physical: int
    unique: int
    references: int
    duplicates: int
    upload_mib_s: float
    download_mib_s: float
    peak_rss_mib: float


GEAR: list[int] = []
_state = 0x4D595DF4D0F33173
for _ in range(256):
    _state = (_state + 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
    value = _state
    value = ((value ^ (value >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    value = ((value ^ (value >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    GEAR.append((value ^ (value >> 31)) & 0xFFFFFFFFFFFFFFFF)


def fixed_chunks(path: Path, size: int) -> Iterable[bytes]:
    with path.open("rb") as source:
        while chunk := source.read(size):
            yield chunk


def gear_chunks(path: Path, minimum: int, target: int, maximum: int) -> Iterable[bytes]:
    mask = target - 1
    chunk = bytearray()
    fingerprint = 0
    with path.open("rb") as source:
        while block := source.read(64 * 1024):
            for byte in block:
                chunk.append(byte)
                fingerprint = ((fingerprint << 1) + GEAR[byte]) & 0xFFFFFFFFFFFFFFFF
                if len(chunk) >= maximum or (len(chunk) >= minimum and fingerprint & mask == 0):
                    yield bytes(chunk)
                    chunk.clear()
                    fingerprint = 0
    if chunk:
        yield bytes(chunk)


def benchmark(name: str, files: list[Path], strategy: str, store: Path,
              chunker: Callable[[Path], Iterable[bytes]]) -> Result:
    shutil.rmtree(store, ignore_errors=True)
    store.mkdir(parents=True)
    manifests: list[list[tuple[str, int]]] = []
    logical = sum(path.stat().st_size for path in files)
    references = 0
    upload_started = time.perf_counter()
    for path in files:
        manifest: list[tuple[str, int]] = []
        for chunk in chunker(path):
            digest = hashlib.sha256(chunk).hexdigest()
            canonical = store / digest[:2] / digest[2:4] / digest
            if not canonical.exists():
                canonical.parent.mkdir(parents=True, exist_ok=True)
                temporary = canonical.with_suffix(".part")
                with temporary.open("xb") as target:
                    target.write(chunk)
                    target.flush()
                    os.fsync(target.fileno())
                os.replace(temporary, canonical)
            manifest.append((digest, len(chunk)))
            references += 1
        manifests.append(manifest)
    upload_seconds = time.perf_counter() - upload_started

    canonical_files = [path for path in store.glob("*/*/*") if path.is_file()]
    physical = sum(path.stat().st_size for path in canonical_files)
    download_started = time.perf_counter()
    for source, manifest in zip(files, manifests):
        reconstructed = hashlib.sha256()
        for digest, _ in manifest:
            with (store / digest[:2] / digest[2:4] / digest).open("rb") as chunk:
                while block := chunk.read(64 * 1024):
                    reconstructed.update(block)
        original = hashlib.sha256(source.read_bytes()).hexdigest()
        if reconstructed.hexdigest() != original:
            raise RuntimeError(f"reconstruction mismatch for {source}")
    download_seconds = time.perf_counter() - download_started
    unique = len(canonical_files)
    peak_rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
    return Result(name, strategy, logical, physical, unique, references, references - unique,
                  logical / 1024 / 1024 / upload_seconds, logical / 1024 / 1024 / download_seconds, peak_rss)


def deterministic_bytes(size: int, seed: int) -> bytes:
    return random.Random(seed).randbytes(size)


def build_datasets(root: Path, file_size: int, chunk_size: int) -> dict[str, list[Path]]:
    root.mkdir(parents=True, exist_ok=True)
    datasets: dict[str, list[Path]] = {}

    identical = root / "A-identical"
    identical.mkdir()
    base = deterministic_bytes(file_size, 101)
    datasets["A — identical files"] = []
    for index in range(4):
        path = identical / f"copy-{index}.bin"
        path.write_bytes(base)
        datasets["A — identical files"].append(path)

    overlap = root / "B-overlap"
    overlap.mkdir()
    shared = [deterministic_bytes(chunk_size, 200 + index) for index in range(file_size // chunk_size)]
    datasets["B — partial overlap"] = []
    for file_index in range(4):
        path = overlap / f"overlap-{file_index}.bin"
        blocks = list(shared)
        for position in range(5, len(blocks)):
            blocks[position] = deterministic_bytes(chunk_size, 3000 + file_index * 100 + position)
        path.write_bytes(b"".join(blocks))
        datasets["B — partial overlap"].append(path)

    versions = root / "C-versions"
    versions.mkdir()
    datasets["C — small insertions"] = []
    current = base
    for version in range(4):
        if version:
            insertion = deterministic_bytes(4096, 4000 + version)
            position = chunk_size // 2 + version * 131
            current = current[:position] + insertion + current[position:]
        path = versions / f"version-{version + 1}.bin"
        path.write_bytes(current)
        datasets["C — small insertions"].append(path)

    unrelated = root / "D-unrelated"
    unrelated.mkdir()
    datasets["D — unrelated random"] = []
    for index in range(4):
        path = unrelated / f"random-{index}.bin"
        path.write_bytes(deterministic_bytes(file_size, 5000 + index))
        datasets["D — unrelated random"].append(path)
    return datasets


def bytes_label(value: int) -> str:
    return f"{value / 1024 / 1024:.2f} MiB"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file-mib", type=int, default=8, help="MiB per base file (default: 8)")
    parser.add_argument("--chunk-mib", type=int, default=1, help="fixed/target MiB (default: 1)")
    args = parser.parse_args()
    file_size = args.file_mib * 1024 * 1024
    chunk_size = args.chunk_mib * 1024 * 1024
    with tempfile.TemporaryDirectory(prefix="chunkvault-benchmark-") as temporary:
        root = Path(temporary)
        datasets = build_datasets(root / "datasets", file_size, chunk_size)
        store = root / "store"
        results: list[Result] = []
        for name, paths in datasets.items():
            results.append(benchmark(name, paths, "FIXED_SIZE", store,
                                     lambda path, s=chunk_size: fixed_chunks(path, s)))
            if name.startswith("C"):
                results.append(benchmark(name, paths, "CONTENT_DEFINED", store,
                                         lambda path, s=chunk_size: gear_chunks(path, s // 4, s, s * 2)))

    print("| Dataset | Strategy | Logical | Physical | Unique chunks | Duplicate chunks avoided | Upload MiB/s | Download MiB/s | Peak RSS MiB | Ratio |")
    print("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for result in results:
        ratio = result.logical / result.physical if result.physical else 0
        print(f"| {result.dataset} | {result.strategy} | {bytes_label(result.logical)} | "
              f"{bytes_label(result.physical)} | {result.unique} | {result.duplicates} | "
              f"{result.upload_mib_s:.2f} | {result.download_mib_s:.2f} | {result.peak_rss_mib:.1f} | {ratio:.3f}× |")
    print(f"\nEnvironment: Python {platform.python_version()}, {platform.platform()}, "
          f"file={args.file_mib} MiB, fixed/target chunk={args.chunk_mib} MiB.")


if __name__ == "__main__":
    main()
