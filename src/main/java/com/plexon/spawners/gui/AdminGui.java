package com.plexon.spawners.gui;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.util.ItemFactory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AdminGui implements Listener {
    private static final int SLOT_INFO = 4;
    private static final int SLOT_PREVIEW = 10;
    private static final int SLOT_SET_FROM_HAND = 12;
    private static final int SLOT_AMOUNT = 14;
    private static final int SLOT_SILK_LEVEL = 16;
    private static final int SLOT_TOGGLE_ESSENCE = 20;
    private static final int SLOT_TOGGLE_SPAWNER = 22;
    private static final int SLOT_TEST_ESSENCE = 24;
    private static final int SLOT_CLOSE = 26;

    private final PlexonSpawners plugin;
    private final EssenceService essenceService;
    private final MessageService messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AdminGui(final PlexonSpawners plugin, final EssenceService essenceService, final MessageService messages) {
        this.plugin = plugin;
        this.essenceService = essenceService;
        this.messages = messages;
    }

    public void open(final Player player) {
        final AdminGuiHolder holder = new AdminGuiHolder();
        final Inventory inventory = Bukkit.createInventory(
            holder,
            27,
            miniMessage.deserialize("<gradient:#8A2BE2:#D56BFF><b>PlexonSpawners Admin</b></gradient>")
        );
        holder.bind(inventory);

        final ItemStack filler = ItemFactory.create(Material.GRAY_STAINED_GLASS_PANE, "<gray> ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(SLOT_INFO, ItemFactory.create(
            Material.SPAWNER,
            "<gradient:#8A2BE2:#D56BFF><b>PlexonSpawners</b></gradient>",
            "<gray>Standalone spawner handling.</gray>",
            "<dark_gray>No SilkSpawners dependency.</dark_gray>"
        ));

        final ItemStack preview = essenceService.template();
        inventory.setItem(SLOT_PREVIEW, preview);
        inventory.setItem(SLOT_SET_FROM_HAND, ItemFactory.create(
            Material.ANVIL,
            "<green><b>Set Essence From Hand</b></green>",
            "<gray>Copies the exact item in your main hand.</gray>",
            "<gray>PlexonSpawners adds its secure PDC identity.</gray>"
        ));

        final int essenceAmount = plugin.settings().defaultEssenceAmount();
        inventory.setItem(SLOT_AMOUNT, ItemFactory.create(
            Material.ECHO_SHARD,
            "<light_purple><b>Default Essence Amount: " + essenceAmount + "</b></light_purple>",
            "<gray>Left click:</gray> <green>+1</green>",
            "<gray>Right click:</gray> <red>-1</red>",
            "<gray>Shift modifies by 5.</gray>",
            "<dark_gray>Mob overrides remain in config for this milestone.</dark_gray>"
        ));

        final int silkLevel = plugin.settings().requiredSilkTouchLevel();
        inventory.setItem(SLOT_SILK_LEVEL, ItemFactory.create(
            Material.ENCHANTED_BOOK,
            "<aqua><b>Required Silk Touch: " + silkLevel + "</b></aqua>",
            "<gray>Left click:</gray> <green>+1</green>",
            "<gray>Right click:</gray> <red>-1</red>",
            "<gray>Shift modifies by 5.</gray>",
            "<dark_gray>0 means every break qualifies.</dark_gray>"
        ));

        inventory.setItem(SLOT_TOGGLE_ESSENCE, toggleItem(
            plugin.settings().essenceEnabled(),
            "Failed Break Essence",
            "essence.enabled"
        ));
        inventory.setItem(SLOT_TOGGLE_SPAWNER, toggleItem(
            plugin.settings().dropSpawnerWhenQualified(),
            "Qualified Spawner Drop",
            "breaking.drop-spawner-when-qualified"
        ));

        inventory.setItem(SLOT_TEST_ESSENCE, ItemFactory.create(
            Material.CHEST,
            "<yellow><b>Give Test Essence</b></yellow>",
            "<gray>Adds one configured Essence to your inventory.</gray>"
        ));
        inventory.setItem(SLOT_CLOSE, ItemFactory.create(Material.BARRIER, "<red><b>Close</b></red>"));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGuiHolder)) {
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

        final int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_SET_FROM_HAND -> setEssenceFromHand(player);
            case SLOT_AMOUNT -> adjustInt(player, "essence.default-amount", 1, 4096, event.isShiftClick() ? 5 : 1, event.isLeftClick());
            case SLOT_SILK_LEVEL -> adjustInt(player, "breaking.required-silk-touch-level", 0, 255, event.isShiftClick() ? 5 : 1, event.isLeftClick());
            case SLOT_TOGGLE_ESSENCE -> toggle(player, "essence.enabled");
            case SLOT_TOGGLE_SPAWNER -> toggle(player, "breaking.drop-spawner-when-qualified");
            case SLOT_TEST_ESSENCE -> giveTestEssence(player);
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
                return;
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void setEssenceFromHand(final Player player) {
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!essenceService.setTemplate(hand)) {
            messages.send(player, "essence-empty-hand");
            return;
        }
        plugin.reloadPlugin();
        messages.send(player, "essence-set");
        open(player);
    }

    private void adjustInt(
        final Player player,
        final String path,
        final int min,
        final int max,
        final int step,
        final boolean increase
    ) {
        final int current = plugin.getConfig().getInt(path);
        final int next = Math.max(min, Math.min(max, current + (increase ? step : -step)));
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadPlugin();
        open(player);
    }

    private void toggle(final Player player, final String path) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
        plugin.saveConfig();
        plugin.reloadPlugin();
        open(player);
    }

    private void giveTestEssence(final Player player) {
        player.getInventory().addItem(essenceService.create(1)).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );
    }

    private ItemStack toggleItem(final boolean enabled, final String label, final String path) {
        return ItemFactory.create(
            enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? "<green><b>" : "<red><b>") + label + ": " + (enabled ? "ENABLED" : "DISABLED") + "</b>",
            "<gray>Click to toggle.</gray>",
            "<dark_gray>" + path + "</dark_gray>"
        );
    }
}
