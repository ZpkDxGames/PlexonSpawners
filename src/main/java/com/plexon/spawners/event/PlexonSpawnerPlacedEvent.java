package com.plexon.spawners.event;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnerPlacedEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final EntityType entityType;
    private final Location blockLocation;
    private final ItemStack managedItemSource;
    private final String eventId;
    private final String transactionId;

    public PlexonSpawnerPlacedEvent(
        Player player,
        EntityType entityType,
        Location blockLocation,
        ItemStack managedItemSource,
        String eventId,
        String transactionId
    ) {
        super(player);
        this.entityType = entityType;
        this.blockLocation = blockLocation.clone();
        this.managedItemSource = managedItemSource.clone();
        this.eventId = requireId(eventId, "eventId");
        this.transactionId = requireId(transactionId, "transactionId");
    }

    public EntityType getEntityType() { return entityType; }
    public Location getBlockLocation() { return blockLocation.clone(); }
    public ItemStack getManagedItemSource() { return managedItemSource.clone(); }
    public String getEventId() { return eventId; }
    public String getTransactionId() { return transactionId; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static String requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
