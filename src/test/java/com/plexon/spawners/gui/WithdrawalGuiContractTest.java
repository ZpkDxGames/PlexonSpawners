package com.plexon.spawners.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WithdrawalGuiContractTest {
    @Test
    void everyWithdrawalClickReResolvesLiveWildStackerState() throws IOException {
        final String source = source();
        final int encoded = source.indexOf("final Integer encoded = clicked.getItemMeta().getPersistentDataContainer()");
        final int resolve = source.indexOf("final StackedSpawner stacked = wildStacker.resolve(holder.spawnerLocation())", encoded);
        final int current = source.indexOf("final int current = stacked.getStackAmount()", resolve);
        final int validation = source.indexOf("WithdrawalPolicy.isValidRequest(current, requested)", current);
        final int item = source.indexOf("wildStacker.createSpawnerItem(stacked, requested)", validation);
        final int unstack = source.indexOf("wildStacker.withdraw(stacked, requested, player)", item);
        final int success = source.indexOf("result != UnstackResult.SUCCESS", unstack);
        final int deliver = source.indexOf("deliver(player, authoritativeItem)", success);

        assertTrue(encoded >= 0);
        assertTrue(resolve > encoded, "The click must resolve the current WildStacker stack instead of trusting GUI state");
        assertTrue(current > resolve);
        assertTrue(validation > current, "The requested amount must be checked against the live amount");
        assertTrue(item > validation, "Spawner items must be generated only after live amount validation");
        assertTrue(unstack > item);
        assertTrue(success > unstack);
        assertTrue(deliver > success, "Delivery must happen only after WildStacker reports a successful unstack");
    }

    @Test
    void withdrawalPreservesOneLogicalUnitAndUsesAuthoritativeItems() throws IOException {
        final String source = source();
        assertTrue(source.contains("WithdrawalPolicy.maximumWithdrawable(current)"));
        assertTrue(source.contains("wildStacker.createSpawnerItem(stacked, requested)"));
        assertTrue(source.contains("wildStacker.withdraw(stacked, requested, player)"));
        assertTrue(source.contains("overflow.values()"));
        assertTrue(source.contains("dropItemNaturally"));
    }

    @Test
    void withdrawalPathDoesNotInvokeBreakRewardServices() throws IOException {
        final String source = source();
        assertFalse(source.contains("EssenceService"));
        assertFalse(source.contains("CustomDropService"));
        assertFalse(source.contains("RewardRollPolicy"));
    }

    private static String source() throws IOException {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners/gui/SpawnerWithdrawGui.java"));
    }
}
