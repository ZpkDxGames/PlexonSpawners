package com.plexon.spawners.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class AdminGuiHolder implements InventoryHolder {
    public enum Screen {
        HOME,
        SPAWNER_RULES,
        ESSENCE,
        MOBS
    }

    private final Screen screen;
    private final int page;
    private Inventory inventory;

    public AdminGuiHolder(final Screen screen) {
        this(screen, 0);
    }

    public AdminGuiHolder(final Screen screen, final int page) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.page = Math.max(0, page);
    }

    public Screen screen() {
        return screen;
    }

    public int page() {
        return page;
    }

    public void bind(final Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Admin GUI inventory has not been bound yet");
    }
}
