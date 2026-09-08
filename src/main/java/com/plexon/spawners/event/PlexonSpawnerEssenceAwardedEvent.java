package com.plexon.spawners.event;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnerEssenceAwardedEvent extends PlayerEvent {
    public enum DeliveryMode {
        GROUND,
        INVENTORY
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final EntityType entityType;
    private final int amount;
    private final DeliveryMode deliveryMode;
    private final Location sourceLocation;
    private final String eventId;
    private final String transactionId;

    public PlexonSpawnerEssenceAwardedEvent(
        Player player,
        EntityType entityType,
        int amount,
        DeliveryMode deliveryMode,
        Location sourceLocation,
        String eventId,
        String transactionId
    ) {
        super(player);
        this.entityType = entityType;
        this.amount = amount;
        this.deliveryMode = deliveryMode;
        this.sourceLocation = sourceLocation.clone();
        this.eventId = requireId(eventId, "eventId");
        this.transactionId = requireId(transactionId, "transactionId");
    }

    public EntityType getEntityType() { return entityType; }
    public int getAmount() { return amount; }
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public Location getSourceLocation() { return sourceLocation.clone(); }
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
