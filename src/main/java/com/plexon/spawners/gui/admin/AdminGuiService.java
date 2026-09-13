package com.plexon.spawners.gui.admin;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.breaking.NonSilkRewardMode;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.essence.EssenceService;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.reward.CustomDropService;
import com.plexon.spawners.reward.RewardItemFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminGuiService implements Listener {
    private static final int MAIN_STATUS = 4;
    private static final int MAIN_BREAK = 10;
    private static final int MAIN_ESSENCE = 11;
    private static final int MAIN_CUSTOM = 12;
    private static final int MAIN_MOBS = 13;
    private static final int MAIN_WITHDRAW = 14;
    private static final int MAIN_WORLDS = 15;
    private static final int MAIN_MESSAGES = 16;
    private static final int MAIN_DISCARD = 20;
    private static final int MAIN_SAVE = 22;
    private static final int MAIN_RELOAD = 24;
    private static final int MAIN_CLOSE = 26;

    private final PlexonSpawners plugin;
    private final PluginSettings settings;
    private final MessageService messages;
    private final EssenceService essence;
    private final CustomDropService customDrop;
    private final ConfigRevisionService revisions;
    private final AdminConfigPersistence persistence;
    private final RewardItemFactory itemFactory = new RewardItemFactory();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey previewKey;
    private final Map<UUID, AdminSettingsSession> sessions = new ConcurrentHashMap<>();

    public AdminGuiService(
        final PlexonSpawners plugin,
        final PluginSettings settings,
        final MessageService messages,
        final EssenceService essence,
        final CustomDropService customDrop,
        final ConfigRevisionService revisions
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.essence = essence;
        this.customDrop = customDrop;
        this.revisions = revisions;
        this.persistence = new AdminConfigPersistence(plugin, revisions);
        this.previewKey = new NamespacedKey(plugin, "admin_preview");
    }

    public boolean open(final Player player) {
        if (!settings.adminGuiEnabled()) {
            messages.send(player, "admin-gui-disabled");
            return false;
        }
        if (!player.hasPermission("plexonspawners.admin.gui")) {
            messages.send(player, "no-permission");
            return false;
        }
        final AdminSettingsSession session = sessions.compute(player.getUniqueId(), (ignored, existing) -> {
            if (existing != null) return existing;
            return new AdminSettingsSession(player.getUniqueId(), AdminSettingsDraft.from(plugin.getConfig()), revisions.current());
        });
        session.touch();
        openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        return true;
    }

    public void invalidateSessions() {
        for (final AdminSettingsSession session : sessions.values()) session.touch();
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.playerId().equals(player.getUniqueId())) return;
        final AdminSettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        session.touch();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        switch (holder.screen()) {
            case MAIN -> clickMain(player, session, event.getRawSlot());
            case BREAK -> clickBreak(player, session, event);
            case ESSENCE -> clickEssence(player, session, event);
            case CUSTOM -> clickCustom(player, session, event);
            case MOBS -> clickMobs(player, session, holder, event);
            case MOB_EDIT -> clickMobEdit(player, session, holder.entityType(), event);
            case WITHDRAWAL -> clickWithdrawal(player, session, event);
            case WORLDS -> clickWorlds(player, session, event);
            case MESSAGES -> clickMessages(player, session, event);
            case CONFIRM_SAVE -> clickConfirmSave(player, session, event.getRawSlot());
            case CONFIRM_DISCARD -> clickConfirmDiscard(player, session, event.getRawSlot());
            case CONFIRM_RELOAD -> clickConfirmReload(player, session, event.getRawSlot());
            case CONFIRM_MOB_RESET -> clickConfirmMobReset(player, session, holder.entityType(), event.getRawSlot());
            case STALE -> clickStale(player, session, event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminGuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGuiHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                sessions.remove(player.getUniqueId());
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof AdminGuiHolder)) {
                sessions.remove(player.getUniqueId());
            }
        });
    }

    private void clickMain(final Player player, final AdminSettingsSession session, final int slot) {
        switch (slot) {
            case MAIN_BREAK -> openScreen(player, session, AdminGuiHolder.Screen.BREAK, 0, null);
            case MAIN_ESSENCE -> openScreen(player, session, AdminGuiHolder.Screen.ESSENCE, 0, null);
            case MAIN_CUSTOM -> openScreen(player, session, AdminGuiHolder.Screen.CUSTOM, 0, null);
            case MAIN_MOBS -> openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
            case MAIN_WITHDRAW -> openScreen(player, session, AdminGuiHolder.Screen.WITHDRAWAL, 0, null);
            case MAIN_WORLDS -> openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
            case MAIN_MESSAGES -> openScreen(player, session, AdminGuiHolder.Screen.MESSAGES, 0, null);
            case MAIN_DISCARD -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_DISCARD, 0, null);
            case MAIN_SAVE -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_SAVE, 0, null);
            case MAIN_RELOAD -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_RELOAD, 0, null);
            case MAIN_CLOSE -> player.closeInventory();
            default -> { }
        }
    }

    private void clickBreak(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        switch (event.getRawSlot()) {
            case 10 -> draft.breakingEnabled(!draft.breakingEnabled());
            case 11 -> draft.requiredSilk(draft.requiredSilk() + numericDelta(event, 1, 5));
            case 12 -> draft.allowBypass(!draft.allowBypass());
            case 13 -> draft.defaultMode(cycleMode(draft.defaultMode(), event.isRightClick() ? -1 : 1));
            case 15 -> draft.creativeRecover(!draft.creativeRecover());
            case 16 -> draft.creativeEssence(!draft.creativeEssence());
            case 17 -> draft.creativeCustom(!draft.creativeCustom());
            case 22 -> { openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null); return; }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.BREAK, 0, null);
    }

    private void clickEssence(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        switch (event.getRawSlot()) {
            case 10 -> draft.essenceEnabled(!draft.essenceEnabled());
            case 11 -> draft.essenceAmount(draft.essenceAmount() + numericDelta(event, 1, 16));
            case 12 -> draft.essenceChance(draft.essenceChance() + decimalDelta(event));
            case 13 -> draft.essenceDelivery(toggleDelivery(draft.essenceDelivery()));
            case 16 -> {
                final RewardItemFactory.ItemDefinition captured = itemFactory.sanitize(player.getInventory().getItemInMainHand());
                if (captured == null) {
                    messages.send(player, "admin-held-item-required");
                    return;
                }
                draft.essenceItem(captured);
            }
            case 17 -> draft.essenceItem(defaultEssenceItem());
            case 22 -> { openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null); return; }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.ESSENCE, 0, null);
    }

    private void clickCustom(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        switch (event.getRawSlot()) {
            case 10 -> draft.customEnabled(!draft.customEnabled());
            case 11 -> draft.customAmount(draft.customAmount() + numericDelta(event, 1, 16));
            case 12 -> draft.customChance(draft.customChance() + decimalDelta(event));
            case 13 -> draft.customDelivery(toggleDelivery(draft.customDelivery()));
            case 16 -> {
                final RewardItemFactory.ItemDefinition captured = itemFactory.sanitize(player.getInventory().getItemInMainHand());
                if (captured == null) {
                    messages.send(player, "admin-held-item-required");
                    return;
                }
                draft.customItem(captured);
            }
            case 17 -> draft.customItem(defaultCustomItem());
            case 22 -> { openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null); return; }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.CUSTOM, 0, null);
    }

    private void clickMobs(
        final Player player,
        final AdminSettingsSession session,
        final AdminGuiHolder holder,
        final InventoryClickEvent event
    ) {
        final int slot = event.getRawSlot();
        final List<EntityType> types = mobTypes(session);
        if (slot >= 0 && slot < 45) {
            final int index = holder.page() * 45 + slot;
            if (index >= types.size()) return;
            final EntityType type = types.get(index);
            if ((event.isRightClick() || event.isShiftClick()) && session.draft().mobOverrides().containsKey(type)) {
                session.pendingMobReset(type);
                openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_MOB_RESET, 0, type);
            } else {
                openScreen(player, session, AdminGuiHolder.Screen.MOB_EDIT, 0, type);
            }
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, holder.page() - 1, null);
        } else if (slot == 47) {
            session.showAllMobs(!session.showAllMobs());
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
        } else if (slot == 49) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (slot == 53 && (holder.page() + 1) * 45 < types.size()) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, holder.page() + 1, null);
        }
    }

    private void clickMobEdit(
        final Player player,
        final AdminSettingsSession session,
        final EntityType type,
        final InventoryClickEvent event
    ) {
        if (type == null) return;
        if (event.getRawSlot() == 22) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
            return;
        }
        if (event.getRawSlot() == 24) {
            if (session.draft().mobOverrides().containsKey(type)) {
                session.pendingMobReset(type);
                openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_MOB_RESET, 0, type);
            }
            return;
        }

        final AdminSettingsDraft.MobOverride current = session.draft().ensureMobOverride(type);
        AdminSettingsDraft.MobOverride updated = current;
        switch (event.getRawSlot()) {
            case 10 -> updated = new AdminSettingsDraft.MobOverride(
                cycleMode(current.mode(), event.isRightClick() ? -1 : 1), current.essenceAmount(), current.essenceChance(),
                current.customAmount(), current.customChance());
            case 11 -> updated = new AdminSettingsDraft.MobOverride(current.mode(),
                clamp(current.essenceAmount() + numericDelta(event, 1, 16), 1, 4096), current.essenceChance(),
                current.customAmount(), current.customChance());
            case 12 -> updated = new AdminSettingsDraft.MobOverride(current.mode(), current.essenceAmount(),
                clampChance(current.essenceChance() + decimalDelta(event)), current.customAmount(), current.customChance());
            case 14 -> updated = new AdminSettingsDraft.MobOverride(current.mode(), current.essenceAmount(), current.essenceChance(),
                clamp(current.customAmount() + numericDelta(event, 1, 16), 1, 4096), current.customChance());
            case 15 -> updated = new AdminSettingsDraft.MobOverride(current.mode(), current.essenceAmount(), current.essenceChance(),
                current.customAmount(), clampChance(current.customChance() + decimalDelta(event)));
            default -> { return; }
        }
        session.draft().setMobOverride(type, updated);
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.MOB_EDIT, 0, type);
    }

    private void clickWithdrawal(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        if (event.getRawSlot() == 9) draft.withdrawalEnabled(!draft.withdrawalEnabled());
        else if (event.getRawSlot() == 10) draft.openOnRightClick(!draft.openOnRightClick());
        else if (event.getRawSlot() >= 11 && event.getRawSlot() <= 15) {
            final int index = event.getRawSlot() - 11;
            if (index >= draft.withdrawPresets().size()) return;
            draft.withdrawPresets().set(index, clamp(
                draft.withdrawPresets().get(index) + numericDelta(event, 1, 8), 1, 4096));
        } else if (event.getRawSlot() == 22) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            return;
        } else return;
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.WITHDRAWAL, 0, null);
    }

    private void clickWorlds(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        if (event.getRawSlot() == 49) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            return;
        }
        if (event.getRawSlot() == 4) {
            if (draft.enabledWorlds().isEmpty()) {
                for (final World world : Bukkit.getWorlds()) draft.enabledWorlds().add(world.getName());
            } else {
                draft.enabledWorlds().clear();
            }
            session.markDirty();
            openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 45 || draft.enabledWorlds().isEmpty()) return;
        final List<String> worlds = worldRows(draft);
        if (event.getRawSlot() >= worlds.size()) return;
        toggleWorld(draft, worlds.get(event.getRawSlot()));
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
    }

    private void clickMessages(final Player player, final AdminSettingsSession session, final InventoryClickEvent event) {
        final AdminSettingsDraft draft = session.draft();
        switch (event.getRawSlot()) {
            case 10 -> draft.silkRecoveredMessage(!draft.silkRecoveredMessage());
            case 11 -> draft.essenceAwardedMessage(!draft.essenceAwardedMessage());
            case 12 -> draft.customAwardedMessage(!draft.customAwardedMessage());
            case 13 -> draft.withdrawSuccessMessage(!draft.withdrawSuccessMessage());
            case 14 -> draft.withdrawFailedMessage(!draft.withdrawFailedMessage());
            case 22 -> { openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null); return; }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.MESSAGES, 0, null);
    }

    private void clickConfirmSave(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            return;
        }
        if (slot != 15) return;
        final AdminConfigPersistence.SaveResult result = persistence.save(session);
        switch (result.status()) {
            case SAVED -> {
                messages.send(player, "admin-save-success");
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            }
            case STALE -> openScreen(player, session, AdminGuiHolder.Screen.STALE, 0, null);
            case INVALID -> {
                messages.send(player, "admin-save-invalid");
                openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_SAVE, 0, null);
            }
            case FAILED -> {
                messages.send(player, "admin-save-failed");
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            }
        }
    }

    private void clickConfirmDiscard(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (slot == 15) {
            session.replace(AdminSettingsDraft.from(plugin.getConfig()), revisions.current());
            messages.send(player, "admin-discarded");
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        }
    }

    private void clickConfirmReload(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (slot == 15) {
            plugin.reloadPlugin();
            session.replace(AdminSettingsDraft.from(plugin.getConfig()), revisions.current());
            messages.send(player, "reloaded");
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        }
    }

    private void clickConfirmMobReset(
        final Player player,
        final AdminSettingsSession session,
        final EntityType type,
        final int slot
    ) {
        if (type == null) return;
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MOB_EDIT, 0, type);
        } else if (slot == 15) {
            session.draft().resetMobOverride(type);
            session.pendingMobReset(null);
            session.markDirty();
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
        }
    }

    private void clickStale(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            session.replace(AdminSettingsDraft.from(plugin.getConfig()), revisions.current());
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (slot == 15) {
            player.closeInventory();
        }
    }

    private void openScreen(
        final Player player,
        final AdminSettingsSession session,
        final AdminGuiHolder.Screen screen,
        final int page,
        final EntityType entityType
    ) {
        final int size = screen == AdminGuiHolder.Screen.MOBS || screen == AdminGuiHolder.Screen.WORLDS ? 54 : 27;
        final AdminGuiHolder holder = new AdminGuiHolder(player.getUniqueId(), screen, Math.max(0, page), entityType);
        final Component title = miniMessage.deserialize(title(session, screen, entityType));
        final Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bind(inventory);
        render(session, holder, inventory);
        player.openInventory(inventory);
    }

    private void render(final AdminSettingsSession session, final AdminGuiHolder holder, final Inventory inventory) {
        inventory.clear();
        switch (holder.screen()) {
            case MAIN -> renderMain(session, inventory);
            case BREAK -> renderBreak(session, inventory);
            case ESSENCE -> renderEssence(session, inventory);
            case CUSTOM -> renderCustom(session, inventory);
            case MOBS -> renderMobs(session, holder.page(), inventory);
            case MOB_EDIT -> renderMobEdit(session, holder.entityType(), inventory);
            case WITHDRAWAL -> renderWithdrawal(session, inventory);
            case WORLDS -> renderWorlds(session, inventory);
            case MESSAGES -> renderMessages(session, inventory);
            case CONFIRM_SAVE -> renderConfirmSave(session, inventory);
            case CONFIRM_DISCARD -> renderConfirm(inventory, "Discard unsaved draft?", session.dirty() ? "Unsaved changes will be lost." : "The draft is already clean.");
            case CONFIRM_RELOAD -> renderConfirm(inventory, "Reload from disk?", session.dirty() ? "Unsaved changes will be lost." : "Live config will be reloaded and revision bumped.");
            case CONFIRM_MOB_RESET -> renderConfirm(inventory, "Reset mob override?", holder.entityType() == null ? "Unknown mob" : pretty(holder.entityType().name()) + " will inherit defaults.");
            case STALE -> renderStale(inventory);
        }
    }

    private void renderMain(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        inventory.setItem(MAIN_STATUS, item(Material.COMPARATOR, "<#92E1FF><b>Status / Integration</b></#92E1FF>", List.of(
            "<gray>WildStacker:</gray> <white>" + plugin.wildStacker().version() + "</white>",
            "<gray>Stack authority:</gray> <white>WildStacker</white>",
            "<gray>Config schema:</gray> <white>" + settings.configSchema() + "</white>",
            "<gray>Config revision:</gray> <white>" + revisions.current() + "</white>",
            "<gray>Draft:</gray> <white>" + (session.dirty() ? "UNSAVED" : "clean") + "</white>",
            "<gray>Validation:</gray> <white>" + (validation.valid() ? "valid" : validation.errors().size() + " error(s)") + "</white>"), false));
        inventory.setItem(MAIN_BREAK, nav(Material.DIAMOND_PICKAXE, "Break Policy", "Silk, Creative and non-Silk mode"));
        inventory.setItem(MAIN_ESSENCE, nav(Material.AMETHYST_SHARD, "Essence", "Chance, amount, delivery and item"));
        inventory.setItem(MAIN_CUSTOM, nav(Material.PRISMARINE_CRYSTALS, "Custom Drop", "Custom non-Silk reward item"));
        inventory.setItem(MAIN_MOBS, nav(Material.ZOMBIE_SPAWN_EGG, "Mob Overrides", "Per-mob reward policy"));
        inventory.setItem(MAIN_WITHDRAW, nav(Material.CHEST, "Withdrawal", "Player withdrawal GUI settings"));
        inventory.setItem(MAIN_WORLDS, nav(Material.GRASS_BLOCK, "Worlds", "All worlds or allowlist"));
        inventory.setItem(MAIN_MESSAGES, nav(Material.WRITABLE_BOOK, "Messages", "Player feedback toggles"));
        inventory.setItem(MAIN_DISCARD, item(Material.RED_DYE, "<#FF6B6B><b>Discard</b></#FF6B6B>", List.of("<gray>Restore this session from live settings.</gray>"), false));
        inventory.setItem(MAIN_SAVE, item(validation.valid() ? Material.LIME_DYE : Material.BARRIER,
            validation.valid() ? "<#72F1B8><b>Save</b></#72F1B8>" : "<#FF6B6B><b>Cannot Save</b></#FF6B6B>",
            List.of("<gray>Validate, backup, atomically write and reload.</gray>"), validation.valid()));
        inventory.setItem(MAIN_RELOAD, item(Material.CLOCK, "<#FFD166><b>Reload Disk Config</b></#FFD166>", List.of("<gray>Requires confirmation.</gray>"), false));
        inventory.setItem(MAIN_CLOSE, item(Material.BARRIER, "<#FF6B6B><b>Close</b></#FF6B6B>", List.of(), false));
    }

    private void renderBreak(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        inventory.setItem(10, toggle("Breaking Policy", d.breakingEnabled()));
        inventory.setItem(11, numberItem(Material.ENCHANTED_BOOK, "Required Silk Touch", d.requiredSilk(), "Left/right: ±1", "Shift: ±5"));
        inventory.setItem(12, toggle("Silk Bypass Permission", d.allowBypass()));
        inventory.setItem(13, item(Material.HOPPER, "<#92E1FF><b>Non-Silk Mode</b></#92E1FF>", List.of(
            "<gray>Draft:</gray> <white>" + d.defaultMode() + "</white>", "<gray>Left/right click to cycle.</gray>"), true));
        inventory.setItem(15, toggle("Creative: Recover Spawner", d.creativeRecover()));
        inventory.setItem(16, toggle("Creative: Award Essence", d.creativeEssence()));
        inventory.setItem(17, toggle("Creative: Award Custom Item", d.creativeCustom()));
        inventory.setItem(22, back());
    }

    private void renderEssence(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        inventory.setItem(10, toggle("Essence Enabled", d.essenceEnabled()));
        inventory.setItem(11, numberItem(Material.PAPER, "Default Amount", d.essenceAmount(), "Left/right: ±1", "Shift: ±16"));
        inventory.setItem(12, decimalItem("Default Chance", d.essenceChance()));
        inventory.setItem(13, deliveryItem(d.essenceDelivery()));
        inventory.setItem(14, preview(d.essenceItem(), "Essence Item Preview"));
        inventory.setItem(16, item(Material.PLAYER_HEAD, "<#72F1B8><b>Use Held Item as Template</b></#72F1B8>",
            List.of("<gray>Copies only supported visual fields.</gray>", "<gray>Foreign PDC is not retained.</gray>"), false));
        inventory.setItem(17, item(Material.BARRIER, "<#FFD166><b>Reset Item Appearance</b></#FFD166>", List.of(), false));
        inventory.setItem(22, back());
    }

    private void renderCustom(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        inventory.setItem(10, toggle("Custom Drop Enabled", d.customEnabled()));
        inventory.setItem(11, numberItem(Material.PAPER, "Default Amount", d.customAmount(), "Left/right: ±1", "Shift: ±16"));
        inventory.setItem(12, decimalItem("Default Chance", d.customChance()));
        inventory.setItem(13, deliveryItem(d.customDelivery()));
        inventory.setItem(14, preview(d.customItem(), "Custom Drop Preview"));
        inventory.setItem(16, item(Material.PLAYER_HEAD, "<#72F1B8><b>Use Held Item as Template</b></#72F1B8>",
            List.of("<gray>Copies material, name, lore, glint and model data.</gray>", "<gray>Foreign PDC is discarded.</gray>"), false));
        inventory.setItem(17, item(Material.BARRIER, "<#FFD166><b>Reset Custom Item</b></#FFD166>", List.of(), false));
        inventory.setItem(22, back());
    }

    private void renderMobs(final AdminSettingsSession session, final int page, final Inventory inventory) {
        final List<EntityType> types = mobTypes(session);
        final int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < types.size(); slot++) {
            final EntityType type = types.get(start + slot);
            final AdminSettingsDraft.MobOverride override = session.draft().mobOverrides().get(type);
            final Material icon = spawnEgg(type);
            inventory.setItem(slot, item(icon, "<#92E1FF><b>" + pretty(type.name()) + "</b></#92E1FF>", List.of(
                "<gray>State:</gray> <white>" + (override == null ? "DEFAULT" : "OVERRIDDEN") + "</white>",
                "<gray>Mode:</gray> <white>" + (override == null ? session.draft().defaultMode() : override.mode()) + "</white>",
                "<gray>Left click:</gray> <white>Edit</white>",
                "<gray>Right/shift:</gray> <white>Reset override</white>"), override != null));
        }
        if (types.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "<#FFD166><b>No configured overrides</b></#FFD166>",
                List.of("<gray>Toggle Show All to choose a spawnable mob.</gray>"), false));
        }
        if (page > 0) inventory.setItem(45, nav(Material.ARROW, "Previous", "Previous page"));
        inventory.setItem(47, item(Material.ENDER_EYE, "<#92E1FF><b>" + (session.showAllMobs() ? "Showing All Mobs" : "Configured Only") + "</b></#92E1FF>",
            List.of("<gray>Click to toggle the selector scope.</gray>"), session.showAllMobs()));
        inventory.setItem(49, back());
        if ((page + 1) * 45 < types.size()) inventory.setItem(53, nav(Material.ARROW, "Next", "Next page"));
    }

    private void renderMobEdit(final AdminSettingsSession session, final EntityType type, final Inventory inventory) {
        if (type == null) return;
        final AdminSettingsDraft d = session.draft();
        final boolean overridden = d.mobOverrides().containsKey(type);
        final AdminSettingsDraft.MobOverride value = overridden ? d.mobOverrides().get(type)
            : new AdminSettingsDraft.MobOverride(d.defaultMode(), d.essenceAmount(), d.essenceChance(), d.customAmount(), d.customChance());
        inventory.setItem(4, item(spawnEgg(type), "<#92E1FF><b>" + pretty(type.name()) + "</b></#92E1FF>",
            List.of("<gray>State:</gray> <white>" + (overridden ? "OVERRIDDEN" : "INHERITED") + "</white>"), overridden));
        inventory.setItem(10, item(Material.HOPPER, "<#92E1FF><b>Reward Mode</b></#92E1FF>", List.of(
            "<gray>Effective:</gray> <white>" + value.mode() + "</white>", "<gray>Click to override/cycle.</gray>"), overridden));
        inventory.setItem(11, numberItem(Material.AMETHYST_SHARD, "Essence Amount", value.essenceAmount(), "Left/right: ±1", "Shift: ±16"));
        inventory.setItem(12, decimalItem("Essence Chance", value.essenceChance()));
        inventory.setItem(14, numberItem(Material.PRISMARINE_CRYSTALS, "Custom Amount", value.customAmount(), "Left/right: ±1", "Shift: ±16"));
        inventory.setItem(15, decimalItem("Custom Chance", value.customChance()));
        inventory.setItem(22, back());
        if (overridden) inventory.setItem(24, item(Material.RED_DYE, "<#FF6B6B><b>Reset Override</b></#FF6B6B>",
            List.of("<gray>Returns this mob to inherited defaults.</gray>", "<gray>Confirmation required.</gray>"), false));
    }

    private void renderWithdrawal(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        inventory.setItem(9, toggle("Withdrawal GUI", d.withdrawalEnabled()));
        inventory.setItem(10, toggle("Open on Right Click", d.openOnRightClick()));
        for (int index = 0; index < Math.min(5, d.withdrawPresets().size()); index++) {
            inventory.setItem(11 + index, numberItem(Material.CHEST, "Preset " + (index + 1), d.withdrawPresets().get(index),
                "Left/right: ±1", "Shift: ±8"));
        }
        inventory.setItem(18, item(Material.SPAWNER, "<#92E1FF><b>WildStacker Authority</b></#92E1FF>", List.of(
            "<gray>Quantity is resolved live on every player click.</gray>",
            "<gray>Final logical unit is always retained.</gray>",
            "<gray>Spawner item comes from WildStacker.</gray>"), false));
        inventory.setItem(22, back());
    }

    private void renderWorlds(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        final boolean all = d.enabledWorlds().isEmpty();
        inventory.setItem(4, item(all ? Material.LIME_DYE : Material.YELLOW_DYE,
            "<#92E1FF><b>Scope: " + (all ? "ALL WORLDS" : "ALLOWLIST") + "</b></#92E1FF>",
            List.of("<gray>Click to switch scope.</gray>", "<gray>Offline configured names are retained until removed.</gray>"), all));
        final List<String> rows = worldRows(d);
        for (int slot = 0; slot < Math.min(45, rows.size()); slot++) {
            final String world = rows.get(slot);
            final boolean loaded = Bukkit.getWorld(world) != null;
            final boolean enabled = all || containsWorld(d, world);
            inventory.setItem(slot, item(loaded ? Material.GRASS_BLOCK : Material.PAPER,
                "<#92E1FF><b>" + world + "</b></#92E1FF>", List.of(
                    "<gray>Loaded:</gray> <white>" + loaded + "</white>",
                    "<gray>Enabled:</gray> <white>" + enabled + "</white>",
                    all ? "<dark_gray>Switch to ALLOWLIST to toggle individual worlds.</dark_gray>" : "<gray>Click to toggle.</gray>"), enabled));
        }
        inventory.setItem(49, back());
    }

    private void renderMessages(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft d = session.draft();
        inventory.setItem(10, toggle("Silk Recovered Message", d.silkRecoveredMessage()));
        inventory.setItem(11, toggle("Essence Awarded Message", d.essenceAwardedMessage()));
        inventory.setItem(12, toggle("Custom Drop Awarded Message", d.customAwardedMessage()));
        inventory.setItem(13, toggle("Withdrawal Success Message", d.withdrawSuccessMessage()));
        inventory.setItem(14, toggle("Withdrawal Failure Message", d.withdrawFailedMessage()));
        inventory.setItem(22, back());
    }

    private void renderConfirmSave(final AdminSettingsSession session, final Inventory inventory) {
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        final List<String> lore = new ArrayList<>();
        lore.add("<gray>Errors:</gray> <white>" + validation.errors().size() + "</white>");
        lore.add("<gray>Warnings:</gray> <white>" + validation.warnings().size() + "</white>");
        for (final String error : validation.errors().stream().limit(4).toList()) lore.add("<#FF6B6B>• " + escape(error) + "</#FF6B6B>");
        for (final String warning : validation.warnings().stream().limit(4).toList()) lore.add("<#FFD166>• " + escape(warning) + "</#FFD166>");
        inventory.setItem(13, item(validation.valid() ? Material.WRITABLE_BOOK : Material.BARRIER,
            validation.valid() ? "<#72F1B8><b>Draft Valid</b></#72F1B8>" : "<#FF6B6B><b>Draft Invalid</b></#FF6B6B>", lore, validation.valid()));
        inventory.setItem(11, item(Material.RED_DYE, "<#FF6B6B><b>Cancel</b></#FF6B6B>", List.of(), false));
        if (validation.valid()) inventory.setItem(15, item(Material.LIME_DYE, "<#72F1B8><b>Confirm Save</b></#72F1B8>", List.of(
            "<gray>A timestamped backup will be created first.</gray>"), true));
    }

    private void renderConfirm(final Inventory inventory, final String title, final String detail) {
        inventory.setItem(13, item(Material.WRITABLE_BOOK, "<#FFD166><b>" + title + "</b></#FFD166>", List.of("<gray>" + detail + "</gray>"), false));
        inventory.setItem(11, item(Material.RED_DYE, "<#FF6B6B><b>Cancel</b></#FF6B6B>", List.of(), false));
        inventory.setItem(15, item(Material.LIME_DYE, "<#72F1B8><b>Confirm</b></#72F1B8>", List.of(), true));
    }

    private void renderStale(final Inventory inventory) {
        inventory.setItem(13, item(Material.CLOCK, "<#FFD166><b>Stale Admin Session</b></#FFD166>", List.of(
            "<gray>Another save or reload changed the live revision.</gray>",
            "<gray>This draft will not overwrite newer settings.</gray>"), false));
        inventory.setItem(11, item(Material.LIME_DYE, "<#72F1B8><b>Load Current Live Config</b></#72F1B8>", List.of(), true));
        inventory.setItem(15, item(Material.BARRIER, "<#FF6B6B><b>Close</b></#FF6B6B>", List.of(), false));
    }

    private List<EntityType> mobTypes(final AdminSettingsSession session) {
        return Arrays.stream(EntityType.values())
            .filter(EntityType::isSpawnable)
            .filter(type -> session.showAllMobs() || session.draft().mobOverrides().containsKey(type))
            .sorted(Comparator.comparing(EntityType::name))
            .toList();
    }

    private List<String> worldRows(final AdminSettingsDraft draft) {
        final LinkedHashMap<String, String> worlds = new LinkedHashMap<>();
        for (final World world : Bukkit.getWorlds()) worlds.put(world.getName().toLowerCase(Locale.ROOT), world.getName());
        for (final String world : draft.enabledWorlds()) worlds.putIfAbsent(world.toLowerCase(Locale.ROOT), world);
        return List.copyOf(worlds.values());
    }

    private static boolean containsWorld(final AdminSettingsDraft draft, final String world) {
        return draft.enabledWorlds().stream().anyMatch(value -> value.equalsIgnoreCase(world));
    }

    private static void toggleWorld(final AdminSettingsDraft draft, final String world) {
        final String existing = draft.enabledWorlds().stream().filter(value -> value.equalsIgnoreCase(world)).findFirst().orElse(null);
        if (existing == null) draft.enabledWorlds().add(world);
        else draft.enabledWorlds().remove(existing);
    }

    private ItemStack preview(final RewardItemFactory.ItemDefinition definition, final String label) {
        final ItemStack item = itemFactory.build(definition, previewKey);
        if (item.getType().isAir()) return item(Material.BARRIER, "<#FF6B6B><b>Invalid " + label + "</b></#FF6B6B>", List.of(), false);
        return item;
    }

    private ItemStack toggle(final String label, final boolean enabled) {
        return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? "<#72F1B8><b>" : "<gray><b>") + label + "</b>" + (enabled ? "</#72F1B8>" : "</gray>"),
            List.of("<gray>Draft:</gray> <white>" + (enabled ? "ENABLED" : "DISABLED") + "</white>", "<gray>Click to toggle.</gray>"), enabled);
    }

    private ItemStack numberItem(final Material material, final String label, final int value, final String normal, final String shifted) {
        return item(material, "<#92E1FF><b>" + label + "</b></#92E1FF>", List.of(
            "<gray>Draft:</gray> <white>" + value + "</white>", "<gray>" + normal + "</gray>", "<gray>" + shifted + "</gray>"), false);
    }

    private ItemStack decimalItem(final String label, final double value) {
        return item(Material.CLOCK, "<#92E1FF><b>" + label + "</b></#92E1FF>", List.of(
            "<gray>Draft:</gray> <white>" + String.format(Locale.ROOT, "%.1f%%", value) + "</white>",
            "<gray>Left/right: ±1%</gray>", "<gray>Shift: ±5%</gray>"), false);
    }

    private ItemStack deliveryItem(final PluginSettings.RewardDelivery delivery) {
        return item(delivery == PluginSettings.RewardDelivery.INVENTORY ? Material.CHEST : Material.DROPPER,
            "<#92E1FF><b>Delivery: " + delivery + "</b></#92E1FF>", List.of("<gray>Click to toggle inventory/ground.</gray>"), false);
    }

    private ItemStack nav(final Material material, final String label, final String detail) {
        return item(material, "<#92E1FF><b>" + label + "</b></#92E1FF>", List.of("<gray>" + detail + "</gray>", "<gray>Click to open.</gray>"), false);
    }

    private ItemStack back() {
        return item(Material.ARROW, "<#92E1FF><b>Back</b></#92E1FF>", List.of("<gray>Return to the previous menu.</gray>"), false);
    }

    private ItemStack item(final Material material, final String name, final List<String> lore, final boolean glow) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(miniMessage::deserialize).toList());
        meta.setEnchantmentGlintOverride(glow);
        item.setItemMeta(meta);
        return item;
    }

    private String title(final AdminSettingsSession session, final AdminGuiHolder.Screen screen, final EntityType entityType) {
        final String suffix = switch (screen) {
            case MAIN -> "";
            case BREAK -> " <gray>• Break Policy</gray>";
            case ESSENCE -> " <gray>• Essence</gray>";
            case CUSTOM -> " <gray>• Custom Drop</gray>";
            case MOBS -> " <gray>• Mob Overrides</gray>";
            case MOB_EDIT -> " <gray>• " + (entityType == null ? "Mob" : pretty(entityType.name())) + "</gray>";
            case WITHDRAWAL -> " <gray>• Withdrawal</gray>";
            case WORLDS -> " <gray>• Worlds</gray>";
            case MESSAGES -> " <gray>• Messages</gray>";
            case CONFIRM_SAVE -> " <gray>• Confirm Save</gray>";
            case CONFIRM_DISCARD -> " <gray>• Discard</gray>";
            case CONFIRM_RELOAD -> " <gray>• Reload</gray>";
            case CONFIRM_MOB_RESET -> " <gray>• Reset Override</gray>";
            case STALE -> " <gray>• Stale</gray>";
        };
        return session.draft().adminGuiTitle() + suffix;
    }

    private static int numericDelta(final InventoryClickEvent event, final int normal, final int shifted) {
        final int magnitude = event.isShiftClick() ? shifted : normal;
        return event.isRightClick() ? -magnitude : magnitude;
    }

    private static double decimalDelta(final InventoryClickEvent event) {
        final double magnitude = event.isShiftClick() ? 5.0D : 1.0D;
        return event.isRightClick() ? -magnitude : magnitude;
    }

    private static PluginSettings.RewardDelivery toggleDelivery(final PluginSettings.RewardDelivery value) {
        return value == PluginSettings.RewardDelivery.INVENTORY ? PluginSettings.RewardDelivery.GROUND : PluginSettings.RewardDelivery.INVENTORY;
    }

    private static NonSilkRewardMode cycleMode(final NonSilkRewardMode current, final int direction) {
        final NonSilkRewardMode[] values = NonSilkRewardMode.values();
        final int index = Math.floorMod(current.ordinal() + direction, values.length);
        return values[index];
    }

    private static Material spawnEgg(final EntityType type) {
        final Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return material == null ? Material.SPAWNER : material;
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampChance(final double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : 0.0D;
    }

    private static RewardItemFactory.ItemDefinition defaultEssenceItem() {
        return new RewardItemFactory.ItemDefinition(Material.AMETHYST_SHARD,
            "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
            List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true, null);
    }

    private static RewardItemFactory.ItemDefinition defaultCustomItem() {
        return new RewardItemFactory.ItemDefinition(Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true, null);
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String escape(final String raw) {
        return raw.replace("<", "\\<").replace(">", "\\>");
    }
}
