package com.plexon.spawners.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CoreBindingContractTest {
    @Test
    void moduleIdentityAndApiRangeStayStable() {
        assertEquals("spawners", CoreBridge.MODULE_ID);
        assertEquals(">=1.0 <2.0", CoreBridge.SUPPORTED_API_RANGE);
    }

    @Test
    void pluginMetadataRequiresPlexonCore() throws IOException {
        try (InputStream input = CoreBindingContractTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(input, "processed plugin.yml must be present on the test classpath");
            final String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(yaml.contains("load: POSTWORLD"), "PlexonSpawners should load after STARTUP Core initialization");
            assertTrue(yaml.contains("depend:\n  - PlexonCore"), "PlexonCore must be a hard dependency");
            assertFalse(yaml.contains("softdepend:\n  - PlexonCore"), "Core must not silently downgrade to optional mode");
        }
    }
}
