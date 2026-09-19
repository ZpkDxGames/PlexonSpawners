package com.plexon.spawners.gui.admin;

import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class AdminGuiHolder implements InventoryHolder {
    public enum Screen {
        MAIN, BREAK, ESSENCE, CUSTOM, MOBS, MOB_EDIT, WORLDS, MESSAGES,
        CONFIRM_SAVE, CONFIRM_DISCARD, CONFIRM_RELOAD, CONFIRM_MOB_RESET, STALE, SAVE_PENDING
    }

    private final UUID playerId;
    private final Screen screen;
    private final int page;
    private final EntityType entityType;
    private final long generation;
    private final long revision;
    private Inventory inventory;

    public AdminGuiHolder(
        final UUID playerId,
        final Screen screen,
        final int page,
        final EntityType entityType,
        final long generation,
        final long revision
    ) {
        this.playerId = playerId;
        this.screen = screen;
        this.page = page;
        this.entityType = entityType;
        this.generation = generation;
        this.revision = revision;
    }

    public void bind(final Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory has not been bound yet");
        return inventory;
    }
    public UUID playerId() { return playerId; }
    public Screen screen() { return screen; }
    public int page() { return page; }
    public EntityType entityType() { return entityType; }
    public long generation() { return generation; }
    public long revision() { return revision; }
}
