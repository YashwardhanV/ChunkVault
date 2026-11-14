package com.chunkvault.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class StorageLifecycleLock {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public Lock uploadLock() { return lock.readLock(); }
    public Lock garbageCollectionLock() { return lock.writeLock(); }
}
