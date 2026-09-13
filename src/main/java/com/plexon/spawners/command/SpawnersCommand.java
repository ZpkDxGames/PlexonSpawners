package com.plexon.spawners.command;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.gui.admin.AdminGuiService;
import com.plexon.spawners.message.MessageService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

        if (sub.equals("give")) {
            return giveSpawner(sender, args);
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

        sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/" + label + " <admin|give|status|reload></white>"));
        return true;
    }

    private boolean giveSpawner(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("plexonspawners.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 4) {
            messages.send(sender, "give-usage");
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            messages.send(sender, "give-player-not-found", Map.of("player", args[1]));
            return true;
        }

        final EntityType mobType = parseMobType(args[2]);
        if (mobType == null) {
            messages.send(sender, "give-invalid-mob", Map.of("mob", args[2]));
            return true;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ignored) {
            messages.send(sender, "give-invalid-amount", Map.of("amount", args[3]));
            return true;
        }
        if (amount < 1) {
            messages.send(sender, "give-invalid-amount", Map.of("amount", args[3]));
            return true;
        }

        // WildStacker owns spawner item representation. Delegate to its own give path instead of
        // duplicating provider-specific NBT/PDC/upgrade metadata inside PlexonSpawners.
        final String wildStackerCommand = "stacker give -s " + target.getName()
            + " spawner " + mobType.name() + " " + amount;
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), wildStackerCommand)) {
            messages.send(sender, "give-failed");
            return true;
        }

        final Map<String, String> placeholders = Map.of(
            "player", target.getName(),
            "mob", formatMobType(mobType),
            "amount", Integer.toString(amount)
        );
        messages.send(sender, "give-success", placeholders);
        if (sender != target) messages.send(target, "give-received", placeholders);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String alias,
        final @NotNull String[] args
    ) {
        if (args.length == 1) {
            final List<String> values = new ArrayList<>();
            if (sender.hasPermission("plexonspawners.admin.gui")) values.add("admin");
            if (sender.hasPermission("plexonspawners.admin.give")) values.add("give");
            if (sender.hasPermission("plexonspawners.admin.status")) values.add("status");
            if (sender.hasPermission("plexonspawners.admin.reload")) values.add("reload");
            return filter(values, args[0]);
        }

        if (!args[0].equalsIgnoreCase("give") || !sender.hasPermission("plexonspawners.admin.give")) {
            return List.of();
        }

        if (args.length == 2) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        if (args.length == 3) {
            final List<String> mobTypes = Arrays.stream(EntityType.values())
                .filter(SpawnersCommand::isSpawnerMob)
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
            return filter(mobTypes, args[2]);
        }
        if (args.length == 4) {
            return filter(List.of("1", "8", "16", "32", "64"), args[3]);
        }
        return List.of();
    }

    private static @Nullable EntityType parseMobType(final String raw) {
        final String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            final EntityType type = EntityType.valueOf(normalized);
            return isSpawnerMob(type) ? type : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isSpawnerMob(final EntityType type) {
        final Class<?> entityClass = type.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }

    private static String formatMobType(final EntityType type) {
        final String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static List<String> filter(final List<String> values, final String rawPrefix) {
        final String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static String enabled(final boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String escape(final String raw) {
        return raw.replace("<", "\\<").replace(">", "\\>");
    }
}
