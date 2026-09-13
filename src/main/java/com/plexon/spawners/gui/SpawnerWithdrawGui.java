package com.plexon.spawners.gui;

import com.bgsoftware.wildstacker.api.enums.UnstackResult;
import com.bgsoftware.wildstacker.api.objects.StackedSpawner;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.integration.WildStackerBridge;
import com.plexon.spawners.message.MessageService;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerWithdrawGui implements Listener {
    private static final int[] PRESET_SLOTS = {10, 11, 12, 13, 14};
    private static final int ALL_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final MessageService messages;
    private final WildStackerBridge wildStacker;
    private final NamespacedKey withdrawAmountKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerWithdrawGui(
        final JavaPlugin plugin,
        final PluginSettings settings,
        final MessageService messages,
        final WildStackerBridge wildStacker
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.wildStacker = wildStacker;
        this.withdrawAmountKey = new NamespacedKey(plugin, "withdraw_amount");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!settings.guiEnabled() || !settings.guiOpenOnRightClick()) return;
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        final Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SPAWNER) return;
        if (!event.getPlayer().hasPermission("plexonspawners.gui")) return;

        final StackedSpawner stacked = wildStacker.resolve(clicked.getLocation());
        if (stacked == null) {
            if (settings.withdrawFailedMessage()) messages.send(event.getPlayer(), "withdraw-unavailable");
            return;
        }

        event.setCancelled(true);
        open(event.getPlayer(), clicked.getLocation());
    }

    public void open(final Player player, final org.bukkit.Location location) {
        final SpawnerWithdrawGuiHolder holder = new SpawnerWithdrawGuiHolder(player.getUniqueId(), location);
        final Component title = miniMessage.deserialize(settings.guiTitle());
        final Inventory inventory = Bukkit.createInventory(holder, 27, title);
        render(player, holder, inventory);
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerWithdrawGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(holder.playerId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        if (event.getRawSlot() == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        final ItemStack clicked = event.getView().getTopInventory().getItem(event.getRawSlot());
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        final Integer encoded = clicked.getItemMeta().getPersistentDataContainer()
            .get(withdrawAmountKey, PersistentDataType.INTEGER);
        if (encoded == null) return;

        final StackedSpawner stacked = wildStacker.resolve(holder.spawnerLocation());
        if (stacked == null) {
            fail(player, holder, event.getView().getTopInventory(), "withdraw-stale");
            return;
        }

        final int current = stacked.getStackAmount();
        final int requested = encoded == -1 ? WithdrawalPolicy.maximumWithdrawable(current) : encoded;
        if (!WithdrawalPolicy.isValidRequest(current, requested)) {
            fail(player, holder, event.getView().getTopInventory(), "withdraw-invalid");
            return;
        }

        final ItemStack authoritativeItem = wildStacker.createSpawnerItem(stacked, requested);
        if (authoritativeItem == null || authoritativeItem.getType().isAir()) {
            fail(player, holder, event.getView().getTopInventory(), "withdraw-failed");
            return;
        }

        final UnstackResult result = wildStacker.withdraw(stacked, requested, player);
        if (result != UnstackResult.SUCCESS) {
            fail(player, holder, event.getView().getTopInventory(), "withdraw-failed");
            return;
        }

        deliver(player, authoritativeItem);
        if (settings.withdrawSuccessMessage()) {
            messages.send(player, "withdraw-success", Map.of("amount", Integer.toString(requested)));
        }
        render(player, holder, event.getView().getTopInventory());
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerWithdrawGuiHolder)) return;
        final int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    private void render(final Player player, final SpawnerWithdrawGuiHolder holder, final Inventory inventory) {
        inventory.clear();
        final StackedSpawner stacked = wildStacker.resolve(holder.spawnerLocation());
        if (stacked == null) {
            inventory.setItem(13, item(Material.BARRIER, "<#FF6B6B><b>Spawner unavailable</b></#FF6B6B>",
                List.of("<gray>The WildStacker stack no longer exists.</gray>"), null));
            inventory.setItem(CLOSE_SLOT, closeItem());
            return;
        }

        final int amount = stacked.getStackAmount();
        final int maximum = WithdrawalPolicy.maximumWithdrawable(amount);
        inventory.setItem(4, item(Material.SPAWNER,
            "<gradient:#56B9F2:#92E1FF><b>" + pretty(stacked.getSpawnedType().name()) + " Spawner</b></gradient>",
            List.of("<gray>WildStacker amount:</gray> <white>" + amount + "</white>",
                "<gray>Available to withdraw:</gray> <white>" + maximum + "</white>"), null));

        int slotIndex = 0;
        for (final int preset : settings.withdrawPresets()) {
            if (slotIndex >= PRESET_SLOTS.length) break;
            if (preset <= maximum) {
                inventory.setItem(PRESET_SLOTS[slotIndex++], withdrawItem(preset, false));
            }
        }

        if (maximum > 0) {
            inventory.setItem(ALL_SLOT, withdrawItem(-1, true));
        } else {
            inventory.setItem(13, item(Material.BARRIER, "<#FFD166><b>Nothing to withdraw</b></#FFD166>",
                List.of("<gray>The physical block must retain one logical spawner.</gray>"), null));
        }
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private ItemStack withdrawItem(final int amount, final boolean all) {
        return item(Material.CHEST,
            all ? "<#72F1B8><b>Withdraw all available</b></#72F1B8>" : "<#72F1B8><b>Withdraw " + amount + "</b></#72F1B8>",
            List.of("<gray>Uses WildStacker's authoritative spawner item.</gray>", "<#8B95A7>Click to withdraw.</#8B95A7>"), amount);
    }

    private ItemStack closeItem() {
        return item(Material.BARRIER, "<#FF6B6B><b>Close</b></#FF6B6B>", List.of(), null);
    }

    private ItemStack item(final Material material, final String name, final List<String> lore, final Integer withdrawAmount) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(miniMessage::deserialize).toList());
        if (withdrawAmount != null) {
            meta.getPersistentDataContainer().set(withdrawAmountKey, PersistentDataType.INTEGER, withdrawAmount);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void deliver(final Player player, final ItemStack item) {
        try {
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (final ItemStack extra : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
        } catch (final RuntimeException exception) {
            plugin.getLogger().warning("Inventory delivery failed after successful WildStacker unstack; dropping item safely: "
                + exception.getMessage());
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private void fail(final Player player, final SpawnerWithdrawGuiHolder holder, final Inventory inventory, final String key) {
        if (settings.withdrawFailedMessage()) messages.send(player, key);
        render(player, holder, inventory);
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
