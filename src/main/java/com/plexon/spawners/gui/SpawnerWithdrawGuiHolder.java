package com.plexon.spawners.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SpawnerWithdrawGuiHolder implements InventoryHolder {
    private final UUID spawnerId;

    public SpawnerWithdrawGuiHolder(final UUID spawnerId) {
        this.spawnerId = spawnerId;
    }

    public UUID spawnerId() {
        return spawnerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
