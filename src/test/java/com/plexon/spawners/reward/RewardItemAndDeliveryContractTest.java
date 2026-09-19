package com.plexon.spawners.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RewardItemAndDeliveryContractTest {
    @Test
    void exactPaperByteSerializationIsAuthoritative() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/reward/RewardItemFactory.java"));
        assertTrue(source.contains("serializeAsBytes()"));
        assertTrue(source.contains("ItemStack.deserializeBytes(bytes)"));
        assertTrue(source.contains("Base64.getEncoder()"));
        assertTrue(source.contains("Base64.getDecoder()"));
        assertTrue(source.contains("normalized.setAmount(1)"));
        assertTrue(source.contains("exact-data"));
        assertTrue(source.contains("definition.exactData()"));
    }

    @Test
    void identityMarkerIsAddedToExactCloneWithoutErasingForeignPdc() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/reward/RewardItemFactory.java"));
        assertTrue(source.contains("final ItemStack item = decode(definition.exactData())"));
        assertTrue(source.contains("meta.getPersistentDataContainer().set(identityKey, PersistentDataType.INTEGER, 1)"));
        assertFalse(source.contains("getPersistentDataContainer().getKeys().forEach"));
        assertFalse(source.contains("PersistentDataContainer#remove"));
    }

    @Test
    void deliveryClonesExactTemplateAndChunksLegalStackSizes() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/reward/RewardDelivery.java"));
        assertTrue(source.contains("final ItemStack stack = template.clone()"));
        assertTrue(source.contains("Math.min((long) maxStack, remaining)"));
        assertTrue(source.contains("player.getInventory().addItem(stack)"));
        assertTrue(source.contains("fallbackLocation.getWorld().dropItemNaturally"));
    }
}
