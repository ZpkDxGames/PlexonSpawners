package com.plexon.spawners.integration.core;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

final class StandaloneCoreBridge implements CoreBridge {
    private final JavaPlugin plugin;
    private final boolean installed;
    private final String pluginVersion;
    private final String detail;
    private final ThreadPoolExecutor io;

    StandaloneCoreBridge(
        final JavaPlugin plugin,
        final boolean installed,
        final String pluginVersion,
        final String detail
    ) {
        this.plugin = plugin;
        this.installed = installed;
        this.pluginVersion = pluginVersion == null || pluginVersion.isBlank() ? "-" : pluginVersion;
        this.detail = detail == null ? "" : detail;
        io = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), runnable -> {
            final Thread thread = new Thread(runnable, "PlexonSpawners-IO");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public boolean installed() { return installed; }
    @Override public boolean available() { return false; }
    @Override public boolean compatible() { return false; }
    @Override public String pluginVersion() { return pluginVersion; }
    @Override public String apiVersion() { return "-"; }
    @Override public String mode() { return "STANDALONE"; }
    @Override public String registrationState() { return installed ? "UNAVAILABLE" : "NOT_INSTALLED"; }
    @Override public String detail() { return detail; }
    @Override public void registerStarting() {}
    @Override public void markReady(final String ignored) {}
    @Override public void markDegraded(final String ignored) {}
    @Override public void markFailed(final String ignored) {}
    @Override public void unregister() {}

    @Override
    public <T> CompletableFuture<T> supplyIo(final Supplier<T> supplier) {
        if (!plugin.isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Plugin is disabled"));
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (!plugin.isEnabled()) throw new IllegalStateException("Plugin disabled before IO task ran");
                return supplier.get();
            }, io);
        } catch (final RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<Void> runIo(final Runnable task) {
        return supplyIo(() -> { task.run(); return null; });
    }

    @Override
    public void runPrimary(final Runnable task) {
        if (!plugin.isEnabled()) return;
        if (org.bukkit.Bukkit.isPrimaryThread()) task.run();
        else plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void schedulePrimary(final Duration delay, final Runnable task) {
        if (!plugin.isEnabled()) return;
        final long ticks = Math.max(1L, (long) Math.ceil(Math.max(0L, delay.toMillis()) / 50.0D));
        plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
    }

    @Override
    public void close() {
        io.shutdownNow();
    }
}
