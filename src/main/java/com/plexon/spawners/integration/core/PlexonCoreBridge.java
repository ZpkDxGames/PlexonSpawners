package com.plexon.spawners.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.time.Instant;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonCoreBridge implements CoreBridge {
    private static final Set<String> CAPABILITIES = Set.of(
        "spawner-engine",
        "managed-spawner-items",
        "spawner-essence",
        "spawner-api",
        "spawner-recovered-event",
        "spawner-placed-event",
        "essence-awarded-event",
        "wildstacker-compat",
        "stateless-runtime"
    );

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean compatible;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    public PlexonCoreBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<PlexonCoreAPI> registration =
            Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        this.core = registration.getProvider();
        this.version = core.version();
        this.compatible = ModuleVersionRange.parse(SUPPORTED_API_RANGE).contains(version);
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
    @Override public String mode() { return compatible && ownsRegistration ? "CORE" : "STANDALONE"; }

    @Override
    public String registrationState() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID).map(descriptor -> descriptor.state().name()).orElse("NOT_REGISTERED");
        }
        return registrationState;
    }

    @Override
    public String detail() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID).map(ModuleDescriptor::detail).orElse(detail);
        }
        return detail;
    }

    @Override
    public void registerStarting() {
        if (!compatible) {
            plugin.getLogger().warning("PlexonCore API " + version.apiVersion()
                + " is incompatible with supported range " + SUPPORTED_API_RANGE
                + "; PlexonSpawners will continue in standalone compatibility mode.");
            return;
        }
        ModuleDescriptor descriptor = new ModuleDescriptor(
            MODULE_ID,
            "PlexonSpawners",
            plugin.getName(),
            plugin.getPluginMeta().getVersion(),
            plugin,
            ModuleVersionRange.parse(SUPPORTED_API_RANGE),
            CAPABILITIES,
            ModuleState.STARTING,
            "Initializing PlexonSpawners",
            Instant.now()
        );
        ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;
        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();
        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning("PlexonCore module registration rejected: " + result.message());
        }
    }

    @Override public void markReady(String detail) { update(ModuleState.READY, detail); }
    @Override public void markDegraded(String detail) { update(ModuleState.DEGRADED, detail); }
    @Override public void markFailed(String detail) { update(ModuleState.FAILED, detail); }

    private void update(ModuleState state, String newDetail) {
        if (!compatible || !ownsRegistration) {
            return;
        }
        if (!core.modules().updateState(MODULE_ID, plugin, state, newDetail)) {
            ownsRegistration = false;
            registrationState = "NOT_OWNER";
            detail = "PlexonCore module ownership changed; state update rejected";
            return;
        }
        registrationState = state.name();
        detail = newDetail == null ? "" : newDetail;
    }

    @Override
    public void unregister() {
        if (!ownsRegistration) {
            return;
        }
        core.modules().unregisterOwnedBy(plugin);
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }
}
