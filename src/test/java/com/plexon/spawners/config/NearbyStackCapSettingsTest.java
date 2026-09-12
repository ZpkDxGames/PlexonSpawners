package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NearbyStackCapSettingsTest {
    @Test
    void defaultsMatchProductContract() {
        final NearbyStackCapSettings settings = new NearbyStackCapSettings();
        settings.reload(new YamlConfiguration());

        assertTrue(settings.enabled());
        assertEquals(8.0D, settings.radius());
        assertEquals(99, settings.maximumAmount());
        assertTrue(settings.sameTypeOnly());
    }

    @Test
    void radiusAndMaximumAreBounded() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.nearby-stack-cap.radius", 999.0D);
        config.set("managed.nearby-stack-cap.maximum-amount", -50);

        final NearbyStackCapSettings settings = new NearbyStackCapSettings();
        settings.reload(config);

        assertEquals(32.0D, settings.radius());
        assertEquals(1, settings.maximumAmount());
    }

    @Test
    void nonFiniteRadiusFallsBackToDefault() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("managed.nearby-stack-cap.radius", Double.NaN);

        final NearbyStackCapSettings settings = new NearbyStackCapSettings();
        settings.reload(config);

        assertEquals(8.0D, settings.radius());
    }
}
