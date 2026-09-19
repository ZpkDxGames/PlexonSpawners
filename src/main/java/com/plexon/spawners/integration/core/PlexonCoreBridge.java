package com.plexon.spawners.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

final class PlexonCoreBridge implements CoreBridge {
    private static final Set<String> CAPABILITIES = Set.of(
        "wildstacker-authoritative",
        "spawner-break-policy",
        "spawner-reward-policy",
        "exact-reward-items",
        "admin-policy-gui",
        "policy-api",
        "post-commit-events"
    );

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean compatible;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    PlexonCoreBridge(final JavaPlugin plugin) {
        this.plugin = plugin;
        final RegisteredServiceProvider<PlexonCoreAPI> registration =
            Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) throw new IllegalStateException("PlexonCore API service is not registered");
        core = registration.getProvider();
        version = core.version();
        compatible = ModuleVersionRange.parse(SUPPORTED_API_RANGE).contains(version);
        if (!compatible) {
            registrationState = "INCOMPATIBLE";
            detail = "Core API " + version.apiVersion() + " is outside supported range " + SUPPORTED_API_RANGE;
        }
    }

    @Override public boolean installed() { return true; }
    @Override public boolean available() { return compatible; }
    @Override public boolean compatible() { return compatible; }
    @Override public String pluginVersion() { return version.pluginVersion(); }
    @Override public String apiVersion() { return version.apiVersion(); }
    @Override public String mode() { return compatible && ownsRegistration ? "CORE_2_1" : "STANDALONE"; }
    @Override public String registrationState() { return registrationState; }
    @Override public String detail() { return detail; }

    @Override
    public void registerStarting() {
        if (!compatible) return;
        final ModuleDescriptor descriptor = new ModuleDescriptor(
            MODULE_ID,
            "PlexonSpawners",
            plugin.getName(),
            plugin.getPluginMeta().getVersion(),
            plugin,
            ModuleVersionRange.parse(SUPPORTED_API_RANGE),
            CAPABILITIES,
            ModuleState.STARTING,
            "WildStacker-authoritative policy layer initializing",
            Instant.now());
        final ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        final ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;
        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();
        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning("PlexonCore module registration rejected: " + result.message());
        }
    }

    @Override public void markReady(final String value) { update(ModuleState.READY, value); }
    @Override public void markDegraded(final String value) { update(ModuleState.DEGRADED, value); }
    @Override public void markFailed(final String value) { update(ModuleState.FAILED, value); }

    private void update(final ModuleState state, final String value) {
        if (!compatible || !ownsRegistration) return;
        if (core.modules().updateState(MODULE_ID, plugin, state, value == null ? "" : value)) {
            registrationState = state.name();
            detail = value == null ? "" : value;
        }
    }

    @Override
    public void unregister() {
        if (!ownsRegistration) return;
        core.modules().unregisterOwnedBy(plugin);
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }

    @Override public <T> CompletableFuture<T> supplyIo(final Supplier<T> supplier) {
        return core.scheduler().supplyIo(plugin, supplier);
    }
    @Override public CompletableFuture<Void> runIo(final Runnable task) {
        return core.scheduler().runIo(plugin, task);
    }
    @Override public void runPrimary(final Runnable task) {
        core.scheduler().runPrimary(plugin, task);
    }
    @Override public void schedulePrimary(final Duration delay, final Runnable task) {
        core.scheduler().schedulePrimary(plugin, delay, task);
    }
}
