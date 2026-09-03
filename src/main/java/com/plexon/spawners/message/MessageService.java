package com.plexon.spawners.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class MessageService {
    private static final Map<String, String> LEGACY_DEFAULTS = Map.ofEntries(
        Map.entry("prefix", "<gradient:#8A2BE2:#D56BFF><b>PlexonSpawners</b></gradient> <dark_gray>»</dark_gray> "),
        Map.entry("no-permission", "<red>You do not have permission to do that.</red>"),
        Map.entry("player-only", "<red>This command can only be used by a player.</red>"),
        Map.entry("reloaded", "<green>Configuration and item templates reloaded.</green>"),
        Map.entry("invalid-player", "<red>Player <white>%player%</white> is not online.</red>"),
        Map.entry("invalid-entity", "<red>Unknown entity type: <white>%entity%</white>.</red>"),
        Map.entry("invalid-number", "<red><white>%value%</white> is not a valid positive number.</red>"),
        Map.entry("gave-spawner", "<green>Gave <white>%amount%x %mob% Spawner</white> to <white>%player%</white>.</green>"),
        Map.entry("gave-essence", "<green>Gave <white>%amount%x Spawner Essence</white> to <white>%player%</white>.</green>"),
        Map.entry("essence-set", "<green>Spawner Essence copied from your main hand and secured with the PlexonSpawners identity tag.</green>"),
        Map.entry("essence-empty-hand", "<red>Hold the item you want to use as Spawner Essence in your main hand.</red>"),
        Map.entry("spawner-recovered", "<green>Recovered a <white>%mob% Spawner</white>.</green>"),
        Map.entry("essence-dropped", "<gray>The spawner shattered into <light_purple>%amount%x Spawner Essence</light_purple>.</gray>")
    );

    private static final Map<String, String> PLEXONCRAFT_DEFAULTS = Map.ofEntries(
        Map.entry("prefix", "<gradient:#56B9F2:#92E1FF><b>SPAWNERS</b></gradient> <dark_gray>»</dark_gray> "),
        Map.entry("no-permission", "<#FF6B6B>You do not have permission to use that.</#FF6B6B>"),
        Map.entry("player-only", "<#FF6B6B>This action is only available in-game.</#FF6B6B>"),
        Map.entry("reloaded", "<#72F1B8>Spawner settings and item templates reloaded.</#72F1B8>"),
        Map.entry("invalid-player", "<#FF6B6B>Player <white>%player%</white> is not online.</#FF6B6B>"),
        Map.entry("invalid-entity", "<#FF6B6B>Unknown creature type: <white>%entity%</white>.</#FF6B6B>"),
        Map.entry("invalid-number", "<#FF6B6B><white>%value%</white> must be a positive number.</#FF6B6B>"),
        Map.entry("gave-spawner", "<#72F1B8>Gave <white>%amount%x %mob% Spawner</white> to <white>%player%</white>.</#72F1B8>"),
        Map.entry("gave-essence", "<#72F1B8>Gave <white>%amount%x Spawner Essence</white> to <white>%player%</white>.</#72F1B8>"),
        Map.entry("essence-set", "<#72F1B8>Spawner Essence template updated from your main hand.</#72F1B8> <#8B95A7>Its secure identity was applied automatically.</#8B95A7>"),
        Map.entry("essence-empty-hand", "<#FFD166>Hold the item you want to use as Spawner Essence in your main hand.</#FFD166>"),
        Map.entry("spawner-recovered", "<#72F1B8>Spawner recovered!</#72F1B8> <#D8DEE9>You secured a <white>%mob% Spawner</white>.</#D8DEE9>"),
        Map.entry("essence-dropped", "<#D8DEE9>The spawner fractured, leaving <gradient:#C850C0:#FF7EB3><b>%amount%x Spawner Essence</b></gradient><#D8DEE9>.</#D8DEE9>")
    );

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessageService(final JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        final File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
        migrateLegacyDefaults(file);
    }

    public void send(final CommandSender sender, final String key) {
        send(sender, key, Map.of());
    }

    public void send(final CommandSender sender, final String key, final Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders, true));
    }

    public Component component(final String key, final Map<String, String> placeholders, final boolean includePrefix) {
        final String prefix = includePrefix ? messages.getString("prefix", "") : "";
        String raw = messages.getString(key, "<red>Missing message: " + key + "</red>");
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", escape(entry.getValue()));
        }
        return miniMessage.deserialize(prefix + raw);
    }

    public Component parse(final String raw) {
        return miniMessage.deserialize(raw);
    }

    private void migrateLegacyDefaults(final File file) {
        boolean changed = false;
        for (final Map.Entry<String, String> entry : LEGACY_DEFAULTS.entrySet()) {
            final String key = entry.getKey();
            if (entry.getValue().equals(messages.getString(key))) {
                messages.set(key, PLEXONCRAFT_DEFAULTS.get(key));
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        try {
            messages.save(file);
            plugin.getLogger().info("Updated stock messages to the PlexonCraft 2.1 theme.");
        } catch (final IOException exception) {
            plugin.getLogger().warning("Could not save migrated messages.yml: " + exception.getMessage());
        }
    }

    private static String escape(final String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
