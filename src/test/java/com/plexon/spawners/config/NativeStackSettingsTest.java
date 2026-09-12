package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NativeStackSettingsTest {
    @Test
    void boundedLinearIsTheDefaultAndUsesConfiguredCycleCap() {
        final NativeStackSettings settings = new NativeStackSettings();
        settings.reload(new YamlConfiguration());

        assertEquals(NativeStackSettings.SpawnMode.BOUNDED_LINEAR, settings.spawnMode());
        assertEquals(NativeStackSettings.DEFAULT_CYCLE_OUTPUT_CAP, settings.maxLogicalOutputPerCycle());
        assertEquals(NativeStackSettings.DEFAULT_CYCLE_OUTPUT_CAP, settings.configuredMaxLogicalOutputPerCycle());
    }

    @Test
    void linearModeRemovesCycleCapWithoutDiscardingConfiguredValue() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.stacking.spawning.mode", "LINEAR");
        config.set("managed.stacking.spawning.max-logical-output-per-cycle", 7);

        final NativeStackSettings settings = new NativeStackSettings();
        settings.reload(config);

        assertEquals(NativeStackSettings.SpawnMode.LINEAR, settings.spawnMode());
        assertEquals(Integer.MAX_VALUE, settings.maxLogicalOutputPerCycle());
        assertEquals(7, settings.configuredMaxLogicalOutputPerCycle());
    }
}
