package com.plexon.spawners.gui;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.config.NearbyStackCapSettings;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.NativeStackPolicy;
import com.plexon.spawners.managed.RedstoneSpawnerLockService;
import com.plexon.spawners.managed.SpawnerAccess;
import com.plexon.spawners.managed.SpawnerStackDisplayService;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTier;
import com.plexon.spawners.managed.SpawnerTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SpawnerControlGui implements Listener, AutoCloseable {
    private static final int SIZE = 27;
    private static final int TYPE_SLOT = 10;
    private static final int TIER_SLOT = 12;
    private static final int STATUS_SLOT = 13;
    private static final int ACCESS_SLOT = 14;
    private static final int WITHDRAW_SLOT = 15;
    private static final int UPGRADE_SLOT = 16;
    private static final int CLOSE_SLOT = 22;
    private static final int[] PRESET_SLOTS = {10, 12, 14};
    private static final int WITHDRAW_ALL_SLOT = 16;
    private static final int WITHDRAW_BACK_SLOT = 22;

    private final JavaPlugin plugin;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final SpawnerTuning tuning;
    private final NativeStackSettings stackSettings;
    private final SpawnerItemService spawnerItems;
    private final EssenceService essenceService;
    private final NearbyStackCapSettings nearbyStackCapSettings;
    private final WildStackerCompat wildStacker;
    private final RedstoneSpawnerLockService redstoneLocks;
    private final SpawnerStackDisplayService displays;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, UUID> openSessions = new HashMap<>();
    private final BukkitTask refreshTask;

    public SpawnerControlGui(
        final JavaPlugin plugin,
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService,
        final SpawnerTuning tuning,
        final NativeStackSettings stackSettings,
        final SpawnerItemService spawnerItems,
        final EssenceService essenceService,
        final NearbyStackCapSettings nearbyStackCapSettings,
        final WildStackerCompat wildStacker,
        final RedstoneSpawnerLockService redstoneLocks,
        final SpawnerStackDisplayService displays
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.stateService = stateService;
        this.tuning = tuning;
        this.stackSettings = stackSettings;
        this.spawnerItems = spawnerItems;
        this.essenceService = essenceService;
        this.nearbyStackCapSettings = nearbyStackCapSettings;
        this.wildStacker = wildStacker;
        this.redstoneLocks = redstoneLocks;
        this.displays = displays;
        this.refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshOpenViews, 10L, 10L);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!tuning.enabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
            || event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }
        ManagedSpawner record = registry.find(event.getClickedBlock().getLocation());
        if (record == null && event.getClickedBlock().getState() instanceof CreatureSpawner state) {
            record = stateService.recover(state);
            if (record != null) {
                registry.register(record);
            }
        }
        if (record == null) {
            return;
        }
        final Player player = event.getPlayer();
        final boolean administrator = isAdministrator(player);
        if (!record.access().canUse(player.getUniqueId(), record.ownerId(), administrator)) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize("<!italic><#FF6B6B>This spawner is private.</#FF6B6B>"));
            return;
        }
        event.setCancelled(true);
        open(player, record.id());
    }

    public void open(final Player player, final UUID spawnerId) {
        final ManagedSpawner record = registry.find(spawnerId);
        if (record == null) {
            player.sendMessage(miniMessage.deserialize("<!italic><#FF6B6B>This managed spawner is no longer available.</#FF6B6B>"));
            return;
        }
        final boolean administrator = isAdministrator(player);
        if (!record.access().canUse(player.getUniqueId(), record.ownerId(), administrator)) {
            return;
        }
        final Inventory inventory = Bukkit.createInventory(new SpawnerControlGuiHolder(record.id()), SIZE,
            miniMessage.deserialize("<!italic><gradient:#56B9F2:#92E1FF><b>Spawner Control</b></gradient>"));
        render(inventory, player, record, administrator);
        openSessions.put(player.getUniqueId(), record.id());
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnerWithdrawGuiHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0 && event.getRawSlot() < SIZE) {
                handleWithdrawClick(player, holder.spawnerId(), event.getRawSlot());
            }
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerControlGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) {
            return;
        }
        final ManagedSpawner current = registry.find(holder.spawnerId());
        if (current == null) {
            player.closeInventory();
            return;
        }
        final boolean administrator = isAdministrator(player);
        switch (event.getRawSlot()) {
            case CLOSE_SLOT -> player.closeInventory();
            case STATUS_SLOT -> refreshDynamic(event.getView().getTopInventory(), player, current, administrator);
            case ACCESS_SLOT -> cycleAccess(player, current, administrator);
            case WITHDRAW_SLOT -> openWithdraw(player, current.id());
            case UPGRADE_SLOT -> upgrade(player, current.id(), administrator);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnerControlGuiHolder
            || event.getView().getTopInventory().getHolder() instanceof SpawnerWithdrawGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerControlGuiHolder
            || event.getInventory().getHolder() instanceof SpawnerWithdrawGuiHolder) {
            openSessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private void cycleAccess(final Player player, final ManagedSpawner current, final boolean administrator) {
        if (!current.access().canManage(player.getUniqueId(), current.ownerId(), administrator)) {
            deny(player, "Only the owner or an administrator can change access.");
            return;
        }
        if (current.migrationState().blocksMutation()) {
            deny(player, "Stack migration must be resolved before changing this spawner.");
            return;
        }
        final SpawnerAccess next = current.access().next();
        final ManagedSpawner updated = registry.updateAccess(current.id(), next);
        if (updated == null || !applyPhysical(updated)) {
            registry.updateAccess(current.id(), current.access());
            deny(player, "Access change was rolled back because the physical spawner could not be updated.");
            return;
        }
        redstoneLocks.refresh(updated);
        displays.refresh(updated);
        player.sendMessage(miniMessage.deserialize("<!italic><#72F1B8>Spawner access:</#72F1B8> <white>" + prettyAccess(next) + "</white>"));
        open(player, current.id());
    }

    private void upgrade(final Player player, final UUID spawnerId, final boolean administrator) {
        final ManagedSpawner current = registry.find(spawnerId);
        if (current == null) {
            player.closeInventory();
            return;
        }
        if (!current.access().canManage(player.getUniqueId(), current.ownerId(), administrator)) {
            deny(player, "Only the owner or an administrator can upgrade this spawner.");
            return;
        }
        if (current.migrationState().blocksMutation()) {
            deny(player, "Stack migration must be resolved before upgrading this spawner.");
            return;
        }
        final SpawnerTier nextTier = tuning.nextTier(current.tier());
        if (nextTier == null) {
            player.sendMessage(miniMessage.deserialize("<!italic><#72F1B8>This spawner is already at maximum tier.</#72F1B8>"));
            return;
        }
        final long longCost = NativeStackPolicy.upgradeCost(nextTier.upgradeCostEssence(), current.stackAmount());
        if (longCost > Integer.MAX_VALUE) {
            deny(player, "This stack's upgrade cost exceeds the supported transaction limit.");
            return;
        }
        final int cost = (int) longCost;
        if (countEssence(player) < cost) {
            player.sendMessage(miniMessage.deserialize("<!italic><#FFB86C>You need <white>" + cost + "</white> Spawner Essence for this stack upgrade.</#FFB86C>"));
            return;
        }
        if (!consumeEssence(player, cost)) {
            deny(player, "Could not reserve the required Spawner Essence.");
            return;
        }
        final ManagedSpawner updated = registry.updateTier(current.id(), nextTier.level());
        if (updated == null || !applyPhysical(updated)) {
            registry.updateTier(current.id(), current.tier());
            refundEssence(player, cost);
            deny(player, "Upgrade failed; the tier and Essence transaction were rolled back.");
            return;
        }
        redstoneLocks.refresh(updated);
        displays.refresh(updated);
        player.sendMessage(miniMessage.deserialize("<!italic><#72F1B8>Spawner stack upgraded to <white>Tier "
            + nextTier.level() + " — " + nextTier.label() + "</white>.</#72F1B8>"));
        open(player, current.id());
    }

    private void openWithdraw(final Player player, final UUID spawnerId) {
        final ManagedSpawner record = registry.find(spawnerId);
        if (record == null || !stackSettings.withdrawEnabled()) {
            return;
        }
        final Inventory inventory = Bukkit.createInventory(new SpawnerWithdrawGuiHolder(spawnerId), SIZE,
            miniMessage.deserialize("<!italic><gradient:#56B9F2:#92E1FF><b>Withdraw Spawners</b></gradient>"));
        renderWithdraw(inventory, record);
        openSessions.put(player.getUniqueId(), spawnerId);
        player.openInventory(inventory);
    }

    private void renderWithdraw(final Inventory inventory, final ManagedSpawner record) {
        inventory.clear();
        final List<Integer> presets = stackSettings.withdrawPresets();
        for (int index = 0; index < PRESET_SLOTS.length && index < presets.size(); index++) {
            final int amount = presets.get(index);
            if (amount > record.stackAmount()) {
                continue;
            }
            inventory.setItem(PRESET_SLOTS[index], item(Material.HOPPER,
                "<!italic><#D8DEE9><b>Withdraw " + amount + "</b></#D8DEE9>",
                List.of("", "<!italic><gray>Take <white>x" + amount + "</white> from this logical stack.</gray>")));
        }
        inventory.setItem(WITHDRAW_ALL_SLOT, item(Material.CHEST,
            "<!italic><#FFD866><b>Withdraw All</b></#FFD866>",
            List.of("", "<!italic><gray>Take all <white>x" + record.stackAmount() + "</white> and remove the physical spawner.</gray>")));
        inventory.setItem(WITHDRAW_BACK_SLOT, item(Material.ARROW,
            "<!italic><#D8DEE9><b>Back</b></#D8DEE9>", List.of("<!italic><dark_gray>Return to Spawner Control.</dark_gray>")));
    }

    private void handleWithdrawClick(final Player player, final UUID spawnerId, final int slot) {
        if (slot == WITHDRAW_BACK_SLOT) {
            open(player, spawnerId);
            return;
        }
        final ManagedSpawner current = registry.find(spawnerId);
        if (current == null) {
            player.closeInventory();
            return;
        }
        final boolean administrator = isAdministrator(player);
        if (!current.access().canManage(player.getUniqueId(), current.ownerId(), administrator)) {
            deny(player, "Only the owner or an administrator can withdraw this stack.");
            return;
        }
        if (current.migrationState().blocksMutation()) {
            deny(player, "Stack migration must be resolved before withdrawing.");
            return;
        }
        int amount = -1;
        for (int index = 0; index < PRESET_SLOTS.length && index < stackSettings.withdrawPresets().size(); index++) {
            if (PRESET_SLOTS[index] == slot) {
                amount = stackSettings.withdrawPresets().get(index);
                break;
            }
        }
        if (slot == WITHDRAW_ALL_SLOT) {
            amount = current.stackAmount();
        }
        if (amount < 1 || amount > current.stackAmount()) {
            return;
        }
        withdraw(player, current, amount);
    }

    private void withdraw(final Player player, final ManagedSpawner current, final int amount) {
        final int remaining = current.stackAmount() - amount;
        if (remaining > 0) {
            final ManagedSpawner updated = registry.updateStackAmount(current.id(), remaining);
            if (updated == null || !applyPhysical(updated)) {
                if (updated != null) {
                    registry.updateStackAmount(current.id(), current.stackAmount());
                }
                deny(player, "Withdraw failed safely; the logical stack was not changed.");
                return;
            }
            displays.refresh(updated);
        } else {
            final CreatureSpawner physical = physicalSpawner(current);
            if (physical == null) {
                deny(player, "The physical spawner is not currently available.");
                return;
            }
            displays.remove(current);
            registry.remove(current.id());
            physical.getBlock().setType(Material.AIR, false);
        }
        for (final ItemStack stack : spawnerItems.createSpawnerStacks(current.type(), amount, current.tier())) {
            player.getInventory().addItem(stack).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        final ManagedSpawner next = registry.find(current.id());
        if (next == null) {
            player.closeInventory();
        } else {
            openWithdraw(player, current.id());
        }
    }

    private void render(final Inventory inventory, final Player player, final ManagedSpawner record, final boolean administrator) {
        inventory.clear();
        final SpawnerTier tier = tuning.tier(record.tier());
        final boolean canManage = record.access().canManage(player.getUniqueId(), record.ownerId(), administrator);
        final String owner = record.ownerId().equals(player.getUniqueId()) ? "You" : record.ownerId().toString().substring(0, 8) + "…";
        inventory.setItem(TYPE_SLOT, typeItem(record, owner));
        inventory.setItem(TIER_SLOT, item(Material.EXPERIENCE_BOTTLE,
            "<!italic><gradient:#C850C0:#FF7EB3><b>Tier " + tier.level() + " — " + tier.label() + "</b></gradient>",
            List.of("", "<!italic><dark_gray>› <gray>Delay</gray> <white>" + tier.minSpawnDelay() + "–" + tier.maxSpawnDelay() + "t</white>",
                "<!italic><dark_gray>› <gray>Spawn count</gray> <white>" + tier.spawnCount() + "</white>",
                "<!italic><dark_gray>› <gray>Activation</gray> <white>" + tier.requiredPlayerRange() + " blocks</white>")));
        inventory.setItem(STATUS_SLOT, runtimeStatusItem(record));
        inventory.setItem(ACCESS_SLOT, accessItem(record, canManage));
        inventory.setItem(WITHDRAW_SLOT, withdrawItem(record, canManage));
        inventory.setItem(UPGRADE_SLOT, upgradeItem(record, canManage));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
            "<!italic><#FF6B6B><b>Close</b></#FF6B6B>", List.of("<!italic><dark_gray>Return to the world.</dark_gray>")));
    }

    private void refreshDynamic(final Inventory inventory, final Player player, final ManagedSpawner record, final boolean administrator) {
        final boolean canManage = record.access().canManage(player.getUniqueId(), record.ownerId(), administrator);
        final String owner = record.ownerId().equals(player.getUniqueId()) ? "You" : record.ownerId().toString().substring(0, 8) + "…";
        inventory.setItem(TYPE_SLOT, typeItem(record, owner));
        inventory.setItem(STATUS_SLOT, runtimeStatusItem(record));
        inventory.setItem(WITHDRAW_SLOT, withdrawItem(record, canManage));
        inventory.setItem(UPGRADE_SLOT, upgradeItem(record, canManage));
    }

    private ItemStack typeItem(final ManagedSpawner record, final String owner) {
        return item(Material.SPAWNER,
            "<!italic><gradient:#56B9F2:#92E1FF><b>" + SpawnerItemService.pretty(record.type()) + " Spawner</b></gradient>",
            List.of("", "<!italic><dark_gray>› <gray>Stack</gray> <white>x" + record.stackAmount() + " / " + stackSettings.maxStackSize() + "</white>",
                "<!italic><dark_gray>› <gray>Owner</gray> <white>" + owner + "</white>",
                "<!italic><dark_gray>› <gray>Lifetime spawns</gray> <white>" + record.lifetimeSpawns() + "</white>",
                "<!italic><dark_gray>› <gray>Migration</gray> <white>" + record.migrationState().name() + "</white>",
                "<!italic><dark_gray>› <gray>Entity stacking</gray> <white>WildStacker " + wildStacker.status() + "</white>"));
    }

    private ItemStack accessItem(final ManagedSpawner record, final boolean canManage) {
        return item(Material.TRIPWIRE_HOOK, "<!italic><#D8DEE9><b>Access Policy</b></#D8DEE9>",
            List.of("", "<!italic><dark_gray>› <gray>Current</gray> <white>" + prettyAccess(record.access()) + "</white>", "",
                canManage ? "<!italic><#72F1B8>Click to cycle access.</#72F1B8>" : "<!italic><dark_gray>Owner/admin control only.</dark_gray>"));
    }

    private ItemStack withdrawItem(final ManagedSpawner record, final boolean canManage) {
        return item(Material.HOPPER, "<!italic><#D8DEE9><b>Withdraw</b></#D8DEE9>",
            List.of("", "<!italic><dark_gray>› <gray>Available</gray> <white>x" + record.stackAmount() + "</white>", "",
                canManage && stackSettings.withdrawEnabled() ? "<!italic><#72F1B8>Click for withdrawal options.</#72F1B8>" : "<!italic><dark_gray>Unavailable.</dark_gray>"));
    }

    private ItemStack upgradeItem(final ManagedSpawner record, final boolean canManage) {
        final SpawnerTier next = tuning.nextTier(record.tier());
        if (next == null) {
            return item(Material.ECHO_SHARD, "<!italic><gradient:#8A2BE2:#D56BFF><b>Upgrade Spawner</b></gradient>",
                List.of("", "<!italic><#72F1B8>Maximum tier reached.</#72F1B8>"));
        }
        final long finalCost = NativeStackPolicy.upgradeCost(next.upgradeCostEssence(), record.stackAmount());
        return item(Material.ECHO_SHARD, "<!italic><gradient:#8A2BE2:#D56BFF><b>Upgrade Spawner</b></gradient>",
            List.of("", "<!italic><dark_gray>› <gray>Next</gray> <white>Tier " + next.level() + " — " + next.label() + "</white>",
                "<!italic><dark_gray>› <gray>Base Cost</gray> <white>" + next.upgradeCostEssence() + " Essence</white>",
                "<!italic><dark_gray>› <gray>Stack Amount</gray> <white>x" + record.stackAmount() + "</white>",
                "<!italic><dark_gray>› <gray>Final Cost</gray> <white>" + finalCost + " Essence</white>", "",
                canManage ? "<!italic><#72F1B8>Click to upgrade the entire stack.</#72F1B8>" : "<!italic><dark_gray>Owner/admin control only.</dark_gray>"));
    }

    private ItemStack runtimeStatusItem(final ManagedSpawner record) {
        final SpawnerTier tier = tuning.tier(record.tier());
        final boolean locked = redstoneLocks.isLocked(record.id());
        final boolean powered = redstoneLocks.isPowered(record);
        final int ticks = redstoneLocks.nextSpawnTicks(record);
        final String countdown = ticks < 0 ? "Unavailable" : ticks + "t (~" + String.format(Locale.ROOT, "%.1f", ticks / 20.0D) + "s)";
        final int requested = NativeStackPolicy.requestedLogicalOutput(tier.spawnCount(), record.stackAmount(),
            stackSettings.maxLogicalOutputPerCycle(), stackSettings.scaleWithStack());
        final String cap = nearbyStackCapSettings.enabled()
            ? nearbyStackCapSettings.maximumAmount() + " logical / " + nearbyStackCapSettings.radius() + " blocks"
            : "Disabled";
        return item(locked ? Material.REDSTONE_TORCH : Material.CLOCK,
            "<!italic><#D8DEE9><b>Runtime Status</b></#D8DEE9>",
            List.of("", "<!italic><dark_gray>› <gray>Stack</gray> <white>x" + record.stackAmount() + " / " + stackSettings.maxStackSize() + "</white>",
                "<!italic><dark_gray>› <gray>Next spawn</gray> <white>" + countdown + "</white>",
                "<!italic><dark_gray>› <gray>Spawn count</gray> <white>" + tier.spawnCount() + "</white>",
                "<!italic><dark_gray>› <gray>Stack multiplier</gray> <white>x" + record.stackAmount() + "</white>",
                "<!italic><dark_gray>› <gray>Requested logical output</gray> <white>" + requested + "</white>",
                "<!italic><dark_gray>› <gray>Cycle output cap</gray> <white>" + stackSettings.maxLogicalOutputPerCycle() + "</white>",
                "<!italic><dark_gray>› <gray>Nearby logical cap</gray> <white>" + cap + "</white>",
                "<!italic><dark_gray>› <gray>Redstone</gray> <white>" + (locked ? "Locked" : "Unlocked") + " / " + (powered ? "Powered" : "No signal") + "</white>",
                "<!italic><dark_gray>› <gray>Entity stacking</gray> <white>WildStacker " + wildStacker.status() + "</white>"));
    }

    private void refreshOpenViews() {
        final List<UUID> stale = new ArrayList<>();
        for (final Map.Entry<UUID, UUID> entry : openSessions.entrySet()) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            final ManagedSpawner record = registry.find(entry.getValue());
            if (player == null || record == null) {
                stale.add(entry.getKey());
                if (player != null) {
                    player.closeInventory();
                }
                continue;
            }
            final Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SpawnerControlGuiHolder holder && holder.spawnerId().equals(record.id())) {
                refreshDynamic(top, player, record, isAdministrator(player));
            } else if (top.getHolder() instanceof SpawnerWithdrawGuiHolder holder && holder.spawnerId().equals(record.id())) {
                renderWithdraw(top, record);
            } else {
                stale.add(entry.getKey());
            }
        }
        stale.forEach(openSessions::remove);
    }

    private CreatureSpawner physicalSpawner(final ManagedSpawner record) {
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return null;
        }
        final Location location = new Location(world, record.x(), record.y(), record.z());
        return location.getBlock().getState() instanceof CreatureSpawner spawner ? spawner : null;
    }

    private boolean applyPhysical(final ManagedSpawner record) {
        final CreatureSpawner spawner = physicalSpawner(record);
        return spawner != null && stateService.apply(spawner, record, tuning.tier(record.tier()));
    }

    private int countEssence(final Player player) {
        int total = 0;
        for (final ItemStack item : player.getInventory().getStorageContents()) {
            if (essenceService.isEssence(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean consumeEssence(final Player player, final int required) {
        if (required <= 0) {
            return true;
        }
        if (countEssence(player) < required) {
            return false;
        }
        int remaining = required;
        final ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            final ItemStack item = contents[index];
            if (!essenceService.isEssence(item)) {
                continue;
            }
            final int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            contents[index] = take == item.getAmount() ? null : item;
            if (contents[index] != null) {
                contents[index].setAmount(item.getAmount() - take);
            }
        }
        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    private void refundEssence(final Player player, final int amount) {
        if (amount <= 0) {
            return;
        }
        player.getInventory().addItem(essenceService.createStacks(amount)).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private ItemStack item(final Material material, final String name, final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(name));
        final List<Component> lore = new ArrayList<>(loreLines.size());
        for (final String line : loreLines) {
            lore.add(miniMessage.deserialize(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void deny(final Player player, final String message) {
        player.sendMessage(miniMessage.deserialize("<!italic><#FF6B6B>" + message + "</#FF6B6B>"));
    }

    private static String prettyAccess(final SpawnerAccess access) {
        return switch (access) {
            case OWNER_ONLY -> "Owner Only";
            case PUBLIC_USE -> "Public Use";
            case PUBLIC -> "Public";
        };
    }

    private static boolean isAdministrator(final Player player) {
        return player.hasPermission("plexonspawners.admin") || player.hasPermission("plexonspawners.bypass.access");
    }

    @Override
    public void close() {
        refreshTask.cancel();
        openSessions.clear();
    }
}
