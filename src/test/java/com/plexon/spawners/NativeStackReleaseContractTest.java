package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Stable-release source contracts for the 3.3 native stack ownership boundary. */
class NativeStackReleaseContractTest {
    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void persistenceOwnsFirstClassNativeStackStateAndReadsLegacySchema() throws Exception {
        final String model = source("managed/ManagedSpawner.java");
        final String registry = source("managed/ManagedSpawnerRegistry.java");
        final String physical = source("managed/SpawnerStateService.java");

        assertTrue(model.contains("int stackAmount"));
        assertTrue(model.contains("SpawnerMigrationState migrationState"));
        assertTrue(registry.contains("PERSISTENCE_SCHEMA = 2"));
        assertTrue(registry.contains("HEADER_V1"));
        assertTrue(registry.contains("HEADER_V2"));
        assertTrue(registry.contains("1, SpawnerMigrationState.PENDING"));
        assertTrue(registry.contains("Integer.toString(record.stackAmount())"));
        assertTrue(registry.contains("record.migrationState().name()"));
        assertTrue(physical.contains("STATE_SCHEMA = 2"));
        assertTrue(physical.contains("managed_stack_amount"));
        assertTrue(physical.contains("managed_stack_migration"));
    }

    @Test
    void migrationDurablyClaimsAmountBeforeProviderNormalizationAndIsRestartSafe() throws Exception {
        final String migration = source("managed/SpawnerMigrationService.java");
        final int durable = migration.indexOf("updateStackAndMigration(input.id(), amount, SpawnerMigrationState.MIGRATING)");
        final int flush = migration.indexOf("registry.flushSync()", durable);
        final int normalize = migration.indexOf("normalizeProvider(durable, spawner, amount)", flush);

        assertTrue(durable >= 0 && flush > durable && normalize > flush);
        assertTrue(migration.contains("input.migrationState() == SpawnerMigrationState.MIGRATING"));
        assertTrue(migration.contains("if (providerAmount.amount() <= 1)"));
        assertTrue(migration.contains("SpawnerMigrationState.CONFLICT"));
        assertTrue(migration.contains("Stack-sensitive mutations are blocked"));
        assertTrue(migration.contains("wildStacker.unstackOne(spawner, null)"));
    }

    @Test
    void nativeBreakAndWithdrawPreserveTierAndNeverNeedWildStackerSpawnerAuthority() throws Exception {
        final String breaking = source("listener/SpawnerBreakListener.java");
        final String gui = source("gui/SpawnerControlGui.java");

        assertTrue(breaking.contains("final int logicalAmount = managed.stackAmount()"));
        assertTrue(breaking.contains("stackSettings.sneakBreakAll()"));
        assertTrue(breaking.contains("stackSettings.requireOwner()"));
        assertTrue(breaking.contains("registry.updateStackAmount(managed.id(), remaining)"));
        assertTrue(breaking.contains("createSpawnerStacks(type, amount, tier)"));
        assertTrue(gui.contains("createSpawnerStacks(current.type(), amount, current.tier())"));
        assertTrue(gui.contains("registry.updateStackAmount(current.id(), remaining)"));
        assertTrue(gui.contains("NativeStackPolicy.upgradeCost(nextTier.upgradeCostEssence(), current.stackAmount())"));
        assertFalse(gui.contains("getLogicalSpawnerAmount"));
    }

    @Test
    void placementMergesHeldAmountWithoutOverflowLossAndHonorsCreativeConsumption() throws Exception {
        final String registry = source("managed/ManagedSpawnerRegistry.java");
        final String placement = source("listener/SpawnerPlaceListener.java");

        assertTrue(registry.contains("findAutoStackTarget"));
        assertTrue(registry.contains("settings.requireSameEntityType()"));
        assertTrue(registry.contains("settings.requireSameOwner()"));
        assertTrue(registry.contains("settings.requireSameTier()"));
        assertTrue(registry.contains("settings.requireSameAccess()"));
        assertTrue(registry.contains("verticalAligned(placedLocation, candidate) ? 0 : 1"));
        assertTrue(registry.contains("thenComparingInt(ManagedSpawner::y)"));
        assertTrue(placement.contains("final int incomingAmount = logicalPlacementAmount(event)"));
        assertTrue(placement.contains(".withStackAmount(incomingAmount)"));
        assertTrue(placement.contains("target.stackAmount(), pending.stackAmount(), stackSettings.maxStackSize()"));
        assertTrue(placement.contains("pending.withStackAmount(merge.remainder())"));
        assertTrue(placement.contains("consumedAmount = merge.mergedAmount()"));
        assertTrue(placement.contains("adjustHeldAmount(event, consumedAmount)"));
        assertTrue(placement.contains("stackSettings.creativeConsumeOnPlace()"));
        assertTrue(placement.contains("setItemInOffHand(replacement)"));
        assertTrue(placement.contains("setItemInMainHand(replacement)"));
    }

    @Test
    void displayIsEventDrivenAndHideTitleRemovesExistingTitlesOnReconcile() throws Exception {
        final String display = source("managed/SpawnerStackDisplayService.java");
        final String plugin = source("PlexonSpawners.java");

        assertTrue(display.contains("settings.hideTitle()"));
        assertTrue(display.contains("settings.hideSingle()"));
        assertTrue(display.contains("existing.forEach(Entity::remove)"));
        assertTrue(display.contains("spawn(anchor, TextDisplay.class"));
        assertTrue(display.contains("stack_display_owner"));
        assertFalse(display.contains("runTaskTimer"));
        assertFalse(display.contains("getEntitiesByClass"));
        assertTrue(display.contains("for (final ManagedSpawner record : registry.snapshot())"));
        assertTrue(plugin.contains("stackDisplayService.reconcileLoaded()"));
    }

    @Test
    void spawnScalingUsesPlexonAmountBeforeCycleAndNearbyBounds() throws Exception {
        final String listener = source("listener/NearbyStackCapListener.java");
        final String policy = source("managed/NativeStackPolicy.java");
        final String settings = source("config/NativeStackSettings.java");

        assertTrue(listener.contains("managed.stackAmount()"));
        assertTrue(listener.contains("stackSettings.maxLogicalOutputPerCycle()"));
        assertTrue(listener.contains("nearbyRemaining = NearbyStackCapPolicy.remainingCapacity"));
        assertTrue(listener.contains("final int allowed = Math.min(requested, nearbyRemaining)"));
        assertTrue(listener.contains("stackSettings.vanillaPhysicalOutputCap()"));
        assertTrue(listener.contains("CreatureSpawnEvent.SpawnReason.SPAWNER"));
        assertTrue(policy.contains("(long) tierSpawnCount * stackAmount"));
        assertTrue(policy.contains("Math.min(cycleCap"));
        assertTrue(settings.contains("runtime.spawnMode() == SpawnMode.LINEAR"));
        assertTrue(settings.contains("? Integer.MAX_VALUE"));
        assertFalse(listener.contains("getLogicalSpawnerAmount"));
    }

    @Test
    void physicalMutationGuardsCoverExplosionsAndPistons() throws Exception {
        final String protection = source("listener/SpawnerProtectionListener.java");
        assertTrue(protection.contains("EntityExplodeEvent"));
        assertTrue(protection.contains("BlockExplodeEvent"));
        assertTrue(protection.contains("BlockPistonExtendEvent"));
        assertTrue(protection.contains("BlockPistonRetractEvent"));
        assertTrue(protection.contains("event.blockList().removeIf(this::isManagedSpawner)"));
        assertTrue(protection.contains("event.setCancelled(true)"));
    }

    @Test
    void configSchemaEightAndNativeDefaultsAreMaterializedWithoutDeletingOverrides() throws Exception {
        final String plugin = source("PlexonSpawners.java");
        assertTrue(plugin.contains("configVersion < 8"));
        assertTrue(plugin.contains("getConfig().set(\"config-version\", 8)"));
        assertTrue(plugin.contains("getConfig().options().copyDefaults(true)"));
        assertTrue(plugin.contains("saveConfig()"));
    }
}
