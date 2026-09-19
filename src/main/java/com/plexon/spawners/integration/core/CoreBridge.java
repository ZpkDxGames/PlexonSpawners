package com.plexon.spawners.integration.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface CoreBridge extends AutoCloseable {
    String SUPPORTED_API_RANGE = ">=2.1 <3.0";
    String MODULE_ID = "spawners";

    boolean installed();
    boolean available();
    boolean compatible();
    String pluginVersion();
    String apiVersion();
    String mode();
    String registrationState();
    String detail();

    void registerStarting();
    void markReady(String detail);
    void markDegraded(String detail);
    void markFailed(String detail);
    void unregister();

    <T> CompletableFuture<T> supplyIo(Supplier<T> supplier);
    CompletableFuture<Void> runIo(Runnable task);
    void runPrimary(Runnable task);
    void schedulePrimary(Duration delay, Runnable task);

    @Override
    default void close() {
        unregister();
    }
}
