package com.plexon.spawners.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class StandaloneCoreBridgeTest {
    @Test
    void absentCoreRemainsSafeStandalone() {
        CoreBridge bridge = new StandaloneCoreBridge(false, "-", "-", "PlexonCore is not installed");
        assertEquals(">=1.0 <2.0", CoreBridge.SUPPORTED_API_RANGE);
        assertEquals("spawners", CoreBridge.MODULE_ID);
        assertEquals("STANDALONE", bridge.mode());
        assertEquals("NOT_INSTALLED", bridge.registrationState());
        assertFalse(bridge.available());
        assertFalse(bridge.compatible());
    }

    @Test
    void installedButUnavailableCoreStillFallsBackSafely() {
        CoreBridge bridge = new StandaloneCoreBridge(true, "1.0.0", "-", "API unavailable");
        assertEquals("STANDALONE", bridge.mode());
        assertEquals("UNAVAILABLE", bridge.registrationState());
        assertEquals("1.0.0", bridge.pluginVersion());
        assertEquals("API unavailable", bridge.detail());
    }
}
