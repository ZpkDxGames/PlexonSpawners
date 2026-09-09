package com.plexon.spawners.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry.IntegrationState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Core-backed PlexonSpawners module bridge. */
public final class PlexonCoreBridge implements CoreBridge {
    private static final String CORE_PLUGIN = "PlexonCore";
    private static final String INTEGRATION_ID = "PLEXON_SPAWNERS";
    private static final Set<String> CAPABILITIES = Set.of(
        "spawner-engine",
        "managed-spawner-items",
        "spawner-essence",
        "spawner-api",
        "spawner-recovered-event",
        "spawner-placed-event",
        "essence-awarded-event",
        "wildstacker-compat",
        "stateless-runtime",
        "core-native-module",
        "core-health-publishing"
    );

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean compatible;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    public PlexonCoreBridge(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");

        final Plugin expectedCore = Bukkit.getPluginManager().getPlugin(CORE_PLUGIN);
        if (expectedCore == null || !expectedCore.isEnabled()) {
            throw new IllegalStateException("PlexonCore must be installed and enabled before PlexonSpawners");
        }

        final RegisteredServiceProvider<PlexonCoreAPI> registration =
            Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        if (registration.getPlugin() != expectedCore) {
            throw new IllegalStateException(
                "PlexonCoreAPI service is owned by unexpected plugin " + registration.getPlugin().getName()
            );
        }

        this.core = Objects.requireNonNull(registration.getProvider(), "PlexonCore API provider");
        this.version = Objects.requireNonNull(core.version(), "PlexonCore version");
        validateCoreServices();

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
    @Override public String mode() { return compatible && ownsRegistration ? "CORE" : "CORE_UNREGISTERED"; }

    @Override
    public String registrationState() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID)
                .filter(descriptor -> descriptor.plugin() == plugin)
                .map(descriptor -> descriptor.state().name())
                .orElse("REGISTRATION_LOST");
        }
        return registrationState;
    }

    @Override
    public String detail() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID)
                .filter(descriptor -> descriptor.plugin() == plugin)
                .map(ModuleDescriptor::detail)
                .orElse("PlexonCore module registration was lost");
        }
        return detail;
    }

    @Override
    public void registerStarting() {
        if (!compatible) {
            throw new IllegalStateException(
                "PlexonCore API " + version.apiVersion() + " is incompatible with " + SUPPORTED_API_RANGE
            );
        }

        final ModuleDescriptor descriptor = descriptor(ModuleState.STARTING, "Initializing PlexonSpawners");
        ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;

        // A hot reload or a previously failed enable can leave a descriptor owned by
        // an old disabled JavaPlugin instance. Reclaim only that exact stale self-entry;
        // never evict an enabled module or another plugin's registration.
        if (!result.success() && !ownsRegistration && isStaleSelfRegistration(registered)) {
            core.modules().unregister(MODULE_ID);
            result = core.modules().register(descriptor);
            registered = result.descriptor();
            ownsRegistration = registered != null && registered.plugin() == plugin;
        }

        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();

        if (!ownsRegistration) {
            throw new IllegalStateException("PlexonCore module registration rejected: " + result.message());
        }
    }

    @Override public void markReady(String detail) { update(ModuleState.READY, detail); }
    @Override public void markDegraded(String detail) { update(ModuleState.DEGRADED, detail); }
    @Override public void markFailed(String detail) { update(ModuleState.FAILED, detail); }

    private void update(ModuleState state, String newDetail) {
        if (!compatible) {
            return;
        }
        if (!ownsCurrentRegistration()) {
            ownsRegistration = false;
            try {
                registerStarting();
            } catch (RuntimeException exception) {
                registrationState = "REGISTRATION_LOST";
                detail = "Could not restore PlexonCore module registration: " + exception.getMessage();
                plugin.getLogger().severe(detail);
                return;
            }
        }

        final String safeDetail = newDetail == null ? "" : newDetail;
        core.modules().updateState(MODULE_ID, state, safeDetail);
        registrationState = state.name();
        detail = safeDetail;
        publishIntegrationHealth(state, safeDetail);
    }

    @Override
    public void unregister() {
        if (!ownsCurrentRegistration()) {
            ownsRegistration = false;
            registrationState = "UNREGISTERED";
            return;
        }

        core.modules().updateState(MODULE_ID, ModuleState.DISABLED, "PlexonSpawners disabled cleanly");
        publishIntegrationHealth(ModuleState.DISABLED, "PlexonSpawners disabled cleanly");
        core.modules().unregister(MODULE_ID);
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }

    private ModuleDescriptor descriptor(ModuleState state, String descriptorDetail) {
        return new ModuleDescriptor(
            MODULE_ID,
            "PlexonSpawners",
            plugin.getName(),
            plugin.getPluginMeta().getVersion(),
            plugin,
            ModuleVersionRange.parse(SUPPORTED_API_RANGE),
            CAPABILITIES,
            state,
            descriptorDetail,
            Instant.now()
        );
    }

    private boolean ownsCurrentRegistration() {
        return ownsRegistration && core.modules().find(MODULE_ID)
            .map(descriptor -> descriptor.plugin() == plugin)
            .orElse(false);
    }

    private boolean isStaleSelfRegistration(ModuleDescriptor registered) {
        return registered != null
            && registered.plugin() != plugin
            && registered.pluginName().equalsIgnoreCase(plugin.getName())
            && !registered.plugin().isEnabled();
    }

    private void validateCoreServices() {
        Objects.requireNonNull(core.modules(), "PlexonCore module registry");
        Objects.requireNonNull(core.integrations(), "PlexonCore integration registry");
        Objects.requireNonNull(core.text(), "PlexonCore text service");
        Objects.requireNonNull(core.gui(), "PlexonCore GUI service");
        Objects.requireNonNull(core.items(), "PlexonCore item service");
        Objects.requireNonNull(core.scheduler(), "PlexonCore scheduler");
        Objects.requireNonNull(core.persistence(), "PlexonCore persistence service");
        Objects.requireNonNull(core.configs(), "PlexonCore config service");
        Objects.requireNonNull(core.diagnostics(), "PlexonCore diagnostics snapshot");
    }

    private void publishIntegrationHealth(ModuleState state, String integrationDetail) {
        final IntegrationState integrationState = switch (state) {
            case READY -> IntegrationState.READY;
            case INCOMPATIBLE -> IntegrationState.INCOMPATIBLE;
            case FAILED -> IntegrationState.FAILED;
            case MISSING -> IntegrationState.MISSING;
            default -> IntegrationState.DEGRADED;
        };
        core.integrations().publish(
            INTEGRATION_ID,
            plugin.getName(),
            plugin.getPluginMeta().getVersion(),
            integrationState,
            CAPABILITIES,
            integrationDetail
        );
    }
}
