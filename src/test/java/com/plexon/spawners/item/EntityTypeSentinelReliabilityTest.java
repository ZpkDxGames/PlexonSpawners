package com.plexon.spawners.item;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class EntityTypeSentinelReliabilityTest {
    @Test
    void paper262UnknownEntityTypeHasNoKey() {
        assertThrows(IllegalArgumentException.class, EntityType.UNKNOWN::getKey);
    }

    @Test
    void startupLookupSkipsUnknownInsteadOfCallingItsKey() {
        final Map<String, EntityType> lookup = assertDoesNotThrow(SpawnerItemService::buildEntityKeyLookup);
        assertFalse(lookup.containsValue(EntityType.UNKNOWN));
        assertFalse(lookup.containsKey("unknown"));
        assertFalse(lookup.containsKey("minecraft:unknown"));
    }

    @Test
    void startupLookupStillSupportsNamespacedAndLegacyEnumKeys() {
        final Map<String, EntityType> lookup = SpawnerItemService.buildEntityKeyLookup();
        assertSame(EntityType.PIG, lookup.get("minecraft:pig"));
        assertSame(EntityType.PIG, lookup.get("pig"));
    }

    @Test
    void managedItemSerializationRejectsUnknownExplicitly() {
        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> SpawnerItemService.requireManagedEntityKey(EntityType.UNKNOWN)
        );
        assertTrue(exception.getMessage().contains("keyed EntityType"));
        assertTrue(exception.getMessage().contains("UNKNOWN"));
    }

    @Test
    void managedItemSerializationRejectsNullAndAcceptsKeyedTypes() {
        assertThrows(IllegalArgumentException.class, () -> SpawnerItemService.requireManagedEntityKey(null));
        final NamespacedKey pigKey = assertDoesNotThrow(
            () -> SpawnerItemService.requireManagedEntityKey(EntityType.PIG)
        );
        assertEquals("minecraft:pig", pigKey.asString());
    }

    @Test
    void displayNameLoopIsSentinelSafe() {
        assertEquals("Unknown", assertDoesNotThrow(() -> SpawnerItemService.pretty(EntityType.UNKNOWN)));
    }

    @Test
    void entityTypeGetKeyUsageIsCentralizedBehindTheGuard() throws Exception {
        final Path sourceRoot = Path.of("src/main/java/com/plexon/spawners");
        long guardedCalls = 0L;
        try (var paths = Files.walk(sourceRoot)) {
            for (final Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                final String source = Files.readString(path);
                guardedCalls += occurrences(source, "type.getKey()");
                assertFalse(source.contains("entityType.getKey()"), () -> "Unsafe EntityType#getKey use in " + path);
            }
        }
        assertEquals(1L, guardedCalls, "EntityType#getKey must remain centralized in the guarded helper");
    }

    @Test
    void unknownBreakGuardPrecedesLegacyPigFallback() throws Exception {
        final String source = Files.readString(
            Path.of("src/main/java/com/plexon/spawners/listener/SpawnerBreakListener.java")
        );
        final int unknownGuard = source.indexOf("entityType == EntityType.UNKNOWN");
        final int pigFallback = source.indexOf("entityType = EntityType.PIG");
        assertTrue(unknownGuard >= 0);
        assertTrue(pigFallback > unknownGuard, "UNKNOWN must never be converted into the PIG fallback");
        assertTrue(source.substring(unknownGuard, pigFallback).contains("event.setCancelled(true)"));
    }

    private static long occurrences(final String input, final String needle) {
        long count = 0L;
        int offset = 0;
        while ((offset = input.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
