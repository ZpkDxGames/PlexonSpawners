package com.plexon.spawners.config;

import java.util.concurrent.atomic.AtomicLong;

public final class ConfigRevisionService {
    private final AtomicLong revision = new AtomicLong(1L);

    public long current() {
        return revision.get();
    }

    public long bump() {
        return revision.incrementAndGet();
    }
}
