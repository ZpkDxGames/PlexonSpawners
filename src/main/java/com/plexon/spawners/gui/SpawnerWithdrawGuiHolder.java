package com.plexon.spawners.gui;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SpawnerWithdrawGuiHolder implements InventoryHolder {
    private final UUID playerId;
    private final Location spawnerLocation;

    public SpawnerWithdrawGuiHolder(final UUID playerId, final Location spawnerLocation) {
        this.playerId = playerId;
        this.spawnerLocation = spawnerLocation.clone();
    }

    public UUID playerId() {
        return playerId;
    }

    public Location spawnerLocation() {
        return spawnerLocation.clone();
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
