package com.plexon.spawners.gui.admin;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.breaking.NonSilkRewardMode;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.essence.EssenceService;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.reward.CustomDropService;
import com.plexon.spawners.reward.RewardItemFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminGuiService implements Listener {
    private static final Set<ClickType> ALLOWED_CLICKS =
        EnumSet.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);
    private static final int STATUS = 4;
    private static final int BREAK = 10;
    private static final int ESSENCE = 11;
    private static final int CUSTOM = 12;
    private static final int MOBS = 13;
    private static final int WORLDS = 15;
    private static final int MESSAGES = 16;
    private static final int DISCARD = 20;
    private static final int SAVE = 22;
    private static final int RELOAD = 24;
    private static final int CLOSE = 26;

    private final PlexonSpawners plugin;
    private final PluginSettings settings;
    private final MessageService messages;
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
            if (existing != null
                && existing.sourceGeneration() == plugin.runtimeGeneration()
                && existing.sourceRevision() == revisions.current()) {
                return existing;
            }
            return new AdminSettingsSession(
                player.getUniqueId(),
                AdminSettingsDraft.from(plugin.liveConfigCopy()),
                revisions.current(),
                plugin.runtimeGeneration());
        });
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
        if (!ALLOWED_CLICKS.contains(event.getClick())) return;
        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        final ClickSnapshot click = new ClickSnapshot(
            holder, rawSlot, event.isRightClick(), event.isShiftClick(), player.getUniqueId());
        plugin.coreBridge().schedulePrimary(Duration.ofMillis(50), () -> routeDeferred(click));
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminGuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGuiHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.coreBridge().schedulePrimary(Duration.ofMillis(50), () -> {
            if (!player.isOnline()
                || !(player.getOpenInventory().getTopInventory().getHolder() instanceof AdminGuiHolder)) {
                final AdminSettingsSession session = sessions.get(player.getUniqueId());
                if (session != null && !session.savePending()) sessions.remove(player.getUniqueId(), session);
            }
        });
    }

    private void routeDeferred(final ClickSnapshot click) {
        if (!plugin.isEnabled()) return;
        final Player player = Bukkit.getPlayer(click.playerId());
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("plexonspawners.admin.gui")) {
            player.closeInventory();
            return;
        }
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof AdminGuiHolder holder)) return;
        if (holder != click.holder()) return;

        final AdminSettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (session.sourceGeneration() != plugin.runtimeGeneration()
            || session.sourceRevision() != revisions.current()
            || holder.generation() != session.sourceGeneration()
            || holder.revision() != session.sourceRevision()) {
            openScreen(player, session, AdminGuiHolder.Screen.STALE, 0, null);
            return;
        }

        switch (holder.screen()) {
            case MAIN -> clickMain(player, session, click.rawSlot());
            case BREAK -> clickBreak(player, session, click);
            case ESSENCE -> clickEssence(player, session, click);
            case CUSTOM -> clickCustom(player, session, click);
            case MOBS -> clickMobs(player, session, holder, click);
            case MOB_EDIT -> clickMobEdit(player, session, holder.entityType(), click);
            case WORLDS -> clickWorlds(player, session, click.rawSlot());
            case MESSAGES -> clickMessages(player, session, click.rawSlot());
            case CONFIRM_SAVE -> clickConfirmSave(player, session, click.rawSlot());
            case CONFIRM_DISCARD -> clickConfirmDiscard(player, session, click.rawSlot());
            case CONFIRM_RELOAD -> clickConfirmReload(player, session, click.rawSlot());
            case CONFIRM_MOB_RESET -> clickConfirmMobReset(player, session, holder.entityType(), click.rawSlot());
            case STALE -> clickStale(player, session, click.rawSlot());
            case SAVE_PENDING -> { }
        }
    }

    private void clickMain(final Player player, final AdminSettingsSession session, final int slot) {
        switch (slot) {
            case BREAK -> openScreen(player, session, AdminGuiHolder.Screen.BREAK, 0, null);
            case ESSENCE -> openScreen(player, session, AdminGuiHolder.Screen.ESSENCE, 0, null);
            case CUSTOM -> openScreen(player, session, AdminGuiHolder.Screen.CUSTOM, 0, null);
            case MOBS -> openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
            case WORLDS -> openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
            case MESSAGES -> openScreen(player, session, AdminGuiHolder.Screen.MESSAGES, 0, null);
            case DISCARD -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_DISCARD, 0, null);
            case SAVE -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_SAVE, 0, null);
            case RELOAD -> openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_RELOAD, 0, null);
            case CLOSE -> player.closeInventory();
            default -> { }
        }
    }

    private void clickBreak(final Player player, final AdminSettingsSession session, final ClickSnapshot click) {
        final AdminSettingsDraft d = session.draft();
        switch (click.rawSlot()) {
            case 10 -> d.breakingEnabled(!d.breakingEnabled());
            case 11 -> d.requiredSilk(d.requiredSilk() + delta(click, 1, 5));
            case 12 -> d.allowBypass(!d.allowBypass());
            case 13 -> d.defaultMode(cycleMode(d.defaultMode(), click.right() ? -1 : 1));
            case 15 -> d.creativeRecover(!d.creativeRecover());
            case 16 -> d.creativeEssence(!d.creativeEssence());
            case 17 -> d.creativeCustom(!d.creativeCustom());
            case 22 -> {
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                return;
            }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.BREAK, 0, null);
    }

    private void clickEssence(final Player player, final AdminSettingsSession session, final ClickSnapshot click) {
        final AdminSettingsDraft d = session.draft();
        switch (click.rawSlot()) {
            case 10 -> d.essenceEnabled(!d.essenceEnabled());
            case 11 -> d.essenceAmount(d.essenceAmount() + delta(click, 1, 16));
            case 12 -> d.essenceChance(d.essenceChance() + chanceDelta(click));
            case 13 -> d.essenceDelivery(toggleDelivery(d.essenceDelivery()));
            case 16 -> {
                final RewardItemFactory.ItemDefinition captured =
                    itemFactory.sanitize(player.getInventory().getItemInMainHand());
                if (captured == null) {
                    messages.send(player, "admin-held-item-required");
                    return;
                }
                d.essenceItem(captured);
            }
            case 17 -> d.essenceItem(defaultEssenceItem());
            case 22 -> {
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                return;
            }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.ESSENCE, 0, null);
    }

    private void clickCustom(final Player player, final AdminSettingsSession session, final ClickSnapshot click) {
        final AdminSettingsDraft d = session.draft();
        switch (click.rawSlot()) {
            case 10 -> d.customEnabled(!d.customEnabled());
            case 11 -> d.customAmount(d.customAmount() + delta(click, 1, 16));
            case 12 -> d.customChance(d.customChance() + chanceDelta(click));
            case 13 -> d.customDelivery(toggleDelivery(d.customDelivery()));
            case 16 -> {
                final RewardItemFactory.ItemDefinition captured =
                    itemFactory.sanitize(player.getInventory().getItemInMainHand());
                if (captured == null) {
                    messages.send(player, "admin-held-item-required");
                    return;
                }
                d.customItem(captured);
            }
            case 17 -> d.customItem(defaultCustomItem());
            case 22 -> {
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                return;
            }
            default -> { return; }
        }
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.CUSTOM, 0, null);
    }

    private void clickMobs(
        final Player player,
        final AdminSettingsSession session,
        final AdminGuiHolder holder,
        final ClickSnapshot click
    ) {
        final List<EntityType> types = mobTypes(session);
        if (click.rawSlot() >= 0 && click.rawSlot() < 45) {
            final int index = holder.page() * 45 + click.rawSlot();
            if (index >= types.size()) return;
            final EntityType type = types.get(index);
            if ((click.right() || click.shift()) && session.draft().mobOverrides().containsKey(type)) {
                session.pendingMobReset(type);
                openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_MOB_RESET, 0, type);
            } else {
                openScreen(player, session, AdminGuiHolder.Screen.MOB_EDIT, 0, type);
            }
            return;
        }
        if (click.rawSlot() == 45 && holder.page() > 0) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, holder.page() - 1, null);
        } else if (click.rawSlot() == 47) {
            session.showAllMobs(!session.showAllMobs());
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
        } else if (click.rawSlot() == 49) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (click.rawSlot() == 53 && (holder.page() + 1) * 45 < types.size()) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, holder.page() + 1, null);
        }
    }

    private void clickMobEdit(
        final Player player,
        final AdminSettingsSession session,
        final EntityType type,
        final ClickSnapshot click
    ) {
        if (type == null) return;
        if (click.rawSlot() == 22) {
            openScreen(player, session, AdminGuiHolder.Screen.MOBS, 0, null);
            return;
        }
        if (click.rawSlot() == 24) {
            if (session.draft().mobOverrides().containsKey(type)) {
                session.pendingMobReset(type);
                openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_MOB_RESET, 0, type);
            }
            return;
        }

        final AdminSettingsDraft.MobOverride current = session.draft().ensureMobOverride(type);
        AdminSettingsDraft.MobOverride updated;
        switch (click.rawSlot()) {
            case 10 -> updated = new AdminSettingsDraft.MobOverride(
                cycleMode(current.mode(), click.right() ? -1 : 1),
                current.essenceAmount(), current.essenceChance(), current.customAmount(), current.customChance());
            case 11 -> updated = new AdminSettingsDraft.MobOverride(
                current.mode(), clamp(current.essenceAmount() + delta(click, 1, 16), 1, 4096),
                current.essenceChance(), current.customAmount(), current.customChance());
            case 12 -> updated = new AdminSettingsDraft.MobOverride(
                current.mode(), current.essenceAmount(), clampChance(current.essenceChance() + chanceDelta(click)),
                current.customAmount(), current.customChance());
            case 14 -> updated = new AdminSettingsDraft.MobOverride(
                current.mode(), current.essenceAmount(), current.essenceChance(),
                clamp(current.customAmount() + delta(click, 1, 16), 1, 4096), current.customChance());
            case 15 -> updated = new AdminSettingsDraft.MobOverride(
                current.mode(), current.essenceAmount(), current.essenceChance(), current.customAmount(),
                clampChance(current.customChance() + chanceDelta(click)));
            default -> { return; }
        }
        session.draft().setMobOverride(type, updated);
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.MOB_EDIT, 0, type);
    }

    private void clickWorlds(final Player player, final AdminSettingsSession session, final int slot) {
        final AdminSettingsDraft d = session.draft();
        if (slot == 49) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            return;
        }
        if (slot == 53) {
            d.scopeMode(d.scopeMode() == PluginSettings.ScopeMode.ALL
                ? PluginSettings.ScopeMode.ALLOWLIST
                : PluginSettings.ScopeMode.ALL);
            if (d.scopeMode() == PluginSettings.ScopeMode.ALLOWLIST && d.enabledWorlds().isEmpty()) {
                for (final World world : Bukkit.getWorlds()) d.enabledWorlds().add(world.getName());
            }
            session.markDirty();
            openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
            return;
        }
        if (slot < 0 || slot >= 45 || d.scopeMode() != PluginSettings.ScopeMode.ALLOWLIST) return;
        final List<String> worlds = worldRows(d);
        if (slot >= worlds.size()) return;
        toggleWorld(d, worlds.get(slot));
        session.markDirty();
        openScreen(player, session, AdminGuiHolder.Screen.WORLDS, 0, null);
    }

    private void clickMessages(final Player player, final AdminSettingsSession session, final int slot) {
        final AdminSettingsDraft d = session.draft();
        switch (slot) {
            case 10 -> d.silkRecoveredMessage(!d.silkRecoveredMessage());
            case 11 -> d.essenceAwardedMessage(!d.essenceAwardedMessage());
            case 12 -> d.customAwardedMessage(!d.customAwardedMessage());
            case 22 -> {
                openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                return;
            }
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
        if (slot != 15 || session.savePending()) return;
        openScreen(player, session, AdminGuiHolder.Screen.SAVE_PENDING, 0, null);
        persistence.saveAsync(session, result -> {
            if (!player.isOnline()) return;
            switch (result.status()) {
                case SAVED -> {
                    messages.send(player, "admin-save-success");
                    session.replace(AdminSettingsDraft.from(plugin.liveConfigCopy()),
                        revisions.current(), plugin.runtimeGeneration());
                    openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                }
                case STALE -> openScreen(player, session, AdminGuiHolder.Screen.STALE, 0, null);
                case INVALID -> {
                    messages.send(player, "admin-save-invalid");
                    openScreen(player, session, AdminGuiHolder.Screen.CONFIRM_SAVE, 0, null);
                }
                case BUSY -> {
                    messages.send(player, "admin-save-busy");
                    openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                }
                case FAILED -> {
                    messages.send(player, "admin-save-failed");
                    openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
                }
            }
        });
    }

    private void clickConfirmDiscard(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        } else if (slot == 15) {
            session.replace(AdminSettingsDraft.from(plugin.liveConfigCopy()),
                revisions.current(), plugin.runtimeGeneration());
            messages.send(player, "admin-discarded");
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        }
    }

    private void clickConfirmReload(final Player player, final AdminSettingsSession session, final int slot) {
        if (slot == 11) {
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
            return;
        }
        if (slot != 15) return;
        openScreen(player, session, AdminGuiHolder.Screen.SAVE_PENDING, 0, null);
        messages.send(player, "reload-started");
        plugin.reloadPluginAsync().whenComplete((revision, error) -> plugin.coreBridge().runPrimary(() -> {
            if (!player.isOnline()) return;
            if (error != null) {
                messages.send(player, "reload-failed", Map.of("error", safe(rootMessage(error))));
                session.replace(AdminSettingsDraft.from(plugin.liveConfigCopy()),
                    revisions.current(), plugin.runtimeGeneration());
            } else {
                session.replace(AdminSettingsDraft.from(plugin.liveConfigCopy()),
                    revisions.current(), plugin.runtimeGeneration());
                messages.send(player, "reloaded", Map.of("revision", Long.toString(revision)));
            }
            openScreen(player, session, AdminGuiHolder.Screen.MAIN, 0, null);
        }));
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
            session.replace(AdminSettingsDraft.from(plugin.liveConfigCopy()),
                revisions.current(), plugin.runtimeGeneration());
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
        final AdminGuiHolder holder = new AdminGuiHolder(
            player.getUniqueId(), screen, Math.max(0, page), entityType,
            session.sourceGeneration(), session.sourceRevision());
        final Inventory inventory = Bukkit.createInventory(
            holder, size, miniMessage.deserialize(title(session, screen, entityType)));
        holder.bind(inventory);
        render(session, holder, inventory);
        player.openInventory(inventory);
    }

    private void render(final AdminSettingsSession session, final AdminGuiHolder holder, final Inventory inventory) {
        switch (holder.screen()) {
            case MAIN -> renderMain(session, inventory);
            case BREAK -> renderBreak(session, inventory);
            case ESSENCE -> renderEssence(session, inventory);
            case CUSTOM -> renderCustom(session, inventory);
            case MOBS -> renderMobs(session, holder.page(), inventory);
            case MOB_EDIT -> renderMobEdit(session, holder.entityType(), inventory);
            case WORLDS -> renderWorlds(session, inventory);
            case MESSAGES -> renderMessages(session, inventory);
            case CONFIRM_SAVE -> renderConfirmSave(session, inventory);
            case CONFIRM_DISCARD -> renderConfirm(inventory, "Discard unsaved draft?",
                "Unsaved changes will be lost.");
            case CONFIRM_RELOAD -> renderConfirm(inventory, "Reload from disk?",
                "Unsaved changes will be lost; invalid disk config is rejected atomically.");
            case CONFIRM_MOB_RESET -> renderConfirm(inventory, "Reset mob override?",
                holder.entityType() == null ? "Unknown mob" : pretty(holder.entityType().name()) + " will inherit defaults.");
            case STALE -> renderStale(inventory);
            case SAVE_PENDING -> renderPending(inventory);
        }
    }

    private void renderMain(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        inv.setItem(STATUS, item(Material.COMPARATOR, "<#92E1FF><b>Status / Integration</b></#92E1FF>", List.of(
            "<gray>WildStacker:</gray> <white>" + plugin.wildStacker().version() + "</white>",
            "<gray>Stack authority:</gray> <white>WildStacker</white>",
            "<gray>Core:</gray> <white>" + plugin.coreBridge().mode() + " / " + plugin.coreBridge().registrationState() + "</white>",
            "<gray>Config:</gray> <white>schema " + settings.configSchema() + " / gen " + plugin.runtimeGeneration() + "</white>",
            "<gray>Draft:</gray> <white>" + (session.dirty() ? "UNSAVED" : "clean") + "</white>",
            "<gray>Validation:</gray> <white>" + (validation.valid() ? "valid" : validation.errors().size() + " error(s)") + "</white>"
        ), false));
        inv.setItem(BREAK, nav(Material.DIAMOND_PICKAXE, "Break Policy", "Silk, Creative and non-Silk mode"));
        inv.setItem(ESSENCE, nav(Material.AMETHYST_SHARD, "Essence", "Chance, amount, delivery and exact item"));
        inv.setItem(CUSTOM, nav(Material.PRISMARINE_CRYSTALS, "Custom Drop", "Custom non-Silk exact reward item"));
        inv.setItem(MOBS, nav(Material.ZOMBIE_SPAWN_EGG, "Mob Overrides", "Per-mob reward policy"));
        inv.setItem(WORLDS, nav(Material.GRASS_BLOCK, "World Scope", "Explicit ALL or allowlist"));
        inv.setItem(MESSAGES, nav(Material.WRITABLE_BOOK, "Messages", "Player feedback toggles"));
        inv.setItem(DISCARD, item(Material.RED_DYE, "<#FF6B6B><b>Discard</b></#FF6B6B>",
            List.of("<gray>Restore this session from live settings.</gray>"), false));
        inv.setItem(SAVE, item(validation.valid() ? Material.LIME_DYE : Material.BARRIER,
            validation.valid() ? "<#72F1B8><b>Save</b></#72F1B8>" : "<#FF6B6B><b>Cannot Save</b></#FF6B6B>",
            List.of("<gray>Validate, backup/write on I/O, atomically commit runtime.</gray>"), validation.valid()));
        inv.setItem(RELOAD, item(Material.CLOCK, "<#FFD166><b>Reload Disk Config</b></#FFD166>",
            List.of("<gray>Prepare/validate/commit atomically.</gray>"), false));
        inv.setItem(CLOSE, item(Material.BARRIER, "<#FF6B6B><b>Close</b></#FF6B6B>", List.of(), false));
    }

    private void renderBreak(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft d = session.draft();
        inv.setItem(10, toggle("Breaking Policy", d.breakingEnabled()));
        inv.setItem(11, number(Material.ENCHANTED_BOOK, "Required Silk Touch", d.requiredSilk()));
        inv.setItem(12, toggle("Silk Bypass Permission", d.allowBypass()));
        inv.setItem(13, item(Material.HOPPER, "<#92E1FF><b>Non-Silk Mode</b></#92E1FF>",
            List.of("<gray>Draft:</gray> <white>" + d.defaultMode() + "</white>", "<gray>Left/right to cycle.</gray>"), true));
        inv.setItem(15, toggle("Creative: Recover Spawner", d.creativeRecover()));
        inv.setItem(16, toggle("Creative: Award Essence", d.creativeEssence()));
        inv.setItem(17, toggle("Creative: Award Custom Item", d.creativeCustom()));
        inv.setItem(22, back());
    }

    private void renderEssence(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft d = session.draft();
        inv.setItem(10, toggle("Essence Enabled", d.essenceEnabled()));
        inv.setItem(11, number(Material.PAPER, "Default Amount", d.essenceAmount()));
        inv.setItem(12, decimal("Default Chance", d.essenceChance()));
        inv.setItem(13, delivery(d.essenceDelivery()));
        inv.setItem(14, preview(d.essenceItem()));
        inv.setItem(16, item(Material.PLAYER_HEAD, "<#72F1B8><b>Capture Exact Held Item</b></#72F1B8>",
            List.of("<gray>Paper byte serialization preserves components and foreign PDC.</gray>"), false));
        inv.setItem(17, item(Material.BARRIER, "<#FFD166><b>Reset Essence Item</b></#FFD166>", List.of(), false));
        inv.setItem(22, back());
    }

    private void renderCustom(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft d = session.draft();
        inv.setItem(10, toggle("Custom Drop Enabled", d.customEnabled()));
        inv.setItem(11, number(Material.PAPER, "Default Amount", d.customAmount()));
        inv.setItem(12, decimal("Default Chance", d.customChance()));
        inv.setItem(13, delivery(d.customDelivery()));
        inv.setItem(14, preview(d.customItem()));
        inv.setItem(16, item(Material.PLAYER_HEAD, "<#72F1B8><b>Capture Exact Held Item</b></#72F1B8>",
            List.of("<gray>All supported item metadata/components remain authoritative.</gray>"), false));
        inv.setItem(17, item(Material.BARRIER, "<#FFD166><b>Reset Custom Item</b></#FFD166>", List.of(), false));
        inv.setItem(22, back());
    }

    private void renderMobs(final AdminSettingsSession session, final int page, final Inventory inv) {
        final List<EntityType> types = mobTypes(session);
        final int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < types.size(); slot++) {
            final EntityType type = types.get(start + slot);
            final AdminSettingsDraft.MobOverride override = session.draft().mobOverrides().get(type);
            inv.setItem(slot, item(spawnEgg(type), "<#92E1FF><b>" + pretty(type.name()) + "</b></#92E1FF>", List.of(
                "<gray>State:</gray> <white>" + (override == null ? "DEFAULT" : "OVERRIDDEN") + "</white>",
                "<gray>Mode:</gray> <white>" + (override == null ? session.draft().defaultMode() : override.mode()) + "</white>",
                "<gray>Left: edit. Right/shift: reset.</gray>"
            ), override != null));
        }
        if (page > 0) inv.setItem(45, nav(Material.ARROW, "Previous", "Previous page"));
        inv.setItem(47, item(Material.ENDER_EYE,
            "<#92E1FF><b>" + (session.showAllMobs() ? "Showing All Mobs" : "Configured Only") + "</b></#92E1FF>",
            List.of("<gray>Click to toggle selector scope.</gray>"), session.showAllMobs()));
        inv.setItem(49, back());
        if ((page + 1) * 45 < types.size()) inv.setItem(53, nav(Material.ARROW, "Next", "Next page"));
    }

    private void renderMobEdit(final AdminSettingsSession session, final EntityType type, final Inventory inv) {
        if (type == null) return;
        final AdminSettingsDraft d = session.draft();
        final boolean overridden = d.mobOverrides().containsKey(type);
        final AdminSettingsDraft.MobOverride v = overridden ? d.mobOverrides().get(type)
            : new AdminSettingsDraft.MobOverride(
                d.defaultMode(), d.essenceAmount(), d.essenceChance(), d.customAmount(), d.customChance());
        inv.setItem(4, item(spawnEgg(type), "<#92E1FF><b>" + pretty(type.name()) + "</b></#92E1FF>",
            List.of("<gray>" + (overridden ? "OVERRIDDEN" : "INHERITED") + "</gray>"), overridden));
        inv.setItem(10, item(Material.HOPPER, "<#92E1FF><b>Reward Mode</b></#92E1FF>",
            List.of("<gray>Effective:</gray> <white>" + v.mode() + "</white>"), overridden));
        inv.setItem(11, number(Material.AMETHYST_SHARD, "Essence Amount", v.essenceAmount()));
        inv.setItem(12, decimal("Essence Chance", v.essenceChance()));
        inv.setItem(14, number(Material.PRISMARINE_CRYSTALS, "Custom Amount", v.customAmount()));
        inv.setItem(15, decimal("Custom Chance", v.customChance()));
        inv.setItem(22, back());
        if (overridden) inv.setItem(24, item(Material.RED_DYE, "<#FF6B6B><b>Reset Override</b></#FF6B6B>",
            List.of("<gray>Confirmation required.</gray>"), false));
    }

    private void renderWorlds(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft d = session.draft();
        final List<String> rows = worldRows(d);
        for (int slot = 0; slot < Math.min(45, rows.size()); slot++) {
            final String world = rows.get(slot);
            final boolean enabled = d.scopeMode() == PluginSettings.ScopeMode.ALL || containsWorld(d, world);
            inv.setItem(slot, item(Bukkit.getWorld(world) == null ? Material.PAPER : Material.GRASS_BLOCK,
                "<#92E1FF><b>" + safe(world) + "</b></#92E1FF>",
                List.of("<gray>Enabled:</gray> <white>" + enabled + "</white>",
                    d.scopeMode() == PluginSettings.ScopeMode.ALL
                        ? "<dark_gray>Switch to ALLOWLIST to edit individual worlds.</dark_gray>"
                        : "<gray>Click to toggle.</gray>"), enabled));
        }
        inv.setItem(49, back());
        inv.setItem(53, item(d.scopeMode() == PluginSettings.ScopeMode.ALL ? Material.LIME_DYE : Material.YELLOW_DYE,
            "<#92E1FF><b>Scope: " + d.scopeMode() + "</b></#92E1FF>",
            List.of("<gray>Click to switch explicit scope mode.</gray>"), d.scopeMode() == PluginSettings.ScopeMode.ALL));
    }

    private void renderMessages(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft d = session.draft();
        inv.setItem(10, toggle("Silk Recovered Message", d.silkRecoveredMessage()));
        inv.setItem(11, toggle("Essence Awarded Message", d.essenceAwardedMessage()));
        inv.setItem(12, toggle("Custom Drop Awarded Message", d.customAwardedMessage()));
        inv.setItem(22, back());
    }

    private void renderConfirmSave(final AdminSettingsSession session, final Inventory inv) {
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        final List<String> lore = new ArrayList<>();
        lore.add("<gray>Errors:</gray> <white>" + validation.errors().size() + "</white>");
        lore.add("<gray>Warnings:</gray> <white>" + validation.warnings().size() + "</white>");
        validation.errors().stream().limit(4).forEach(error -> lore.add("<#FF6B6B>• " + safe(error) + "</#FF6B6B>"));
        validation.warnings().stream().limit(4).forEach(warning -> lore.add("<#FFD166>• " + safe(warning) + "</#FFD166>"));
        inv.setItem(13, item(validation.valid() ? Material.WRITABLE_BOOK : Material.BARRIER,
            validation.valid() ? "<#72F1B8><b>Draft Valid</b></#72F1B8>" : "<#FF6B6B><b>Draft Invalid</b></#FF6B6B>",
            lore, validation.valid()));
        inv.setItem(11, item(Material.RED_DYE, "<#FF6B6B><b>Cancel</b></#FF6B6B>", List.of(), false));
        if (validation.valid()) inv.setItem(15, item(Material.LIME_DYE,
            "<#72F1B8><b>Confirm Save</b></#72F1B8>",
            List.of("<gray>Filesystem work runs off the primary thread.</gray>"), true));
    }

    private void renderConfirm(final Inventory inv, final String title, final String detail) {
        inv.setItem(13, item(Material.WRITABLE_BOOK, "<#FFD166><b>" + safe(title) + "</b></#FFD166>",
            List.of("<gray>" + safe(detail) + "</gray>"), false));
        inv.setItem(11, item(Material.RED_DYE, "<#FF6B6B><b>Cancel</b></#FF6B6B>", List.of(), false));
        inv.setItem(15, item(Material.LIME_DYE, "<#72F1B8><b>Confirm</b></#72F1B8>", List.of(), true));
    }

    private void renderStale(final Inventory inv) {
        inv.setItem(13, item(Material.CLOCK, "<#FFD166><b>Stale Admin Session</b></#FFD166>",
            List.of("<gray>A newer runtime generation/revision exists.</gray>",
                "<gray>This draft cannot overwrite it.</gray>"), false));
        inv.setItem(11, item(Material.LIME_DYE, "<#72F1B8><b>Load Current Live Config</b></#72F1B8>",
            List.of(), true));
        inv.setItem(15, item(Material.BARRIER, "<#FF6B6B><b>Close</b></#FF6B6B>", List.of(), false));
    }

    private void renderPending(final Inventory inv) {
        inv.setItem(13, item(Material.CLOCK, "<#92E1FF><b>Configuration Transaction Pending</b></#92E1FF>",
            List.of("<gray>Backup/write/validation is running on bounded I/O.</gray>",
                "<gray>Duplicate submissions are blocked.</gray>"), true));
    }

    private List<EntityType> mobTypes(final AdminSettingsSession session) {
        return Arrays.stream(EntityType.values())
            .filter(type -> type != EntityType.UNKNOWN)
            .filter(EntityType::isSpawnable)
            .filter(type -> session.showAllMobs() || session.draft().mobOverrides().containsKey(type))
            .sorted(Comparator.comparing(EntityType::name))
            .toList();
    }

    private List<String> worldRows(final AdminSettingsDraft draft) {
        final LinkedHashMap<String, String> worlds = new LinkedHashMap<>();
        for (final World world : Bukkit.getWorlds()) {
            worlds.put(world.getName().toLowerCase(Locale.ROOT), world.getName());
        }
        for (final String world : draft.enabledWorlds()) {
            worlds.putIfAbsent(world.toLowerCase(Locale.ROOT), world);
        }
        return List.copyOf(worlds.values());
    }

    private static boolean containsWorld(final AdminSettingsDraft d, final String world) {
        return d.enabledWorlds().stream().anyMatch(value -> value.equalsIgnoreCase(world));
    }

    private static void toggleWorld(final AdminSettingsDraft d, final String world) {
        final String existing = d.enabledWorlds().stream()
            .filter(value -> value.equalsIgnoreCase(world)).findFirst().orElse(null);
        if (existing == null) d.enabledWorlds().add(world);
        else d.enabledWorlds().remove(existing);
    }

    private ItemStack preview(final RewardItemFactory.ItemDefinition definition) {
        return itemFactory.build(definition, previewKey);
    }

    private ItemStack toggle(final String label, final boolean enabled) {
        return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? "<#72F1B8><b>" : "<gray><b>") + label + "</b>"
                + (enabled ? "</#72F1B8>" : "</gray>"),
            List.of("<gray>Draft:</gray> <white>" + (enabled ? "ENABLED" : "DISABLED") + "</white>",
                "<gray>Click to toggle.</gray>"), enabled);
    }

    private ItemStack number(final Material material, final String label, final int value) {
        return item(material, "<#92E1FF><b>" + label + "</b></#92E1FF>",
            List.of("<gray>Draft:</gray> <white>" + value + "</white>",
                "<gray>Left/right: ±1; shift: ±larger step.</gray>"), false);
    }

    private ItemStack decimal(final String label, final double value) {
        return item(Material.CLOCK, "<#92E1FF><b>" + label + "</b></#92E1FF>",
            List.of("<gray>Draft:</gray> <white>" + String.format(Locale.ROOT, "%.1f%%", value) + "</white>",
                "<gray>Left/right: ±1%; shift: ±5%.</gray>"), false);
    }

    private ItemStack delivery(final PluginSettings.RewardDelivery delivery) {
        return item(delivery == PluginSettings.RewardDelivery.INVENTORY ? Material.CHEST : Material.DROPPER,
            "<#92E1FF><b>Delivery: " + delivery + "</b></#92E1FF>",
            List.of("<gray>Click to toggle inventory/ground.</gray>"), false);
    }

    private ItemStack nav(final Material material, final String label, final String detail) {
        return item(material, "<#92E1FF><b>" + label + "</b></#92E1FF>",
            List.of("<gray>" + detail + "</gray>", "<gray>Click to open.</gray>"), false);
    }

    private ItemStack back() {
        return item(Material.ARROW, "<#92E1FF><b>Back</b></#92E1FF>", List.of(), false);
    }

    private ItemStack item(final Material material, final String name, final List<String> lore, final boolean glow) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(miniMessage.deserialize(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(miniMessage::deserialize).toList());
        meta.setEnchantmentGlintOverride(glow);
        stack.setItemMeta(meta);
        return stack;
    }

    private String title(
        final AdminSettingsSession session,
        final AdminGuiHolder.Screen screen,
        final EntityType entityType
    ) {
        final String suffix = switch (screen) {
            case MAIN -> "";
            case BREAK -> " <gray>• Break Policy</gray>";
            case ESSENCE -> " <gray>• Essence</gray>";
            case CUSTOM -> " <gray>• Custom Drop</gray>";
            case MOBS -> " <gray>• Mob Overrides</gray>";
            case MOB_EDIT -> " <gray>• " + (entityType == null ? "Mob" : pretty(entityType.name())) + "</gray>";
            case WORLDS -> " <gray>• World Scope</gray>";
            case MESSAGES -> " <gray>• Messages</gray>";
            case CONFIRM_SAVE -> " <gray>• Confirm Save</gray>";
            case CONFIRM_DISCARD -> " <gray>• Discard</gray>";
            case CONFIRM_RELOAD -> " <gray>• Reload</gray>";
            case CONFIRM_MOB_RESET -> " <gray>• Reset Override</gray>";
            case STALE -> " <gray>• Stale</gray>";
            case SAVE_PENDING -> " <gray>• Pending</gray>";
        };
        return session.draft().adminGuiTitle() + suffix;
    }

    private static int delta(final ClickSnapshot click, final int normal, final int shifted) {
        final int magnitude = click.shift() ? shifted : normal;
        return click.right() ? -magnitude : magnitude;
    }

    private static double chanceDelta(final ClickSnapshot click) {
        final double magnitude = click.shift() ? 5.0D : 1.0D;
        return click.right() ? -magnitude : magnitude;
    }

    private static PluginSettings.RewardDelivery toggleDelivery(final PluginSettings.RewardDelivery value) {
        return value == PluginSettings.RewardDelivery.INVENTORY
            ? PluginSettings.RewardDelivery.GROUND : PluginSettings.RewardDelivery.INVENTORY;
    }

    private static NonSilkRewardMode cycleMode(final NonSilkRewardMode current, final int direction) {
        final NonSilkRewardMode[] values = NonSilkRewardMode.values();
        return values[Math.floorMod(current.ordinal() + direction, values.length)];
    }

    private static Material spawnEgg(final EntityType type) {
        final Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return material == null ? Material.SPAWNER : material;
    }

    private RewardItemFactory.ItemDefinition defaultEssenceItem() {
        return itemFactory.read((org.bukkit.configuration.ConfigurationSection) null,
            Material.AMETHYST_SHARD,
            "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
            List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true);
    }

    private RewardItemFactory.ItemDefinition defaultCustomItem() {
        return itemFactory.read((org.bukkit.configuration.ConfigurationSection) null,
            Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true);
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampChance(final double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : 0.0D;
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw
            : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String safe(final String raw) {
        return raw == null ? "" : raw.replace("<", "‹").replace(">", "›");
    }

    private static String rootMessage(final Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ClickSnapshot(
        AdminGuiHolder holder,
        int rawSlot,
        boolean right,
        boolean shift,
        UUID playerId
    ) {}
}
