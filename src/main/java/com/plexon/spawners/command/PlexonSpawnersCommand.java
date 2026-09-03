package com.plexon.spawners.command;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.gui.AdminGui;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlexonSpawnersCommand implements CommandExecutor, TabCompleter {
    private final PlexonSpawners plugin;
    private final AdminGui adminGui;
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;
    private final MessageService messages;

    public PlexonSpawnersCommand(
        final PlexonSpawners plugin,
        final AdminGui adminGui,
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final MessageService messages
    ) {
        this.plugin = plugin;
        this.adminGui = adminGui;
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String label,
        final @NotNull String[] args
    ) {
        if (!sender.hasPermission("plexonspawners.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("admin")) {
            return openAdmin(sender);
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            case "give" -> giveSpawner(sender, args);
            case "essence" -> essence(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean openAdmin(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("plexonspawners.admin.gui")) {
            messages.send(sender, "no-permission");
            return true;
        }
        adminGui.open(player);
        return true;
    }

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("plexonspawners.admin.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        plugin.reloadPlugin();
        messages.send(sender, "reloaded");
        return true;
    }

    private boolean info(final CommandSender sender) {
        sender.sendMessage(messages.parse("<gradient:#8A2BE2:#D56BFF><b>PlexonSpawners</b></gradient> <gray>" + plugin.getPluginMeta().getVersion() + "</gray>"));
        sender.sendMessage(messages.parse("<gray>Paper API:</gray> <white>26.2</white>"));
        sender.sendMessage(messages.parse("<gray>Required Silk Touch:</gray> <white>" + plugin.settings().requiredSilkTouchLevel() + "</white>"));
        sender.sendMessage(messages.parse("<gray>Spawner Essence:</gray> " + (plugin.settings().essenceEnabled() ? "<green>enabled</green>" : "<red>disabled</red>")));
        return true;
    }

    private boolean giveSpawner(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("plexonspawners.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/pspawners give [player] [mob] [amount]</white>"));
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "invalid-player", Map.of("player", args[1]));
            return true;
        }
        final EntityType type = PluginSettings.parseEntityType(args[2]);
        if (type == null || !type.isAlive() || !type.isSpawnable()) {
            messages.send(sender, "invalid-entity", Map.of("entity", args[2]));
            return true;
        }
        final Integer amount = args.length >= 4 ? positiveInt(args[3]) : 1;
        if (amount == null) {
            messages.send(sender, "invalid-number", Map.of("value", args[3]));
            return true;
        }

        giveInStacks(target, type, amount);
        messages.send(sender, "gave-spawner", Map.of(
            "amount", Integer.toString(amount),
            "mob", SpawnerItemService.pretty(type),
            "player", target.getName()
        ));
        return true;
    }

    private boolean essence(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("plexonspawners.admin.essence")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/pspawners essence [set|give]</white>"));
            return true;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!essenceService.setTemplate(player.getInventory().getItemInMainHand())) {
                messages.send(sender, "essence-empty-hand");
                return true;
            }
            plugin.reloadPlugin();
            messages.send(sender, "essence-set");
            return true;
        }

        if (args[1].equalsIgnoreCase("give")) {
            if (args.length < 3) {
                sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/pspawners essence give [player] [amount]</white>"));
                return true;
            }
            final Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages.send(sender, "invalid-player", Map.of("player", args[2]));
                return true;
            }
            final Integer amount = args.length >= 4 ? positiveInt(args[3]) : 1;
            if (amount == null) {
                messages.send(sender, "invalid-number", Map.of("value", args[3]));
                return true;
            }
            giveEssence(target, amount);
            messages.send(sender, "gave-essence", Map.of("amount", Integer.toString(amount), "player", target.getName()));
            return true;
        }

        sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/pspawners essence [set|give]</white>"));
        return true;
    }

    private void giveInStacks(final Player target, final EntityType type, final int total) {
        int remaining = total;
        while (remaining > 0) {
            final int amount = Math.min(64, remaining);
            final ItemStack stack = spawnerItemService.createSpawner(type, amount);
            target.getInventory().addItem(stack).values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover)
            );
            remaining -= amount;
        }
    }

    private void giveEssence(final Player target, final int total) {
        int remaining = total;
        final int max = essenceService.template().getMaxStackSize();
        while (remaining > 0) {
            final int amount = Math.min(max, remaining);
            target.getInventory().addItem(essenceService.create(amount)).values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover)
            );
            remaining -= amount;
        }
    }

    private static Integer positiveInt(final String input) {
        try {
            final int value = Integer.parseInt(input);
            return value > 0 && value <= 4096 ? value : null;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private void sendUsage(final CommandSender sender) {
        sender.sendMessage(messages.parse("<gray>/pspawners admin</gray> <dark_gray>-</dark_gray> <white>open admin editor</white>"));
        sender.sendMessage(messages.parse("<gray>/pspawners give [player] [mob] [amount]</gray>"));
        sender.sendMessage(messages.parse("<gray>/pspawners essence set</gray> <dark_gray>-</dark_gray> <white>copy held item</white>"));
        sender.sendMessage(messages.parse("<gray>/pspawners essence give [player] [amount]</gray>"));
        sender.sendMessage(messages.parse("<gray>/pspawners reload</gray>"));
    }

    @Override
    public List<String> onTabComplete(
        final @NotNull CommandSender sender,
        final @NotNull Command command,
        final @NotNull String alias,
        final @NotNull String[] args
    ) {
        if (!sender.hasPermission("plexonspawners.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("admin", "info", "reload", "give", "essence"), args[0]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3) {
                return filter(Arrays.stream(EntityType.values())
                    .filter(EntityType::isAlive)
                    .filter(EntityType::isSpawnable)
                    .map(EntityType::name)
                    .sorted()
                    .toList(), args[2]);
            }
        }
        if (args[0].equalsIgnoreCase("essence")) {
            if (args.length == 2) {
                return filter(List.of("set", "give"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(final List<String> values, final String input) {
        final String lower = input.toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
