package com.plexon.spawners.managed;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NativeStackSettings;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.plugin.java.JavaPlugin;

/** Idempotent ownership handoff for legacy WildStacker-managed spawner counts. */
public final class SpawnerMigrationService {
    private final JavaPlugin plugin;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final SpawnerTuning tuning;
    private final NativeStackSettings settings;
    private final WildStackerCompat wildStacker;
    private boolean startupWarningLogged;

    public SpawnerMigrationService(
        final JavaPlugin plugin,
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService,
        final SpawnerTuning tuning,
        final NativeStackSettings settings,
        final WildStackerCompat wildStacker
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.stateService = stateService;
        this.tuning = tuning;
        this.settings = settings;
        this.wildStacker = wildStacker;
    }

    public ManagedSpawner reconcile(final ManagedSpawner input, final World world) {
        if (input == null || world == null || !settings.enabled() || !settings.migrationEnabled()) {
            return input;
        }
        if (input.migrationState() == SpawnerMigrationState.NOT_REQUIRED
            || input.migrationState() == SpawnerMigrationState.MIGRATED
            || input.migrationState() == SpawnerMigrationState.CONFLICT) {
            return input;
        }
        if (!world.isChunkLoaded(input.x() >> 4, input.z() >> 4)) {
            return input;
        }
        final Location location = new Location(world, input.x(), input.y(), input.z());
        if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) {
            return input;
        }

        if (!wildStacker.installed()) {
            final ManagedSpawner updated = registry.updateMigrationState(input.id(), SpawnerMigrationState.NOT_REQUIRED);
            if (updated != null) {
                stateService.apply(spawner, updated, tuning.tier(updated.tier()));
            }
            return updated == null ? input : updated;
        }
        logProviderWarningOnce();
        if (!settings.migrationAutoImport()) {
            return markConflict(input, spawner, "WildStacker spawner amount requires migration but auto-import is disabled");
        }

        final WildStackerCompat.Amount providerAmount = wildStacker.getLogicalSpawnerAmount(spawner);
        if (providerAmount.result() == WildStackerCompat.Result.NOT_INSTALLED) {
            final ManagedSpawner updated = registry.updateMigrationState(input.id(), SpawnerMigrationState.NOT_REQUIRED);
            return updated == null ? input : updated;
        }
        if (providerAmount.result() != WildStackerCompat.Result.SUCCESS) {
            return markConflict(input, spawner,
                "WildStacker logical spawner amount is unavailable (" + providerAmount.result() + ")");
        }

        if (input.migrationState() == SpawnerMigrationState.MIGRATING) {
            if (providerAmount.amount() <= 1) {
                return completeMigration(input, spawner);
            }
            return normalizeProvider(input, spawner, providerAmount.amount());
        }

        final int amount = Math.max(1, providerAmount.amount());
        if (amount > settings.maxStackSize()) {
            return markConflict(input, spawner,
                "WildStacker amount x" + amount + " exceeds Plexon max-stack-size " + settings.maxStackSize());
        }
        if (amount <= 1) {
            return completeMigration(input, spawner);
        }

        ManagedSpawner durable = registry.updateStackAndMigration(input.id(), amount, SpawnerMigrationState.MIGRATING);
        if (durable == null) {
            return input;
        }
        try {
            registry.flushSync();
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "Could not persist migration ownership before normalizing WildStacker at " + formatLocation(durable), exception);
            return markConflict(durable, spawner, "Plexon migration state could not be durably persisted");
        }
        stateService.apply(spawner, durable, tuning.tier(durable.tier()));
        plugin.getLogger().info("Importing WildStacker spawner stack x" + amount + " into Plexon at " + formatLocation(durable) + ".");
        return normalizeProvider(durable, spawner, amount);
    }

    public int retryConflicts() {
        int retried = 0;
        for (final ManagedSpawner record : registry.snapshot()) {
            if (record.migrationState() != SpawnerMigrationState.CONFLICT) {
                continue;
            }
            final ManagedSpawner reset = registry.updateMigrationState(record.id(), SpawnerMigrationState.PENDING);
            if (reset == null) {
                continue;
            }
            retried++;
            final World world = Bukkit.getWorld(reset.worldId());
            if (world != null && world.isChunkLoaded(reset.x() >> 4, reset.z() >> 4)) {
                reconcile(reset, world);
            }
        }
        return retried;
    }

    public long conflictCount() {
        return registry.migrationConflictCount();
    }

    private ManagedSpawner normalizeProvider(
        final ManagedSpawner plexonRecord,
        final CreatureSpawner spawner,
        final int currentProviderAmount
    ) {
        int remaining = currentProviderAmount;
        while (remaining > 1) {
            final WildStackerCompat.Result result = wildStacker.unstackOne(spawner, null);
            if (result != WildStackerCompat.Result.SUCCESS) {
                return markConflict(plexonRecord, spawner,
                    "WildStacker normalization stopped at x" + remaining + " (" + result + ")");
            }
            remaining--;
        }
        final WildStackerCompat.Amount verified = wildStacker.getLogicalSpawnerAmount(spawner);
        if (verified.result() != WildStackerCompat.Result.SUCCESS || verified.amount() != 1) {
            return markConflict(plexonRecord, spawner,
                "WildStacker normalization could not verify physical provider amount x1");
        }
        return completeMigration(plexonRecord, spawner);
    }

    private ManagedSpawner completeMigration(final ManagedSpawner record, final CreatureSpawner spawner) {
        final ManagedSpawner updated = registry.updateMigrationState(record.id(), SpawnerMigrationState.MIGRATED);
        if (updated == null) {
            return record;
        }
        registry.flushSync();
        stateService.apply(spawner, updated, tuning.tier(updated.tier()));
        if (record.stackAmount() > 1) {
            plugin.getLogger().info("Plexon now owns native spawner stack x" + updated.stackAmount()
                + " at " + formatLocation(updated) + "; WildStacker provider amount is normalized to x1.");
        }
        return updated;
    }

    private ManagedSpawner markConflict(
        final ManagedSpawner record,
        final CreatureSpawner spawner,
        final String reason
    ) {
        final ManagedSpawner updated = registry.updateMigrationState(record.id(), SpawnerMigrationState.CONFLICT);
        if (updated != null) {
            try {
                registry.flushSync();
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist migration conflict state", exception);
            }
            stateService.apply(spawner, updated, tuning.tier(updated.tier()));
        }
        plugin.getLogger().warning("Spawner migration conflict at " + formatLocation(record) + ": " + reason
            + ". Stack-sensitive mutations are blocked until /pspawners migration retry succeeds.");
        return updated == null ? record : updated;
    }

    private void logProviderWarningOnce() {
        if (startupWarningLogged || !settings.warnWildStackerSpawnerStacking()) {
            return;
        }
        startupWarningLogged = true;
        plugin.getLogger().warning("Native Plexon spawner stacking is enabled. WildStacker should remain enabled for entity stacking only; disable WildStacker spawner stacking/upgrades after migration verification.");
    }

    private static String formatLocation(final ManagedSpawner record) {
        return record.worldId() + ":" + record.x() + "," + record.y() + "," + record.z();
    }
}
