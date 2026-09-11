package com.plexon.spawners.gui;

import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerAccess;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTier;
import com.plexon.spawners.managed.SpawnerTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.bukkit.entity.Player;

public final class SpawnerControlGui implements Listener {
    private static final int SIZE = 27;
    private static final int TYPE_SLOT = 10;
    private static final int TIER_SLOT = 12;
    private static final int ACCESS_SLOT = 14;
    private static final int UPGRADE_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final SpawnerTuning tuning;
    private final EssenceService essenceService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerControlGui(
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService,
        final SpawnerTuning tuning,
        final EssenceService essenceService
    ) {
        this.registry = registry;
        this.stateService = stateService;
        this.tuning = tuning;
        this.essenceService = essenceService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!tuning.enabled()
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null
            || event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }

        ManagedSpawner record = registry.find(event.getClickedBlock().getLocation());
        if (record == null && event.getClickedBlock().getState() instanceof org.bukkit.block.CreatureSpawner state) {
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
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>This spawner is private.</#FF6B6B>"
            ));
            return;
        }

        event.setCancelled(true);
        open(player, record.id());
    }

    public void open(final Player player, final UUID spawnerId) {
        final ManagedSpawner record = registry.find(spawnerId);
        if (record == null) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>This managed spawner is no longer available.</#FF6B6B>"
            ));
            return;
        }
        final boolean administrator = isAdministrator(player);
        if (!record.access().canUse(player.getUniqueId(), record.ownerId(), administrator)) {
            return;
        }

        final Inventory inventory = Bukkit.createInventory(
            new SpawnerControlGuiHolder(record.id()),
            SIZE,
            miniMessage.deserialize("<!italic><gradient:#56B9F2:#92E1FF><b>Spawner Control</b></gradient>")
        );
        render(inventory, player, record, administrator);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(final InventoryClickEvent event) {
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
            case ACCESS_SLOT -> cycleAccess(player, current, administrator);
            case UPGRADE_SLOT -> upgrade(player, current, administrator);
            default -> {
                // Read-only presentation slot.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnerControlGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void cycleAccess(final Player player, final ManagedSpawner current, final boolean administrator) {
        if (!current.access().canManage(player.getUniqueId(), current.ownerId(), administrator)) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>Only the owner or an administrator can change access.</#FF6B6B>"
            ));
            return;
        }

        final SpawnerAccess next = current.access().next();
        final ManagedSpawner updated = registry.updateAccess(current.id(), next);
        if (updated == null || !applyPhysical(updated)) {
            registry.updateAccess(current.id(), current.access());
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>Access change was rolled back because the physical spawner could not be updated.</#FF6B6B>"
            ));
            return;
        }
        player.sendMessage(miniMessage.deserialize(
            "<!italic><#72F1B8>Spawner access:</#72F1B8> <white>" + prettyAccess(next) + "</white>"
        ));
        open(player, current.id());
    }

    private void upgrade(final Player player, final ManagedSpawner current, final boolean administrator) {
        if (!current.access().canManage(player.getUniqueId(), current.ownerId(), administrator)) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>Only the owner or an administrator can upgrade this spawner.</#FF6B6B>"
            ));
            return;
        }

        final SpawnerTier nextTier = tuning.nextTier(current.tier());
        if (nextTier == null) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#72F1B8>This spawner is already at maximum tier.</#72F1B8>"
            ));
            return;
        }
        final int cost = nextTier.upgradeCostEssence();
        if (countEssence(player) < cost) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FFB86C>You need <white>" + cost + "</white> Spawner Essence for this upgrade.</#FFB86C>"
            ));
            return;
        }

        if (!consumeEssence(player, cost)) {
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>Could not reserve the required Spawner Essence.</#FF6B6B>"
            ));
            return;
        }

        final ManagedSpawner updated = registry.updateTier(current.id(), nextTier.level());
        if (updated == null || !applyPhysical(updated)) {
            registry.updateTier(current.id(), current.tier());
            refundEssence(player, cost);
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>Upgrade failed; the tier and Essence transaction were rolled back.</#FF6B6B>"
            ));
            return;
        }

        player.sendMessage(miniMessage.deserialize(
            "<!italic><#72F1B8>Spawner upgraded to <white>Tier " + nextTier.level() + " — " + nextTier.label() + "</white>.</#72F1B8>"
        ));
        open(player, current.id());
    }

    private void render(
        final Inventory inventory,
        final Player player,
        final ManagedSpawner record,
        final boolean administrator
    ) {
        final SpawnerTier tier = tuning.tier(record.tier());
        final boolean canManage = record.access().canManage(player.getUniqueId(), record.ownerId(), administrator);
        final String owner = record.ownerId().equals(player.getUniqueId())
            ? "You"
            : record.ownerId().toString().substring(0, 8) + "…";

        inventory.setItem(TYPE_SLOT, item(
            Material.SPAWNER,
            "<!italic><gradient:#56B9F2:#92E1FF><b>" + SpawnerItemService.pretty(record.type()) + " Spawner</b></gradient>",
            List.of(
                "",
                "<!italic><dark_gray>› <gray>Owner</gray> <white>" + owner + "</white>",
                "<!italic><dark_gray>› <gray>Lifetime spawns</gray> <white>" + record.lifetimeSpawns() + "</white>",
                "",
                "<!italic><dark_gray>ID " + record.id().toString().substring(0, 8) + "</dark_gray>"
            )
        ));

        inventory.setItem(TIER_SLOT, item(
            Material.EXPERIENCE_BOTTLE,
            "<!italic><gradient:#C850C0:#FF7EB3><b>Tier " + tier.level() + " — " + tier.label() + "</b></gradient>",
            List.of(
                "",
                "<!italic><dark_gray>› <gray>Delay</gray> <white>" + tier.minSpawnDelay() + "–" + tier.maxSpawnDelay() + "t</white>",
                "<!italic><dark_gray>› <gray>Spawn count</gray> <white>" + tier.spawnCount() + "</white>",
                "<!italic><dark_gray>› <gray>Nearby cap</gray> <white>" + tier.maxNearbyEntities() + "</white>",
                "<!italic><dark_gray>› <gray>Activation</gray> <white>" + tier.requiredPlayerRange() + " blocks</white>",
                "<!italic><dark_gray>› <gray>Spawn range</gray> <white>" + tier.spawnRange() + " blocks</white>"
            )
        ));

        final List<String> accessLore = new ArrayList<>();
        accessLore.add("");
        accessLore.add("<!italic><dark_gray>› <gray>Current</gray> <white>" + prettyAccess(record.access()) + "</white>");
        accessLore.add("");
        accessLore.add(canManage
            ? "<!italic><#72F1B8>Click to cycle access.</#72F1B8>"
            : "<!italic><dark_gray>Owner/admin control only.</dark_gray>");
        inventory.setItem(ACCESS_SLOT, item(
            Material.TRIPWIRE_HOOK,
            "<!italic><#D8DEE9><b>Access Policy</b></#D8DEE9>",
            accessLore
        ));

        final SpawnerTier next = tuning.nextTier(record.tier());
        final List<String> upgradeLore;
        if (next == null) {
            upgradeLore = List.of(
                "",
                "<!italic><#72F1B8>Maximum tier reached.</#72F1B8>"
            );
        } else {
            upgradeLore = List.of(
                "",
                "<!italic><dark_gray>› <gray>Next</gray> <white>Tier " + next.level() + " — " + next.label() + "</white>",
                "<!italic><dark_gray>› <gray>Cost</gray> <white>" + next.upgradeCostEssence() + " Essence</white>",
                "",
                canManage
                    ? "<!italic><#72F1B8>Click to upgrade.</#72F1B8>"
                    : "<!italic><dark_gray>Owner/admin control only.</dark_gray>"
            );
        }
        inventory.setItem(UPGRADE_SLOT, item(
            Material.ECHO_SHARD,
            "<!italic><gradient:#8A2BE2:#D56BFF><b>Upgrade Spawner</b></gradient>",
            upgradeLore
        ));

        inventory.setItem(CLOSE_SLOT, item(
            Material.BARRIER,
            "<!italic><#FF6B6B><b>Close</b></#FF6B6B>",
            List.of("<!italic><dark_gray>Return to the world.</dark_gray>")
        ));
    }

    private boolean applyPhysical(final ManagedSpawner record) {
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return false;
        }
        return stateService.applyAt(
            new Location(world, record.x(), record.y(), record.z()),
            record,
            tuning.tier(record.tier())
        );
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
            if (take == item.getAmount()) {
                contents[index] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    private void refundEssence(final Player player, final int amount) {
        if (amount <= 0) {
            return;
        }
        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(essenceService.createStacks(amount));
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
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
}
