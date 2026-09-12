package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RedstoneLockSettingsTest {
    @Test
    void defaultsEnableManagedRedstoneFreeze() {
        final RedstoneLockSettings settings = new RedstoneLockSettings();
        settings.reload(new YamlConfiguration());

        assertTrue(settings.enabled());
        assertEquals(20L, settings.pollIntervalTicks());
    }

    @Test
    void administratorCanDisableAndPollIntervalIsBounded() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.redstone-lock.enabled", false);
        config.set("managed.redstone-lock.poll-interval-ticks", 9999L);

        final RedstoneLockSettings settings = new RedstoneLockSettings();
        settings.reload(config);

        assertFalse(settings.enabled());
        assertEquals(200L, settings.pollIntervalTicks());

        config.set("managed.redstone-lock.poll-interval-ticks", 0L);
        settings.reload(config);
        assertEquals(1L, settings.pollIntervalTicks());
    }
}
