package com.plexon.spawners.message;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Generation-safe message catalog. */
public final class MessageService {
    public record Snapshot(Map<String, String> values) {
        public Snapshot {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public String get(final String key, final String fallback) {
            return values.getOrDefault(key, fallback);
        }
    }

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Snapshot runtime = new Snapshot(Map.of("prefix", ""));

    public MessageService(final JavaPlugin plugin) {
        this.plugin = plugin;
        final File file = file();
        if (!file.exists()) plugin.saveResource("messages.yml", false);
    }

    public Snapshot prepareFromDisk() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (final String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) continue;
            final Object raw = yaml.get(key);
            if (!(raw instanceof String value)) continue;
            validate(value, key);
            values.put(key, value);
        }
        if (!values.containsKey("prefix")) values.put("prefix", "");
        return new Snapshot(values);
    }

    public void commit(final Snapshot prepared) {
        runtime = java.util.Objects.requireNonNull(prepared, "prepared");
    }

    public Snapshot snapshot() {
        return runtime;
    }

    public void send(final CommandSender sender, final String key) {
        send(sender, key, Map.of());
    }

    public void send(final CommandSender sender, final String key, final Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders, true));
    }

    public Component component(final String key, final Map<String, String> placeholders, final boolean prefix) {
        final Snapshot snapshot = runtime;
        String raw = snapshot.get(key, "<red>Missing message: " + escape(key) + "</red>");
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", escape(entry.getValue()));
        }
        return miniMessage.deserialize((prefix ? snapshot.get("prefix", "") : "") + raw);
    }

    public Component parse(final String raw) {
        return miniMessage.deserialize(raw);
    }

    private File file() {
        return new File(plugin.getDataFolder(), "messages.yml");
    }

    private void validate(final String raw, final String key) {
        try {
            miniMessage.deserialize(raw);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MiniMessage at messages.yml key " + key, exception);
        }
    }

    private static String escape(final String input) {
        return input == null ? "" : input.replace("<", "\\\\<").replace(">", "\\\\>");
    }
}
