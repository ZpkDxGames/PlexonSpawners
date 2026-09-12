package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class NativeStack34SettingsTest {
    @Test
    void newDefaultsEnableAdjacentStackingAndDirectAggregation() {
        final NativeStackSettings settings = new NativeStackSettings();
        settings.reload(new YamlConfiguration());

        assertTrue(settings.nearbyEnabled());
        assertEquals(1, settings.nearbyRadius());
        assertTrue(settings.entityAggregationEnabled());
        assertTrue(settings.preferExistingEntityStack());
        assertEquals(8.0D, settings.entityAggregationRadius());
        assertEquals(NativeStackSettings.EntityAggregationBackend.AUTO, settings.entityAggregationBackend());
    }

    @Test
    void explicitAdministratorNearbyDisableIsPreservedAtRuntime() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.stacking.auto-stack.nearby.enabled", false);
        final NativeStackSettings settings = new NativeStackSettings();
        settings.reload(config);
        assertEquals(false, settings.nearbyEnabled());
    }

    @Test
    void physicalBackendCanBeForced() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.stacking.spawning.entity-aggregation.backend", "PHYSICAL");
        final NativeStackSettings settings = new NativeStackSettings();
        settings.reload(config);
        assertEquals(NativeStackSettings.EntityAggregationBackend.PHYSICAL, settings.entityAggregationBackend());
    }
}
