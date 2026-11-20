import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Activity, AlertTriangle, Blocks, Check, CheckCircle2, ChevronRight, CircleX, Database,
  Download, Files, GitCompareArrows, HardDrive, Menu, Plus, RefreshCw, ShieldCheck,
  Trash2, UploadCloud, X,
} from 'lucide-react'
import { api } from './api'
import type {
  ChunkingStrategy, Comparison, FileDetail, FileSummary, GcResult, GcStatus,
  StorageStats, UploadResult, VerifyResult,
} from './types'

type View = 'files' | 'storage' | 'integrity' | 'gc'

const emptyStats: StorageStats = {
  logicalBytes: 0, physicalBytes: 0, deduplicatedBytes: 0, deduplicationRatio: 0,
  totalFiles: 0, totalFileVersions: 0, totalChunksReferenced: 0, uniqueChunks: 0,
  duplicateChunksAvoided: 0,
}

const emptyGc: GcStatus = {
  candidateChunks: 0, bytesReclaimable: 0, lastRunAt: null,
  lastChunksDeleted: null, lastBytesReclaimed: null, lastFailures: null,
}

function App() {
  const [view, setView] = useState<View>('files')
  const [stats, setStats] = useState(emptyStats)
  const [files, setFiles] = useState<FileSummary[]>([])
  const [gcStatus, setGcStatus] = useState(emptyGc)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<FileDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [mobileNav, setMobileNav] = useState(false)
  const noticeTimer = useRef<number | undefined>(undefined)

  const refresh = useCallback(async () => {
    try {
      const [statsData, fileData, gcData] = await Promise.all([api.stats(), api.files(), api.gcStatus()])
      setStats(statsData)
      setFiles(fileData.content)
      setGcStatus(gcData)
      setError(null)
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => {
    if (!selectedId) { setDetail(null); return }
    let active = true
    api.file(selectedId)
      .then((value) => active && setDetail(value))
      .catch((err) => active && setError(messageOf(err)))
    return () => { active = false }
  }, [selectedId])

  useEffect(() => () => {
    if (noticeTimer.current) window.clearTimeout(noticeTimer.current)
  }, [])

  const showNotice = (message: string) => {
    setNotice(message)
    if (noticeTimer.current) window.clearTimeout(noticeTimer.current)
    noticeTimer.current = window.setTimeout(() => setNotice(null), 4200)
  }

  const nav = (next: View) => {
    setView(next)
    setMobileNav(false)
  }

  return (
    <div className="app-shell relative min-h-screen overflow-x-hidden bg-paper text-ink">
      <div className="ambient ambient-one" aria-hidden="true" />
      <div className="ambient ambient-two" aria-hidden="true" />
      <aside className={`vault-sidebar fixed inset-y-0 left-0 z-40 w-[272px] text-white transition-transform duration-500 ease-out lg:translate-x-0 ${mobileNav ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-full flex-col px-5 py-6">
          <button className="brand-lockup mb-11 flex items-center gap-3 text-left" onClick={() => nav('files')}>
            <span className="brand-mark grid h-11 w-11 place-items-center rounded-[14px] bg-lime text-ink">
              <Blocks size={22} strokeWidth={2.5} />
            </span>
            <span><b className="block text-lg tracking-[-0.03em]">ChunkVault</b><small className="text-white/40">content storage engine</small></span>
          </button>
          <p className="mb-3 px-3 text-[9px] font-bold uppercase tracking-[0.22em] text-white/30">Workspace</p>
          <nav className="space-y-1.5" aria-label="Primary navigation">
            <NavButton active={view === 'files'} icon={<Files size={18} />} label="Files" onClick={() => nav('files')} />
            <NavButton active={view === 'storage'} icon={<Database size={18} />} label="Storage" onClick={() => nav('storage')} />
            <NavButton active={view === 'integrity'} icon={<ShieldCheck size={18} />} label="Integrity" onClick={() => nav('integrity')} />
            <NavButton active={view === 'gc'} icon={<Trash2 size={18} />} label="Garbage collection" onClick={() => nav('gc')} />
          </nav>
          <div className="node-status mt-auto rounded-2xl p-4">
            <div className="mb-3 flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-lime">
              <span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-lime opacity-50" /><span className="relative h-2 w-2 rounded-full bg-lime" /></span> Engine online
            </div>
            <div className="space-y-2 font-mono text-[10px] text-white/[0.42]"><p className="flex justify-between"><span>NODE</span><span className="text-white/70">LOCAL-01</span></p><p className="flex justify-between"><span>HASH</span><span className="text-white/70">SHA-256</span></p><p className="flex justify-between"><span>MODE</span><span className="text-white/70">IMMUTABLE</span></p></div>
          </div>
        </div>
      </aside>

      <main className="relative lg:pl-[272px]">
        <header className="topbar sticky top-0 z-30 flex h-[68px] items-center justify-between px-5 md:px-8">
          <div className="flex items-center gap-3">
            <button className="rounded-lg p-2 hover:bg-ink/5 lg:hidden" onClick={() => setMobileNav(true)} aria-label="Open navigation"><Menu size={21} /></button>
            <div className="hidden items-center gap-2 font-mono text-[10px] uppercase tracking-[0.16em] text-ink/40 sm:flex">
              <HardDrive size={14} /> <span>vault</span><span className="text-ink/20">/</span><span className="text-ink">{view}</span>
            </div>
          </div>
          <div className="flex items-center gap-2"><span className="hidden rounded-full border border-ink/10 bg-white/60 px-3 py-1.5 text-[10px] font-medium text-ink/45 md:inline-flex">Live metadata</span><button onClick={() => void refresh()} className="button-ghost" disabled={loading}><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Sync</button></div>
        </header>

        <div className="mx-auto max-w-[1480px] px-5 py-7 md:px-9 md:py-10">
          {error && <ErrorBanner message={error} onClose={() => setError(null)} />}
          <div key={view} className="view-enter">
            {view === 'files' && <FilesView files={files} stats={stats} loading={loading} onSelect={setSelectedId}
              onUploaded={(result) => { showNotice(`v${result.versionNumber} committed · ${result.duplicateChunksReused} chunks reused`); void refresh() }} />}
            {view === 'storage' && <StorageView stats={stats} />}
            {view === 'integrity' && <IntegrityView onError={(err) => setError(err)} />}
            {view === 'gc' && <GarbageCollectionView status={gcStatus} onComplete={(result) => {
              showNotice(`${result.bytesReclaimed ? formatBytes(result.bytesReclaimed) : 'No bytes'} reclaimed`)
              void refresh()
            }} onError={(err) => setError(err)} />}
          </div>
        </div>
      </main>

      {mobileNav && <button className="overlay-enter fixed inset-0 z-30 bg-black/45 backdrop-blur-sm lg:hidden" onClick={() => setMobileNav(false)} aria-label="Close navigation" />}
      {detail && <FileDrawer detail={detail} onClose={() => setSelectedId(null)} onRefresh={async (version) => {
        const updated = await api.file(detail.id, version)
        setDetail(updated)
        await refresh()
      }} onDeleted={() => { setSelectedId(null); showNotice('Logical file deleted; shared chunks remain protected'); void refresh() }} />}
      {notice && <div className="toast-enter fixed bottom-6 right-6 z-[70] flex max-w-sm items-center gap-3 rounded-2xl border border-white/10 bg-ink px-4 py-3 text-sm text-white shadow-lift"><span className="grid h-7 w-7 place-items-center rounded-full bg-lime text-ink"><Check size={15} /></span>{notice}</div>}
    </div>
  )
}

function FilesView({ files, stats, loading, onSelect, onUploaded }: {
  files: FileSummary[]; stats: StorageStats; loading: boolean
  onSelect: (id: string) => void; onUploaded: (result: UploadResult) => void
}) {
  return <>
    <section className="hero-grid mb-7 grid gap-5 xl:grid-cols-[1.45fr_0.55fr]">
      <div className="hero-panel relative overflow-hidden rounded-[28px] bg-ink p-6 text-white md:p-9">
        <div className="hero-glow" aria-hidden="true" />
        <div className="relative z-10">
          <div className="mb-7 flex flex-wrap items-center gap-2"><span className="hero-badge"><span className="h-1.5 w-1.5 rounded-full bg-lime" /> Content-addressable core</span><span className="hero-badge">Single node</span></div>
          <h1 className="max-w-3xl text-4xl font-semibold leading-[0.98] tracking-[-0.055em] sm:text-5xl lg:text-[64px]">Every byte,<br /><span className="text-lime">exactly once.</span></h1>
          <p className="mt-5 max-w-xl text-sm leading-6 text-white/[0.52] md:text-base">Stream files into immutable SHA-256 chunks. Reuse matching content instantly. Reconstruct every version from a precise manifest.</p>
          <div className="pipeline mt-8 grid grid-cols-4 gap-2" aria-label="Storage pipeline">
            {['Stream', 'Chunk', 'Hash', 'Commit'].map((stage, index) => <div key={stage} className="pipeline-stage" style={{ animationDelay: `${index * 110}ms` }}><span className="font-mono text-[9px] text-white/[0.28]">0{index + 1}</span><b>{stage}</b>{index < 3 ? <i /> : null}</div>)}
          </div>
        </div>
      </div>
      <SavingsDial stats={stats} />
    </section>
    <UploadPanel onUploaded={onUploaded} />
    <section className="panel table-panel mt-7 overflow-hidden">
      <div className="flex items-center justify-between border-b border-ink/10 px-5 py-4 md:px-6">
        <div><p className="eyebrow mb-1">Manifest index</p><h2 className="font-semibold">Logical files</h2><p className="mt-0.5 text-xs text-ink/[0.42]">Newest first · physical contribution is reclaimable-only</p></div>
        <span className="badge">{stats.totalFileVersions} versions</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="text-[11px] uppercase tracking-[0.13em] text-ink/40"><tr><th>File</th><th>Logical size</th><th>Physical contribution</th><th>Versions</th><th>Created</th><th /></tr></thead>
          <tbody>
            {files.map((file, index) => <tr key={file.id} className="file-row group cursor-pointer border-t border-ink/[0.07]" style={{ animationDelay: `${index * 45}ms` }} onClick={() => onSelect(file.id)}>
              <td><div className="flex items-center gap-3"><span className="file-icon grid h-10 w-10 place-items-center rounded-xl bg-mist"><Files size={17} /></span><div><b className="block max-w-[260px] truncate font-medium">{file.filename}</b><small className="font-mono text-ink/35">CV_{file.id.slice(0, 8).toUpperCase()}</small></div></div></td>
              <td>{formatBytes(file.logicalSize)}</td><td>{formatBytes(file.exclusivePhysicalBytes)}</td>
              <td><span className="badge">v{file.latestVersion} · {file.versions}</span></td><td className="text-ink/55">{formatDate(file.createdAt)}</td>
              <td>
                <button
                  className="rounded-full p-2 text-ink/25 transition hover:bg-ink hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lime"
                  aria-label={`Open ${file.filename}`}
                  onClick={(event) => { event.stopPropagation(); onSelect(file.id) }}
                >
                  <ChevronRight size={18} className="transition group-hover:translate-x-1" />
                </button>
              </td>
            </tr>)}
            {!loading && files.length === 0 && <tr><td colSpan={6} className="py-14 text-center"><Blocks className="mx-auto mb-3 text-ink/20" size={34} /><b className="block">No manifests yet</b><span className="text-ink/45">Upload a file, then upload it again to watch deduplication happen.</span></td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  </>
}

function UploadPanel({ onUploaded, fileId }: { onUploaded: (result: UploadResult) => void; fileId?: string }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [strategy, setStrategy] = useState<ChunkingStrategy>('FIXED_SIZE')
  const [busy, setBusy] = useState(false)
  const [drag, setDrag] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  const upload = async () => {
    if (!file) return
    setBusy(true)
    setUploadError(null)
    try {
      const result = await api.upload(file, strategy, fileId)
      setFile(null)
      if (input.current) input.current.value = ''
      onUploaded(result)
    } catch (err) {
      setUploadError(messageOf(err))
    } finally { setBusy(false) }
  }

  return <section className={`upload-zone relative overflow-hidden rounded-[22px] p-5 md:p-6 ${drag ? 'is-dragging' : ''} ${busy ? 'is-busy' : ''}`}
    onDragOver={(event) => { event.preventDefault(); setDrag(true) }} onDragLeave={() => setDrag(false)}
    onDrop={(event) => { event.preventDefault(); setDrag(false); setFile(event.dataTransfer.files[0] ?? null) }}>
    {busy ? <div className="upload-progress" aria-hidden="true"><i /></div> : null}
    <div className="flex flex-col gap-5 md:flex-row md:items-center">
      <button onClick={() => input.current?.click()} className="upload-trigger grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-ink text-lime" aria-label="Choose file"><UploadCloud size={24} /></button>
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-center gap-2"><h2 className="font-semibold">{file ? file.name : fileId ? 'Add a new version' : 'Ingest a file'}</h2>{file ? <span className="ready-pill">Ready</span> : null}</div>
        <p className="truncate text-sm text-ink/[0.48]">{file ? `${formatBytes(file.size)} · ready for ${strategy === 'FIXED_SIZE' ? 'fixed 4 MiB' : 'content-defined'} chunking` : 'Drop a file or browse · streamed with bounded memory'}</p>
        <input ref={input} className="sr-only" type="file" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <div className="segmented" aria-label="Chunking strategy">
          <button className={strategy === 'FIXED_SIZE' ? 'active' : ''} onClick={() => setStrategy('FIXED_SIZE')}>Fixed 4 MiB</button>
          <button className={strategy === 'CONTENT_DEFINED' ? 'active' : ''} onClick={() => setStrategy('CONTENT_DEFINED')}>Content-defined</button>
        </div>
        <button className="button-primary" onClick={() => void upload()} disabled={!file || busy}>
          {busy ? <RefreshCw className="animate-spin" size={16} /> : <Plus size={16} />}{busy ? 'Hashing & committing…' : fileId ? 'Create version' : 'Store file'}
        </button>
      </div>
    </div>
    {uploadError && <p className="mt-4 flex items-center gap-2 text-xs text-red-700"><AlertTriangle size={14} />{uploadError}</p>}
  </section>
}

function StorageView({ stats }: { stats: StorageStats }) {
  const saving = stats.logicalBytes ? Math.max(0, Math.min(100, (stats.deduplicatedBytes / stats.logicalBytes) * 100)) : 0
  return <>
    <PageHeading eyebrow="Storage accounting" title="Logical demand versus physical reality." description="These figures are computed from live file-version manifests and unique chunk rows—not client-side estimates." />
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <MetricCard label="Logical storage" value={formatBytes(stats.logicalBytes)} detail={`${stats.totalFileVersions} represented versions`} icon={<Files />} tone="dark" />
      <MetricCard label="Physical storage" value={formatBytes(stats.physicalBytes)} detail={`${stats.uniqueChunks} SHA-256 chunks`} icon={<HardDrive />} />
      <MetricCard label="Deduplicated" value={formatBytes(stats.deduplicatedBytes)} detail={`${saving.toFixed(1)}% avoided`} icon={<Blocks />} tone="lime" />
      <MetricCard label="Dedup ratio" value={`${stats.deduplicationRatio.toFixed(2)}×`} detail="logical ÷ physical" icon={<Activity />} />
    </div>
    <section className="mt-6 grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
      <div className="panel p-6 md:p-8">
        <div className="flex items-end justify-between"><div><p className="eyebrow">Byte efficiency</p><h2 className="text-2xl font-semibold tracking-tight">{saving.toFixed(1)}% of logical bytes avoided</h2></div><span className="font-mono text-sm">{stats.duplicateChunksAvoided} duplicate refs</span></div>
        <div className="mt-8 h-10 overflow-hidden rounded-xl bg-mist"><div className="h-full bg-coral transition-all" style={{ width: `${100 - saving}%` }} /></div>
        <div className="mt-3 flex justify-between text-xs text-ink/50"><span>physical {formatBytes(stats.physicalBytes)}</span><span>deduplicated {formatBytes(stats.deduplicatedBytes)}</span></div>
        <div className="mt-8 rounded-xl bg-ink px-5 py-4 font-mono text-sm text-white"><span className="text-lime">ratio</span> = logicalBytes / physicalBytes</div>
      </div>
      <div className="panel p-6">
        <p className="eyebrow">Manifest graph</p><h2 className="mb-6 text-xl font-semibold">One identity, many references</h2>
        <div className="space-y-3 text-sm">
          <FlowRow label="File versions" value={stats.totalFileVersions} color="bg-coral" />
          <FlowRow label="Chunk references" value={stats.totalChunksReferenced} color="bg-ink" />
          <FlowRow label="Unique chunks" value={stats.uniqueChunks} color="bg-lime" />
        </div>
        <p className="mt-6 border-t border-ink/10 pt-5 text-xs leading-5 text-ink/50">Deletion removes manifest edges. Chunk bytes remain until the reference query proves zero and GC rechecks under lock.</p>
      </div>
    </section>
  </>
}

function IntegrityView({ onError }: { onError: (message: string) => void }) {
  const [result, setResult] = useState<VerifyResult | null>(null)
  const [busy, setBusy] = useState(false)
  const verify = async () => {
    setBusy(true)
    try { setResult(await api.verifyAll()) } catch (err) { onError(messageOf(err)) } finally { setBusy(false) }
  }
  return <>
    <PageHeading eyebrow="Integrity verification" title="Trust the digest, then verify it." description="The scanner reads every selected canonical file, computes SHA-256 again, and compares both length and digest with metadata." action={<button className="button-primary" onClick={() => void verify()} disabled={busy}>{busy ? <RefreshCw className="animate-spin" size={16} /> : <ShieldCheck size={16} />}Verify all chunks</button>} />
    <div className="grid gap-4 md:grid-cols-3">
      <StatusCard label="Healthy" value={result?.healthy ?? 0} icon={<CheckCircle2 />} className="text-emerald-700" />
      <StatusCard label="Missing" value={result?.missing ?? 0} icon={<AlertTriangle />} className="text-amber-700" />
      <StatusCard label="Corrupted" value={result?.corrupted ?? 0} icon={<CircleX />} className="text-red-700" />
    </div>
    <section className="panel mt-6 overflow-hidden">
      <div className="border-b border-ink/10 px-6 py-5"><h2 className="font-semibold">Verification ledger</h2><p className="text-xs text-ink/45">{result ? `Completed ${formatDate(result.completedAt)}` : 'No scan run in this browser session'}</p></div>
      {result?.items.length ? <div className="divide-y divide-ink/[0.07]">{result.items.map((item) => <div key={item.target} className="grid gap-3 px-6 py-4 text-sm md:grid-cols-[140px_1fr_120px] md:items-center"><span className={`w-fit rounded-full px-2.5 py-1 text-[11px] font-bold ${statusStyle(item.status)}`}>{item.status}</span><div className="min-w-0"><p className="truncate font-mono text-xs">{item.target}</p><p className="mt-1 text-xs text-ink/45">{item.message}</p></div><span className="text-right text-xs text-ink/50">{formatBytes(item.size)}</span></div>)}</div>
        : <EmptyState icon={<ShieldCheck />} title="No integrity evidence yet" body="Run a scan to compare stored bytes with their content addresses." />}
    </section>
  </>
}

function GarbageCollectionView({ status, onComplete, onError }: { status: GcStatus; onComplete: (result: GcResult) => void; onError: (message: string) => void }) {
  const [busy, setBusy] = useState(false)
  const collect = async () => {
    setBusy(true)
    try { onComplete(await api.gc()) } catch (err) { onError(messageOf(err)) } finally { setBusy(false) }
  }
  return <>
    <PageHeading eyebrow="Reference-safe cleanup" title="Reclaim only what no manifest can reach." description="GC rechecks each zero-reference candidate inside a transaction, deletes metadata, then removes physical bytes. Uploads and GC cannot overlap on this node." action={<button className="button-primary" onClick={() => void collect()} disabled={busy}>{busy ? <RefreshCw className="animate-spin" size={16} /> : <Trash2 size={16} />}Run collection</button>} />
    <div className="grid gap-4 md:grid-cols-3">
      <MetricCard label="Candidate chunks" value={status.candidateChunks.toLocaleString()} detail="zero relational references" icon={<Blocks />} tone="dark" />
      <MetricCard label="Bytes reclaimable" value={formatBytes(status.bytesReclaimable)} detail="managed chunk metadata" icon={<HardDrive />} tone="lime" />
      <MetricCard label="Last run" value={status.lastRunAt ? formatRelative(status.lastRunAt) : 'Never'} detail={status.lastFailures ? `${status.lastFailures} filesystem failures` : 'no recorded failures'} icon={<Activity />} />
    </div>
    <section className="panel mt-6 p-6 md:p-8"><p className="eyebrow">Safety sequence</p><div className="mt-5 grid gap-3 md:grid-cols-5">{['Find zero refs', 'Acquire row lock', 'Count again', 'Commit metadata', 'Delete bytes'].map((step, index) => <div key={step} className="relative rounded-xl bg-mist p-4"><span className="mb-6 block font-mono text-xs text-ink/35">0{index + 1}</span><b className="text-sm">{step}</b>{index < 4 && <ChevronRight className="absolute -right-5 top-1/2 z-10 hidden -translate-y-1/2 text-ink/25 md:block" />}</div>)}</div><p className="mt-6 text-sm leading-6 text-ink/55">If filesystem deletion fails after metadata commits, the leftover file is an untracked orphan—not lost referenced data. The next orphan sweep retries it after the grace period.</p></section>
  </>
}

function FileDrawer({ detail, onClose, onRefresh, onDeleted }: { detail: FileDetail; onClose: () => void; onRefresh: (version?: number) => Promise<void>; onDeleted: () => void }) {
  const [tab, setTab] = useState<'manifest' | 'versions'>('manifest')
  const [comparison, setComparison] = useState<Comparison | null>(null)
  const [error, setError] = useState<string | null>(null)
  const versionNumbers = detail.versions.map((version) => version.versionNumber)
  const from = versionNumbers[1]
  const to = versionNumbers[0]
  useEffect(() => {
    let active = true
    if (tab === 'versions' && from && to) {
      api.compare(detail.id, from, to)
        .then((value) => active && setComparison(value))
        .catch((err) => active && setError(messageOf(err)))
    } else {
      setComparison(null)
    }
    return () => { active = false }
  }, [detail.id, from, tab, to])

  return <div className="drawer-overlay fixed inset-0 z-50 flex justify-end bg-ink/45 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="File details">
    <button className="absolute inset-0" onClick={onClose} aria-label="Close file details" />
    <aside className="drawer-panel relative h-full w-full max-w-3xl overflow-y-auto bg-paper shadow-2xl">
      <div className="sticky top-0 z-10 flex items-center justify-between border-b border-ink/10 bg-paper/90 px-5 py-4 backdrop-blur-xl md:px-7"><div className="min-w-0"><p className="eyebrow">File manifest</p><h2 className="truncate text-xl font-semibold tracking-tight">{detail.filename}</h2></div><button className="close-button rounded-xl p-2" onClick={onClose} aria-label="Close drawer"><X size={20} /></button></div>
      <div className="space-y-6 p-5 md:p-7">
        {error && <ErrorBanner message={error} onClose={() => setError(null)} />}
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4"><MiniStat label="Version" value={`v${detail.selectedVersion.versionNumber}`} /><MiniStat label="Logical" value={formatBytes(detail.selectedVersion.totalSize)} /><MiniStat label="Chunks" value={detail.selectedVersion.chunkCount.toString()} /><MiniStat label="Strategy" value={detail.selectedVersion.chunkingStrategy === 'FIXED_SIZE' ? 'Fixed' : 'CDC'} /></div>
        <div className="rounded-xl bg-ink p-4 text-white"><p className="mb-3 text-[10px] uppercase tracking-[0.15em] text-white/40">File SHA-256</p><p className="break-all font-mono text-xs text-lime">{detail.selectedVersion.fileHash}</p></div>
        <div className="flex gap-1 rounded-xl bg-mist p-1"><button className={`drawer-tab ${tab === 'manifest' ? 'active' : ''}`} onClick={() => setTab('manifest')}>Chunk manifest</button><button className={`drawer-tab ${tab === 'versions' ? 'active' : ''}`} onClick={() => setTab('versions')}>Version reuse</button></div>
        {tab === 'manifest' ? <>
          <ChunkStrip chunks={detail.manifest.map((chunk) => ({ hash: chunk.hash, reused: chunk.shared }))} />
          <div className="overflow-hidden rounded-xl border border-ink/10 bg-white/60"><div className="grid grid-cols-[48px_1fr_80px_80px] gap-3 border-b border-ink/10 px-4 py-3 text-[10px] uppercase tracking-wider text-ink/40"><span>#</span><span>Content address</span><span>Size</span><span>Refs</span></div>{detail.manifest.map((chunk) => <div key={chunk.index} className="grid grid-cols-[48px_1fr_80px_80px] gap-3 border-b border-ink/[0.06] px-4 py-3 text-xs last:border-0"><span className="font-mono text-ink/35">{String(chunk.index).padStart(2, '0')}</span><span className="truncate font-mono">{chunk.hash}</span><span>{formatBytes(chunk.size)}</span><span><span className={chunk.shared ? 'text-coral' : 'text-ink/45'}>{chunk.referenceCount}×</span></span></div>)}</div>
        </> : <VersionPanel detail={detail} comparison={comparison} onSelect={(version) => void onRefresh(version)} />}
        <div className="border-t border-ink/10 pt-6"><UploadPanel fileId={detail.id} onUploaded={(result) => { void onRefresh(result.versionNumber) }} /></div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-ink/10 pt-6"><a className="button-primary" href={api.downloadUrl(detail.id, detail.selectedVersion.versionNumber)}><Download size={16} />Download v{detail.selectedVersion.versionNumber}</a><button className="button-danger" onClick={async () => { if (window.confirm('Delete this logical file and every version? Shared chunks will remain.')) { await api.delete(detail.id); onDeleted() } }}><Trash2 size={15} />Delete logical file</button></div>
      </div>
    </aside>
  </div>
}

function VersionPanel({ detail, comparison, onSelect }: { detail: FileDetail; comparison: Comparison | null; onSelect: (version: number) => void }) {
  return <div className="space-y-5">
    <div className="flex gap-2 overflow-x-auto pb-1">{detail.versions.map((version) => <button key={version.id} className={`shrink-0 rounded-xl border px-4 py-3 text-left ${version.versionNumber === detail.selectedVersion.versionNumber ? 'border-ink bg-ink text-white' : 'border-ink/10 bg-white/60'}`} onClick={() => onSelect(version.versionNumber)}><b className="block text-sm">Version {version.versionNumber}</b><small className="opacity-55">{version.chunkCount} chunks · {formatBytes(version.totalSize)}</small></button>)}</div>
    {comparison ? <div className="rounded-xl border border-ink/10 bg-white/60 p-5"><div className="mb-5 flex items-center justify-between"><div><p className="eyebrow">v{comparison.fromVersion} → v{comparison.toVersion}</p><h3 className="text-xl font-semibold">{comparison.reusedChunks}/{comparison.totalTargetChunks} chunks reused</h3></div><GitCompareArrows className="text-coral" /></div><ChunkStrip chunks={comparison.targetManifest.map((chunk) => ({ hash: chunk.hash, reused: chunk.reused }))} /><div className="mt-4 h-2 overflow-hidden rounded-full bg-mist"><div className="h-full bg-lime" style={{ width: `${comparison.reuseRatio * 100}%` }} /></div><p className="mt-2 text-right text-xs text-ink/45">{(comparison.reuseRatio * 100).toFixed(1)}% manifest reuse</p></div> : <EmptyState icon={<GitCompareArrows />} title="One version only" body="Add another version to compare chunk reuse." />}
  </div>
}

function ChunkStrip({ chunks }: { chunks: Array<{ hash: string; reused: boolean }> }) {
  return <div><div className="mb-2 flex items-center gap-4 text-[10px] uppercase tracking-wider text-ink/40"><span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-sm bg-lime" />shared/reused</span><span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-sm bg-coral" />unique/changed</span></div><div className="chunk-strip flex min-h-14 gap-1 overflow-x-auto rounded-xl bg-ink p-2">{chunks.map((chunk, index) => <div key={`${chunk.hash}-${index}`} title={`chunk ${index}: ${chunk.hash}`} style={{ animationDelay: `${index * 28}ms` }} className={`chunk-cell min-w-8 flex-1 rounded-md ${chunk.reused ? 'bg-lime' : 'bg-coral'}`}><span className="sr-only">Chunk {index}</span></div>)}</div></div>
}

function SavingsDial({ stats }: { stats: StorageStats }) {
  const saved = stats.logicalBytes ? Math.max(0, (stats.deduplicatedBytes / stats.logicalBytes) * 100) : 0
  return <div className="efficiency-card panel relative overflow-hidden p-6"><div className="orbit-ring" aria-hidden="true"><i /></div><p className="eyebrow">Live efficiency</p><div className="relative mt-7"><div className="text-5xl font-semibold tracking-[-0.06em]">{saved.toFixed(1)}<span className="text-2xl text-ink/[0.28]">%</span></div><p className="mt-1 text-xs uppercase tracking-[0.13em] text-ink/[0.42]">logical bytes avoided</p></div><div className="efficiency-track mt-7"><i style={{ width: `${Math.min(100, saved)}%` }} /></div><div className="mt-6 grid grid-cols-2 gap-3 border-t border-ink/10 pt-4"><MiniStat label="Logical" value={formatBytes(stats.logicalBytes)} /><MiniStat label="Physical" value={formatBytes(stats.physicalBytes)} /></div></div>
}

function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="mb-8 flex flex-col justify-between gap-5 md:flex-row md:items-end"><div><p className="eyebrow">{eyebrow}</p><h1 className="max-w-4xl text-4xl font-semibold tracking-[-0.04em] md:text-5xl">{title}</h1><p className="mt-4 max-w-2xl text-sm leading-6 text-ink/55">{description}</p></div>{action}</div>
}

function MetricCard({ label, value, detail, icon, tone }: { label: string; value: string; detail: string; icon: React.ReactNode; tone?: 'dark' | 'lime' }) {
  return <div className={`metric-card rounded-2xl border p-5 ${tone === 'dark' ? 'border-ink bg-ink text-white' : tone === 'lime' ? 'border-lime bg-lime' : 'border-ink/10 bg-white/60'}`}><div className="mb-8 flex items-center justify-between"><span className={`text-[10px] font-semibold uppercase tracking-[0.14em] ${tone === 'dark' ? 'text-white/45' : 'text-ink/[0.42]'}`}>{label}</span><span className={`metric-icon grid h-9 w-9 place-items-center rounded-xl [&>svg]:h-4 [&>svg]:w-4 ${tone === 'dark' ? 'bg-white/10 text-lime' : 'bg-ink/5'}`}>{icon}</span></div><strong className="block text-3xl tracking-[-0.04em]">{value}</strong><span className={`mt-1 block text-xs ${tone === 'dark' ? 'text-white/[0.42]' : 'text-ink/[0.42]'}`}>{detail}</span></div>
}

function StatusCard({ label, value, icon, className }: { label: string; value: number; icon: React.ReactNode; className: string }) { return <div className="status-card panel flex items-center gap-4 p-5"><span className={`grid h-12 w-12 place-items-center rounded-xl bg-current/10 [&>svg]:h-5 [&>svg]:w-5 ${className}`}>{icon}</span><div><strong className="block text-2xl">{value}</strong><span className="text-[10px] uppercase tracking-[0.14em] text-ink/[0.42]">{label}</span></div></div> }
function MiniStat({ label, value }: { label: string; value: string }) { return <div><span className="block text-[10px] uppercase tracking-wider text-ink/40">{label}</span><b className="mt-1 block truncate text-sm">{value}</b></div> }
function FlowRow({ label, value, color }: { label: string; value: number; color: string }) { return <div className="flow-row flex items-center gap-3 rounded-xl bg-mist p-3"><span className={`h-3 w-3 rounded-sm ${color}`} /><span className="flex-1">{label}</span><b className="font-mono">{value}</b></div> }
function NavButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) { return <button className={`nav-button relative flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm ${active ? 'active text-white' : 'text-white/[0.48] hover:text-white'}`} onClick={onClick}>{icon}<span className="flex-1 text-left font-medium">{label}</span>{active ? <span className="h-1.5 w-1.5 rounded-full bg-lime shadow-[0_0_12px_#a8ff4f]" /> : null}</button> }
function ErrorBanner({ message, onClose }: { message: string; onClose: () => void }) { return <div className="mb-5 flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"><AlertTriangle size={17} /><span className="flex-1">{message}</span><button onClick={onClose}><X size={16} /></button></div> }
function EmptyState({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) { return <div className="py-14 text-center text-ink/40"><span className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-xl bg-mist [&>svg]:h-5 [&>svg]:w-5">{icon}</span><b className="block text-ink">{title}</b><p className="mt-1 text-sm">{body}</p></div> }

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const index = Math.min(Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
function formatRelative(value: string) { const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000); const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' }); if (Math.abs(seconds) < 60) return formatter.format(seconds, 'second'); if (Math.abs(seconds) < 3600) return formatter.format(Math.round(seconds / 60), 'minute'); return formatter.format(Math.round(seconds / 3600), 'hour') }
function statusStyle(status: string) { return status === 'HEALTHY' ? 'bg-emerald-100 text-emerald-800' : status === 'MISSING' ? 'bg-amber-100 text-amber-800' : 'bg-red-100 text-red-800' }
function messageOf(error: unknown) { return error instanceof Error ? error.message : 'Unexpected request failure' }

export default App
