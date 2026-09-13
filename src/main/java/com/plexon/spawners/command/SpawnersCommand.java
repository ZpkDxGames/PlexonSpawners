package com.plexon.spawners.command;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.gui.admin.AdminGuiService;
import com.plexon.spawners.message.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpawnersCommand implements CommandExecutor, TabCompleter {
    private final PlexonSpawners plugin;
    private final MessageService messages;
    private final AdminGuiService adminGui;

    public SpawnersCommand(final PlexonSpawners plugin, final MessageService messages, final AdminGuiService adminGui) {
        this.plugin = plugin;
        this.messages = messages;
        this.adminGui = adminGui;
    }

    @Override
    public boolean onCommand(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String label,
        final @NotNull String[] args
    ) {
        final String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin") || sub.equals("gui") || sub.equals("settings")) {
            if (!sender.hasPermission("plexonspawners.admin.gui")) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                messages.send(sender, "admin-player-only");
                return true;
            }
            adminGui.open(player);
            return true;
        }

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
            sender.sendMessage(messages.parse("<gray>Config schema:</gray> <white>" + plugin.settings().configSchema() + "</white>"));
            sender.sendMessage(messages.parse("<gray>Config revision:</gray> <white>" + plugin.revisions().current() + "</white>"));
            sender.sendMessage(messages.parse("<gray>Breaking policy:</gray> <white>"
                + enabled(plugin.settings().breakingEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>Required Silk Touch:</gray> <white>"
                + plugin.settings().requiredSilkTouchLevel() + "</white>"));
            sender.sendMessage(messages.parse("<gray>Non-Silk mode:</gray> <white>"
                + plugin.settings().defaultNonSilkRewardMode() + "</white>"));
            sender.sendMessage(messages.parse("<gray>Essence:</gray> <white>"
                + enabled(plugin.settings().essenceEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>Custom drop:</gray> <white>"
                + enabled(plugin.settings().customDropEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>Withdrawal GUI:</gray> <white>"
                + enabled(plugin.settings().guiEnabled()) + "</white>"));
            sender.sendMessage(messages.parse("<gray>World scope:</gray> <white>"
                + (plugin.settings().enabledWorlds().isEmpty() ? "ALL" : plugin.settings().enabledWorlds().size() + " configured")
                + "</white>"));
            sender.sendMessage(messages.parse("<#FFD166>WildStacker prerequisite:</#FFD166> <gray>non-Silk breaks must reach its unstack pipeline; PlexonSpawners does not edit WildStacker configuration.</gray>"));
            for (final String warning : plugin.settings().validationWarnings()) {
                sender.sendMessage(messages.parse("<#FFD166>Config warning:</#FFD166> <gray>" + escape(warning) + "</gray>"));
            }
            return true;
        }

        sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/" + label + " <admin|status|reload></white>"));
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
        final List<String> values = new ArrayList<>();
        if (sender.hasPermission("plexonspawners.admin.gui")) values.add("admin");
        if (sender.hasPermission("plexonspawners.admin.status")) values.add("status");
        if (sender.hasPermission("plexonspawners.admin.reload")) values.add("reload");
        final String prefix = args[0].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private static String enabled(final boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String escape(final String raw) {
        return raw.replace("<", "\\<").replace(">", "\\>");
    }
}
