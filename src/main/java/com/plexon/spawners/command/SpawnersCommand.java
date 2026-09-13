package com.plexon.spawners.command;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.message.MessageService;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpawnersCommand implements CommandExecutor, TabCompleter {
    private final PlexonSpawners plugin;
    private final MessageService messages;

    public SpawnersCommand(final PlexonSpawners plugin, final MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String label,
        final @NotNull String[] args
    ) {
        final String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("plexonspawners.admin.reload")) {
                messages.send(sender, "no-permission");
                return true;
            }
            plugin.reloadPlugin();
            messages.send(sender, "reloaded");
            return true;
        }

        if (sub.equals("status")) {
            if (!sender.hasPermission("plexonspawners.admin.status")) {
                messages.send(sender, "no-permission");
                return true;
            }
            sender.sendMessage(messages.parse("<gradient:#56B9F2:#92E1FF><b>PlexonSpawners "
                + plugin.getPluginMeta().getVersion() + "</b></gradient>"));
            sender.sendMessage(messages.parse("<gray>WildStacker:</gray> <#72F1B8>detected</#72F1B8> <white>"
                + plugin.wildStacker().version() + "</white>"));
            sender.sendMessage(messages.parse("<gray>Stack authority:</gray> <white>WildStacker</white>"));
            sender.sendMessage(messages.parse("<gray>Silk recovery:</gray> <white>"
                + enabled(plugin.settings().breakingEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>Essence:</gray> <white>"
                + enabled(plugin.settings().essenceEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>Withdrawal GUI:</gray> <white>"
                + enabled(plugin.settings().guiEnabled()) + "</white>"));
            for (final String warning : plugin.settings().validationWarnings()) {
                sender.sendMessage(messages.parse("<#FFD166>Config warning:</#FFD166> <gray>" + warning + "</gray>"));
            }
            return true;
        }

        sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/" + label + " <status|reload></white>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String alias,
        final @NotNull String[] args
    ) {
        if (args.length != 1) return List.of();
        return List.of("status", "reload").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    private static String enabled(final boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
