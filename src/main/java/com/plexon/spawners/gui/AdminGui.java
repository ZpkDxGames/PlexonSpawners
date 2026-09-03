package com.plexon.spawners.gui;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.util.ItemFactory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AdminGui implements Listener {
    private static final int GUI_SIZE = 54;
    private static final int MOB_PAGE_SIZE = 45;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;

    private final PlexonSpawners plugin;
    private final EssenceService essenceService;
    private final MessageService messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final List<EntityType> editableMobs;

    public AdminGui(final PlexonSpawners plugin, final EssenceService essenceService, final MessageService messages) {
        this.plugin = plugin;
        this.essenceService = essenceService;
        this.messages = messages;
        this.editableMobs = Arrays.stream(EntityType.values())
            .filter(EntityType::isAlive)
            .filter(EntityType::isSpawnable)
            .sorted(Comparator.comparing(EntityType::name))
            .toList();
    }

    public void open(final Player player) {
        openHome(player);
    }

    private void openHome(final Player player) {
        final Inventory inventory = create(AdminGuiHolder.Screen.HOME, 0, "<gradient:#7C3AED:#D946EF><b>PlexonSpawners • Admin</b></gradient>");

        inventory.setItem(11, ItemFactory.create(
            Material.SPAWNER,
            "<aqua><b>Spawner Rules</b></aqua>",
            "<gray>Control how spawners behave when mined.</gray>",
            "",
            "<white>•</white> Enable or disable break handling",
            "<white>•</white> Set the required Silk Touch level",
            "<white>•</white> Control spawner and XP drops",
            "<white>•</white> Choose creative-mode behavior",
            "",
            "<yellow>Click to configure.</yellow>"
        ));
        inventory.setItem(13, ItemFactory.create(
            Material.ECHO_SHARD,
            "<light_purple><b>Spawner Essence</b></light_purple>",
            "<gray>Configure the physical currency obtained</gray>",
            "<gray>when a player fails the Silk Touch rule.</gray>",
            "",
            "<white>Chance:</white> <yellow>" + formatChance(plugin.settings().defaultEssenceChance()) + "</yellow>",
            "<white>Amount:</white> <yellow>" + plugin.settings().defaultEssenceAmount() + "</yellow>",
            "<white>Delivery:</white> <yellow>" + prettyDelivery(plugin.settings().essenceDelivery()) + "</yellow>",
            "",
            "<yellow>Click to configure.</yellow>"
        ));
        inventory.setItem(15, ItemFactory.create(
            Material.CREEPER_HEAD,
            "<green><b>Mob Values</b></green>",
            "<gray>Give specific spawner types their own</gray>",
            "<gray>Essence amount and drop chance.</gray>",
            "",
            "<dark_gray>Mob settings override the global Essence</dark_gray>",
            "<dark_gray>values only for that spawner type.</dark_gray>",
            "",
            "<yellow>Click to browse mobs.</yellow>"
        ));

        inventory.setItem(31, ItemFactory.create(
            Material.BOOK,
            "<gold><b>Current Behavior</b></gold>",
            "<gray>A failed Silk Touch break first rolls the</gray>",
            "<gray>configured Essence chance. If successful,</gray>",
            "<gray>the configured amount is delivered.</gray>",
            "",
            "<white>0%</white> <dark_gray>= never drops</dark_gray>",
            "<white>100%</white> <dark_gray>= always drops</dark_gray>",
            "<white>Decimals</white> <dark_gray>= supported</dark_gray>"
        ));
        inventory.setItem(40, ItemFactory.create(
            Material.CLOCK,
            "<yellow><b>Reload Configuration</b></yellow>",
            "<gray>Reloads configuration and messages from disk.</gray>",
            "<dark_gray>Useful after editing YAML manually.</dark_gray>"
        ));
        inventory.setItem(SLOT_CLOSE, closeItem());

        player.openInventory(inventory);
    }

    private void openSpawnerRules(final Player player) {
        final Inventory inventory = create(AdminGuiHolder.Screen.SPAWNER_RULES, 0, "<gradient:#38BDF8:#22D3EE><b>Spawner Rules</b></gradient>");
        final PluginSettings settings = plugin.settings();

        inventory.setItem(10, toggleItem(
            settings.breakingEnabled(),
            "Spawner Break Handling",
            "When disabled, this plugin does not change spawner break drops."
        ));
        inventory.setItem(12, ItemFactory.create(
            Material.ENCHANTED_BOOK,
            "<aqua><b>Required Silk Touch: " + settings.requiredSilkTouchLevel() + "</b></aqua>",
            "<gray>The minimum Silk Touch enchantment level</gray>",
            "<gray>needed to recover the actual spawner.</gray>",
            "",
            "<green>Left click:</green> +1",
            "<red>Right click:</red> -1",
            "<yellow>Shift:</yellow> change by 5",
            "<dark_gray>0 means every valid break qualifies.</dark_gray>"
        ));
        inventory.setItem(14, toggleItem(
            settings.dropSpawnerWhenQualified(),
            "Qualified Spawner Drop",
            "When enabled, a qualifying break drops the typed spawner item."
        ));
        inventory.setItem(16, toggleItem(
            settings.dropExperience(),
            "Experience Drops",
            "Controls whether the original block-break experience is preserved."
        ));
        inventory.setItem(31, toggleItem(
            settings.creativeDrops(),
            "Creative Mode Drops",
            "When disabled, creative players destroy spawners without items or Essence."
        ));
        inventory.setItem(33, ItemFactory.create(
            Material.MAP,
            "<yellow><b>Enabled Worlds</b></yellow>",
            "<gray>World filtering remains a text-list setting</gray>",
            "<gray>because world names are server-specific.</gray>",
            "",
            "<dark_gray>An empty list enables every world.</dark_gray>",
            "<dark_gray>See the documented config.yml for examples.</dark_gray>"
        ));

        inventory.setItem(SLOT_BACK, backItem());
        inventory.setItem(SLOT_CLOSE, closeItem());
        player.openInventory(inventory);
    }

    private void openEssence(final Player player) {
        final Inventory inventory = create(AdminGuiHolder.Screen.ESSENCE, 0, "<gradient:#A855F7:#EC4899><b>Spawner Essence</b></gradient>");
        final PluginSettings settings = plugin.settings();

        inventory.setItem(10, essenceService.template());
        inventory.setItem(12, ItemFactory.create(
            Material.ANVIL,
            "<green><b>Set Essence From Hand</b></green>",
            "<gray>Copies the exact item in your main hand.</gray>",
            "<gray>Name, lore, model data and metadata are retained.</gray>",
            "",
            "<dark_gray>A private identity tag is added automatically.</dark_gray>"
        ));
        inventory.setItem(14, toggleItem(
            settings.essenceEnabled(),
            "Failed-Break Essence",
            "Controls whether failed Silk Touch breaks can produce Essence."
        ));
        inventory.setItem(16, ItemFactory.create(
            Material.COMPASS,
            "<yellow><b>Default Drop Chance: " + formatChance(settings.defaultEssenceChance()) + "</b></yellow>",
            "<gray>The probability that a failed break</gray>",
            "<gray>actually produces Spawner Essence.</gray>",
            "",
            "<green>Left click:</green> +1%",
            "<red>Right click:</red> -1%",
            "<yellow>Shift:</yellow> change by 5%",
            "<dark_gray>Range: 0% to 100%.</dark_gray>"
        ));
        inventory.setItem(28, ItemFactory.create(
            Material.ECHO_SHARD,
            "<light_purple><b>Default Amount: " + settings.defaultEssenceAmount() + "</b></light_purple>",
            "<gray>How much Essence is delivered after</gray>",
            "<gray>a successful chance roll.</gray>",
            "",
            "<green>Left click:</green> +1",
            "<red>Right click:</red> -1",
            "<yellow>Shift:</yellow> change by 5"
        ));
        inventory.setItem(30, ItemFactory.create(
            settings.essenceDelivery() == PluginSettings.EssenceDelivery.GROUND ? Material.HOPPER : Material.CHEST,
            "<aqua><b>Delivery: " + prettyDelivery(settings.essenceDelivery()) + "</b></aqua>",
            settings.essenceDelivery() == PluginSettings.EssenceDelivery.GROUND
                ? "<gray>Essence is dropped naturally at the broken spawner.</gray>"
                : "<gray>Essence goes to the player's inventory first.</gray>",
            "<dark_gray>Inventory overflow is always dropped safely.</dark_gray>",
            "",
            "<yellow>Click to switch delivery mode.</yellow>"
        ));
        inventory.setItem(32, ItemFactory.create(
            Material.CHEST,
            "<yellow><b>Give Test Essence</b></yellow>",
            "<gray>Gives you one genuine configured Essence.</gray>",
            "<dark_gray>Useful when preparing shops or testing item identity.</dark_gray>"
        ));
        inventory.setItem(34, ItemFactory.create(
            Material.CREEPER_HEAD,
            "<green><b>Mob Values</b></green>",
            "<gray>Configure amount and chance by spawner type.</gray>",
            "<yellow>Click to open the mob browser.</yellow>"
        ));

        inventory.setItem(SLOT_BACK, backItem());
        inventory.setItem(SLOT_CLOSE, closeItem());
        player.openInventory(inventory);
    }

    private void openMobs(final Player player, final int requestedPage) {
        final int maxPage = Math.max(0, (editableMobs.size() - 1) / MOB_PAGE_SIZE);
        final int page = Math.max(0, Math.min(maxPage, requestedPage));
        final Inventory inventory = create(
            AdminGuiHolder.Screen.MOBS,
            page,
            "<gradient:#22C55E:#84CC16><b>Mob Values • " + (page + 1) + "/" + (maxPage + 1) + "</b></gradient>"
        );

        final int start = page * MOB_PAGE_SIZE;
        final int end = Math.min(editableMobs.size(), start + MOB_PAGE_SIZE);
        for (int index = start; index < end; index++) {
            final EntityType type = editableMobs.get(index);
            final int slot = index - start;
            inventory.setItem(slot, mobItem(type));
        }

        inventory.setItem(SLOT_BACK, backItem());
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, ItemFactory.create(Material.ARROW, "<yellow><b>Previous Page</b></yellow>"));
        }
        inventory.setItem(SLOT_CLOSE, closeItem());
        if (page < maxPage) {
            inventory.setItem(SLOT_NEXT, ItemFactory.create(Material.ARROW, "<yellow><b>Next Page</b></yellow>"));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("plexonspawners.admin.gui")) {
            player.closeInventory();
            messages.send(player, "no-permission");
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        switch (holder.screen()) {
            case HOME -> handleHome(player, event.getRawSlot());
            case SPAWNER_RULES -> handleSpawnerRules(player, event);
            case ESSENCE -> handleEssence(player, event);
            case MOBS -> handleMobs(player, event, holder.page());
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleHome(final Player player, final int slot) {
        switch (slot) {
            case 11 -> openSpawnerRules(player);
            case 13 -> openEssence(player);
            case 15 -> openMobs(player, 0);
            case 40 -> {
                plugin.reloadPlugin();
                messages.send(player, "reloaded");
                openHome(player);
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
                return;
            }
        }
        clickSound(player);
    }

    private void handleSpawnerRules(final Player player, final InventoryClickEvent event) {
        final int slot = event.getRawSlot();
        switch (slot) {
            case 10 -> toggle("breaking.enabled");
            case 12 -> adjustInt("breaking.required-silk-touch-level", 0, 255, event.isShiftClick() ? 5 : 1, event.isLeftClick());
            case 14 -> toggle("breaking.drop-spawner-when-qualified");
            case 16 -> toggle("breaking.drop-experience");
            case 31 -> toggle("breaking.creative-drops");
            case SLOT_BACK -> {
                openHome(player);
                clickSound(player);
                return;
            }
            case SLOT_CLOSE -> {
                player.closeInventory();
                clickSound(player);
                return;
            }
            default -> {
                return;
            }
        }
        saveAndReload();
        openSpawnerRules(player);
        clickSound(player);
    }

    private void handleEssence(final Player player, final InventoryClickEvent event) {
        final int slot = event.getRawSlot();
        switch (slot) {
            case 12 -> {
                setEssenceFromHand(player);
                clickSound(player);
                return;
            }
            case 14 -> toggle("essence.enabled");
            case 16 -> adjustDouble("essence.default-chance", 0.0, 100.0, event.isShiftClick() ? 5.0 : 1.0, event.isLeftClick());
            case 28 -> adjustInt("essence.default-amount", 1, 4096, event.isShiftClick() ? 5 : 1, event.isLeftClick());
            case 30 -> plugin.getConfig().set(
                "essence.delivery",
                plugin.settings().essenceDelivery() == PluginSettings.EssenceDelivery.GROUND ? "INVENTORY" : "GROUND"
            );
            case 32 -> {
                giveTestEssence(player);
                clickSound(player);
                return;
            }
            case 34 -> {
                openMobs(player, 0);
                clickSound(player);
                return;
            }
            case SLOT_BACK -> {
                openHome(player);
                clickSound(player);
                return;
            }
            case SLOT_CLOSE -> {
                player.closeInventory();
                clickSound(player);
                return;
            }
            default -> {
                return;
            }
        }
        saveAndReload();
        openEssence(player);
        clickSound(player);
    }

    private void handleMobs(final Player player, final InventoryClickEvent event, final int page) {
        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < MOB_PAGE_SIZE) {
            final int index = page * MOB_PAGE_SIZE + slot;
            if (index < editableMobs.size()) {
                editMob(editableMobs.get(index), event);
                openMobs(player, page);
                clickSound(player);
            }
            return;
        }

        switch (slot) {
            case SLOT_BACK -> openEssence(player);
            case SLOT_PREVIOUS -> openMobs(player, page - 1);
            case SLOT_NEXT -> openMobs(player, page + 1);
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
                return;
            }
        }
        clickSound(player);
    }

    private void editMob(final EntityType type, final InventoryClickEvent event) {
        final String root = "essence.mob-overrides." + type.name();
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            plugin.getConfig().set(root, null);
            saveAndReload();
            return;
        }

        final int currentAmount = plugin.settings().essenceAmount(type);
        final double currentChance = plugin.settings().essenceChance(type);
        if (!plugin.getConfig().isConfigurationSection(root)) {
            plugin.getConfig().set(root, null);
        }

        if (event.isShiftClick()) {
            final double delta = event.isLeftClick() ? 5.0 : -5.0;
            plugin.getConfig().set(root + ".chance", clamp(currentChance + delta, 0.0, 100.0));
            if (!plugin.getConfig().contains(root + ".amount")) {
                plugin.getConfig().set(root + ".amount", currentAmount);
            }
        } else if (event.isLeftClick() || event.isRightClick()) {
            final int delta = event.isLeftClick() ? 1 : -1;
            plugin.getConfig().set(root + ".amount", Math.max(1, Math.min(4096, currentAmount + delta)));
            if (!plugin.getConfig().contains(root + ".chance")) {
                plugin.getConfig().set(root + ".chance", currentChance);
            }
        } else {
            return;
        }
        saveAndReload();
    }

    private ItemStack mobItem(final EntityType type) {
        final boolean overridden = plugin.settings().hasEssenceOverride(type);
        return ItemFactory.create(
            mobIcon(type),
            (overridden ? "<green><b>" : "<white><b>") + pretty(type) + "</b>",
            "<gray>Essence amount:</gray> <yellow>" + plugin.settings().essenceAmount(type) + "</yellow>",
            "<gray>Drop chance:</gray> <yellow>" + formatChance(plugin.settings().essenceChance(type)) + "</yellow>",
            "<gray>Source:</gray> " + (overridden ? "<green>Mob override</green>" : "<dark_gray>Global defaults</dark_gray>"),
            "",
            "<green>Left / Right:</green> amount ±1",
            "<yellow>Shift Left / Right:</yellow> chance ±5%",
            "<red>Drop key (Q):</red> reset override"
        );
    }

    private Inventory create(final AdminGuiHolder.Screen screen, final int page, final String title) {
        final AdminGuiHolder holder = new AdminGuiHolder(screen, page);
        final Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, miniMessage.deserialize(title));
        holder.bind(inventory);
        final ItemStack filler = ItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, "<gray> ");
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        return inventory;
    }

    private void setEssenceFromHand(final Player player) {
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!essenceService.setTemplate(hand)) {
            messages.send(player, "essence-empty-hand");
            return;
        }
        plugin.reloadPlugin();
        messages.send(player, "essence-set");
        openEssence(player);
    }

    private void giveTestEssence(final Player player) {
        player.getInventory().addItem(essenceService.create(1)).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );
    }

    private void toggle(final String path) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
    }

    private void adjustInt(final String path, final int min, final int max, final int step, final boolean increase) {
        final int current = plugin.getConfig().getInt(path);
        plugin.getConfig().set(path, Math.max(min, Math.min(max, current + (increase ? step : -step))));
    }

    private void adjustDouble(final String path, final double min, final double max, final double step, final boolean increase) {
        final double current = plugin.getConfig().getDouble(path);
        plugin.getConfig().set(path, clamp(current + (increase ? step : -step), min, max));
    }

    private void saveAndReload() {
        plugin.saveConfig();
        plugin.reloadPlugin();
    }

    private ItemStack toggleItem(final boolean enabled, final String label, final String explanation) {
        return ItemFactory.create(
            enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? "<green><b>" : "<red><b>") + label + ": " + (enabled ? "ENABLED" : "DISABLED") + "</b>",
            "<gray>" + explanation + "</gray>",
            "",
            "<yellow>Click to toggle.</yellow>"
        );
    }

    private static ItemStack backItem() {
        return ItemFactory.create(Material.ARROW, "<yellow><b>Back</b></yellow>", "<gray>Return to the previous menu.</gray>");
    }

    private static ItemStack closeItem() {
        return ItemFactory.create(Material.BARRIER, "<red><b>Close</b></red>");
    }

    private static Material mobIcon(final EntityType type) {
        final Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg == null ? Material.SPAWNER : egg;
    }

    private static String pretty(final EntityType type) {
        final String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String prettyDelivery(final PluginSettings.EssenceDelivery delivery) {
        return delivery == PluginSettings.EssenceDelivery.GROUND ? "Ground Drop" : "Player Inventory";
    }

    private static String formatChance(final double chance) {
        if (Math.rint(chance) == chance) {
            return ((int) chance) + "%";
        }
        return String.format(Locale.ROOT, "%.1f%%", chance);
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void clickSound(final Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
