package com.plexon.spawners.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessageService {
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

    private static String escape(final String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
