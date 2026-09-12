package com.plexon.spawners.command;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.gui.AdminGui;
import com.plexon.spawners.integration.core.CoreBridge;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.message.MessageService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnersCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SPAWNABLE_ENTITY_NAMES = Arrays.stream(EntityType.values())
        .filter(EntityType::isAlive)
        .filter(EntityType::isSpawnable)
        .map(EntityType::name)
        .sorted()
        .toList();

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
            case "info", "diagnostics" -> diagnostics(sender);
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

    private boolean diagnostics(final CommandSender sender) {
        final CoreBridge core = plugin.coreBridge();
        final PerformanceCounters.Snapshot performance = plugin.performanceCounters().snapshot();
        sender.sendMessage(messages.parse("<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Diagnostics</b></gradient>"));
        sendDiagnostic(sender, "Plugin", plugin.getPluginMeta().getVersion());
        sendDiagnostic(sender, "Paper", Bukkit.getServer().getVersion());
        sendDiagnostic(sender, "Java", Integer.toString(Runtime.version().feature()));
        sendDiagnostic(sender, "Mode", core.mode());
        sendDiagnostic(sender, "Core plugin/API", core.pluginVersion() + " / " + core.apiVersion());
        sendDiagnostic(sender, "Supported Core", CoreBridge.SUPPORTED_API_RANGE);
        sendDiagnostic(sender, "Module", core.registrationState());
        sendDiagnostic(sender, "Managed runtime", enabled(plugin.tuning().enabled()));
        sendDiagnostic(sender, "Managed spawners", plugin.managedRegistry() == null
            ? "registry unavailable"
            : Integer.toString(plugin.managedRegistry().size()));
        sendDiagnostic(sender, "Tier ceiling", Integer.toString(plugin.tuning().maxTier()));
        sendDiagnostic(sender, "Persistence cadence", plugin.tuning().persistenceIntervalTicks() + " ticks");
        sendDiagnostic(sender, "Provenance radius", plugin.tuning().provenanceSearchRadius() + " blocks");
        sendDiagnostic(sender, "Chunk safety cap", Integer.toString(plugin.tuning().maxManagedPerChunk()));
        sendDiagnostic(sender, "Nearby Stack Cap", enabled(plugin.nearbyStackCapSettings().enabled()));
        sendDiagnostic(sender, "Stack Cap Radius", plugin.nearbyStackCapSettings().radius() + " blocks");
        sendDiagnostic(sender, "Stack Cap Maximum", Integer.toString(plugin.nearbyStackCapSettings().maximumAmount()));
        sendDiagnostic(sender, "Stack Cap Same Type", Boolean.toString(plugin.nearbyStackCapSettings().sameTypeOnly()));
        sendDiagnostic(sender, "Breaking", enabled(plugin.settings().breakingEnabled()));
        sendDiagnostic(sender, "Take ownership", Boolean.toString(plugin.settings().takeOwnership()));
        sendDiagnostic(sender, "Silk required", Integer.toString(plugin.settings().requiredSilkTouchLevel()));
        sendDiagnostic(sender, "Bypass enabled", Boolean.toString(plugin.settings().silkBypassPermissionEnabled()));
        sendDiagnostic(sender, "World filter", plugin.settings().enabledWorldCount() == 0
            ? "all worlds"
            : plugin.settings().enabledWorldCount() + " configured");
        sendDiagnostic(sender, "Essence", enabled(plugin.settings().essenceEnabled()));
        sendDiagnostic(sender, "Delivery", plugin.settings().essenceDelivery().name().toLowerCase(Locale.ROOT));
        sendDiagnostic(sender, "Essence default", plugin.settings().defaultEssenceAmount()
            + " @ " + plugin.settings().defaultEssenceChance() + "%");
        sendDiagnostic(sender, "Essence overrides", Integer.toString(plugin.settings().essenceOverrideCount()));
        sendDiagnostic(sender, "Essence max stack", Integer.toString(essenceService.maxStackSize()));
        sendDiagnostic(sender, "WildStacker", plugin.wildStackerCompat().status());
        sendDiagnostic(sender, "WildStacker resolution", plugin.wildStackerCompat().resolutionMode());
        sendDiagnostic(sender, "WildStacker API cache", plugin.wildStackerCompat().methodCacheReady() ? "ready" : "not ready");
        sendDiagnostic(sender, "Managed template cache", Integer.toString(spawnerItemService.templateCacheSize()));
        sendDiagnostic(sender, "Entity key lookup", Integer.toString(spawnerItemService.entityKeyLookupSize()));
        sendDiagnostic(sender, "Config warnings", Integer.toString(plugin.settings().validationWarnings().size()));
        sendDiagnostic(sender, "Break events", Long.toString(performance.blockBreakEventsSeen()));
        sendDiagnostic(sender, "Fast rejects", performance.nonSpawnerFastRejects()
            + " non-spawner / " + performance.disabledRejects() + " disabled / "
            + performance.worldRejects() + " world");
        sendDiagnostic(sender, "Accepted spawner breaks", Long.toString(performance.acceptedSpawnerBreaks()));
        sendDiagnostic(sender, "WildStacker outcomes", performance.wildStackerSuccess() + " success / "
            + performance.wildStackerNotStacked() + " not-stacked / "
            + performance.wildStackerNotInstalled() + " absent / "
            + performance.wildStackerCancelled() + " cancelled / "
            + performance.wildStackerDegraded() + " degraded");
        sendDiagnostic(sender, "Stack Cap Checks", Long.toString(performance.nearbyStackCapChecks()));
        sendDiagnostic(sender, "Stack Cap Blocks", Long.toString(performance.nearbyStackCapBlocked()));
        sendDiagnostic(sender, "Stack Cap Logical Counted", Long.toString(performance.nearbyStackCapLogicalEntitiesCounted()));
        sendDiagnostic(sender, "Stack Cap WS Lookups", Long.toString(performance.nearbyStackCapWildStackerLookups()));
        sendDiagnostic(sender, "Stack Cap Fail Closed", Long.toString(performance.nearbyStackCapFailClosed()));
        sendDiagnostic(sender, "Recoveries", Long.toString(performance.qualifiedRecoveries()));
        sendDiagnostic(sender, "Essence outcomes", performance.essenceWins() + "/" + performance.essenceRolls()
            + " wins/rolls, " + performance.essenceLogicalAmountAwarded() + " logical");
        sendDiagnostic(sender, "Essence physical", performance.essenceItemStacksCreated()
            + " stacks / " + performance.essenceGroundEntitiesCreated() + " ground entities");
        sendDiagnostic(sender, "Placement events", Long.toString(performance.blockPlaceEventsSeen()));
        sendDiagnostic(sender, "Managed placements", performance.managedPlacementSuccesses()
            + " success / " + performance.vanillaSpawnerPlacementRejects() + " vanilla rejects");
        sendDiagnostic(sender, "Public API", plugin.api() == null ? "not registered" : "registered");
        sendDiagnostic(sender, "Public events", "recovered / essence / placed ready");
        sendDiagnostic(sender, "Provenance API", plugin.api() == null ? "unavailable" : "ready");
        if (core.detail() != null && !core.detail().isBlank()) {
            sendDiagnostic(sender, "Core detail", core.detail());
        }
        return true;
    }

    private static void sendDiagnostic(final CommandSender sender, final String label, final String value) {
        sender.sendMessage(
            Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
        );
    }

    private static String enabled(final boolean value) {
        return value ? "enabled" : "disabled";
    }

    private boolean giveSpawner(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("plexonspawners.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(messages.parse(
                "<gray>Usage:</gray> <white>/pspawners give [player] [mob] [amount] [tier]</white>"
            ));
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
        final Integer tier = args.length >= 5 ? positiveInt(args[4]) : 1;
        if (tier == null || tier > plugin.tuning().maxTier()) {
            sender.sendMessage(messages.parse(
                "<gray>Tier must be between</gray> <white>1</white> <gray>and</gray> <white>"
                    + plugin.tuning().maxTier() + "</white><gray>.</gray>"
            ));
            return true;
        }

        giveInStacks(target, type, amount, tier);
        messages.send(sender, "gave-spawner", Map.of(
            "amount", Integer.toString(amount),
            "mob", SpawnerItemService.pretty(type),
            "player", target.getName()
        ));
        if (tier > 1) {
            sender.sendMessage(messages.parse(
                "<gray>Issued managed spawner tier:</gray> <white>" + tier + "</white>"
            ));
        }
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
            messages.send(sender, "gave-essence", Map.of(
                "amount", Integer.toString(amount),
                "player", target.getName()
            ));
            return true;
        }

        sender.sendMessage(messages.parse("<gray>Usage:</gray> <white>/pspawners essence [set|give]</white>"));
        return true;
    }

    private void giveInStacks(final Player target, final EntityType type, final int total, final int tier) {
        int remaining = total;
        while (remaining > 0) {
            final int amount = Math.min(64, remaining);
            final ItemStack stack = spawnerItemService.createSpawner(type, amount, tier);
            target.getInventory().addItem(stack).values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover)
            );
            remaining -= amount;
        }
    }

    private void giveEssence(final Player target, final int total) {
        final ItemStack[] stacks = essenceService.createStacks(total);
        target.getInventory().addItem(stacks).values().forEach(leftover ->
            target.getWorld().dropItemNaturally(target.getLocation(), leftover)
        );
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
        sender.sendMessage(messages.parse("<gray>/pspawners info</gray> <dark_gray>-</dark_gray> <white>runtime diagnostics</white>"));
        sender.sendMessage(messages.parse("<gray>/pspawners diagnostics</gray> <dark_gray>-</dark_gray> <white>Core/integration diagnostics</white>"));
        sender.sendMessage(messages.parse("<gray>/pspawners give [player] [mob] [amount] [tier]</gray>"));
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
            return filter(List.of("admin", "info", "diagnostics", "reload", "give", "essence"), args[0]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3) {
                return filter(SPAWNABLE_ENTITY_NAMES, args[2]);
            }
            if (args.length == 5) {
                final List<String> tiers = new ArrayList<>();
                for (int tier = 1; tier <= plugin.tuning().maxTier(); tier++) {
                    tiers.add(Integer.toString(tier));
                }
                return filter(tiers, args[4]);
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
