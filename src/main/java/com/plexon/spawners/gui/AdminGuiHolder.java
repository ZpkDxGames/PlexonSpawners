package com.plexon.spawners.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class AdminGuiHolder implements InventoryHolder {
    private Inventory inventory;

    public void bind(final Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Admin GUI inventory has not been bound yet");
    }
}
