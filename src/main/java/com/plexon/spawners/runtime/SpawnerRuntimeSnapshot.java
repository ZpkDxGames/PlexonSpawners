package com.plexon.spawners.runtime;

import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.message.MessageService;
import org.bukkit.inventory.ItemStack;

/** Fully prepared immutable runtime generation. */
public record SpawnerRuntimeSnapshot(
    long generation,
    PluginSettings.Snapshot settings,
    MessageService.Snapshot messages,
    String configYaml,
    ItemStack essenceTemplate,
    ItemStack customTemplate,
    String wildStackerVersion,
    String coreMode,
    String coreState
) {
    public SpawnerRuntimeSnapshot {
        if (generation < 0L) throw new IllegalArgumentException("generation must be non-negative");
        java.util.Objects.requireNonNull(settings, "settings");
        java.util.Objects.requireNonNull(messages, "messages");
        configYaml = java.util.Objects.requireNonNull(configYaml, "configYaml");
        essenceTemplate = essenceTemplate.clone();
        essenceTemplate.setAmount(1);
        customTemplate = customTemplate.clone();
        customTemplate.setAmount(1);
        wildStackerVersion = wildStackerVersion == null ? "unknown" : wildStackerVersion;
        coreMode = coreMode == null ? "STANDALONE" : coreMode;
        coreState = coreState == null ? "UNKNOWN" : coreState;
    }

    @Override public ItemStack essenceTemplate() { return essenceTemplate.clone(); }
    @Override public ItemStack customTemplate() { return customTemplate.clone(); }
}
