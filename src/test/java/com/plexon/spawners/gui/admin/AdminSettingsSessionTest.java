package com.plexon.spawners.gui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.plexon.spawners.config.ConfigRevisionService;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class AdminSettingsSessionTest {
    @Test
    void draftEditsDoNotMutateSourceConfiguration() {
        final YamlConfiguration source = config();
        final AdminSettingsDraft draft = AdminSettingsDraft.from(source);
        draft.essenceChance(90.0D);
        draft.breakingEnabled(false);
        assertEquals(35.0D, source.getDouble("essence.default-chance"));
        assertTrue(source.getBoolean("breaking.enabled"));
        assertNotEquals(source.getDouble("essence.default-chance"), draft.essenceChance());
    }

    @Test
    void sessionDirtyDiscardAndRevisionLifecycleAreExplicit() {
        final ConfigRevisionService revisions = new ConfigRevisionService();
        final AdminSettingsSession session = new AdminSettingsSession(UUID.randomUUID(), AdminSettingsDraft.from(config()), revisions.current());
        assertFalse(session.dirty());
        session.draft().customEnabled(true);
        session.markDirty();
        assertTrue(session.dirty());
        final long newer = revisions.bump();
        assertNotEquals(session.sourceRevision(), newer);
        session.replace(AdminSettingsDraft.from(config()), newer);
        assertFalse(session.dirty());
        assertEquals(newer, session.sourceRevision());
    }

    @Test
    void validationDistinguishesWarningsFromHardErrors() {
        final AdminSettingsDraft draft = AdminSettingsDraft.from(config());
        draft.customEnabled(true);
        draft.customChance(0.0D);
        final AdminSettingsDraft.ValidationResult result = draft.validate();
        assertTrue(result.valid());
        assertFalse(result.warnings().isEmpty());
    }

    private static YamlConfiguration config() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 11);
        config.set("breaking.enabled", true);
        config.set("breaking.non-silk-reward-mode", "ESSENCE");
        config.set("essence.enabled", true);
        config.set("essence.default-amount", 1);
        config.set("essence.default-chance", 35.0D);
        config.set("essence.delivery", "INVENTORY");
        config.set("essence.item.material", "AMETHYST_SHARD");
        config.set("essence.item.name", "<aqua>Essence</aqua>");
        config.set("essence.item.lore", List.of("<gray>Reward</gray>"));
        config.set("custom-drop.enabled", false);
        config.set("custom-drop.default-amount", 1);
        config.set("custom-drop.default-chance", 15.0D);
        config.set("custom-drop.delivery", "INVENTORY");
        config.set("custom-drop.item.material", "PRISMARINE_CRYSTALS");
        config.set("custom-drop.item.name", "<aqua>Fragment</aqua>");
        config.set("custom-drop.item.lore", List.of("<gray>Reward</gray>"));
        config.set("gui.withdraw-presets", List.of(1, 8, 16, 32, 64));
        config.set("gui.title", "<aqua>Withdrawal</aqua>");
        config.set("admin-gui.title", "<aqua>Admin</aqua>");
        return config;
    }
}
