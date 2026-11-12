package com.chunkvault.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ActiveChunkPins {
    private final ConcurrentHashMap<String, AtomicInteger> pins = new ConcurrentHashMap<>();

    public void pin(Collection<String> hashes) {
        hashes.forEach(hash -> pins.compute(hash, (ignored, count) -> {
            if (count == null) return new AtomicInteger(1);
            count.incrementAndGet();
            return count;
        }));
    }

    public void unpin(Collection<String> hashes) {
        hashes.forEach(hash -> pins.computeIfPresent(hash, (ignored, count) ->
                count.decrementAndGet() <= 0 ? null : count));
    }

    public boolean isPinned(String hash) {
        AtomicInteger count = pins.get(hash);
        return count != null && count.get() > 0;
    }
}
