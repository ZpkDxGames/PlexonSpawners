package com.plexon.spawners.integration.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class CoreTextContractTest {
    @Test
    void coreBridgeExposesSharedTextRendering() throws ReflectiveOperationException {
        final Method direct = CoreBridge.class.getMethod("renderMiniMessage", String.class);
        final Method template = CoreBridge.class.getMethod("renderTemplate", String.class, Map.class);
        assertNotNull(direct);
        assertNotNull(template);
        assertNotNull(Component.class);
    }
}
