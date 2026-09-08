package com.plexon.spawners.event;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnerRecoveredEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final EntityType entityType;
    private final int spawnerAmount;
    private final Location sourceLocation;
    private final int silkTouchLevel;
    private final boolean usedBypass;
    private final boolean wildStackerManaged;
    private final String eventId;
    private final String transactionId;

    public PlexonSpawnerRecoveredEvent(
        Player player,
        EntityType entityType,
        int spawnerAmount,
        Location sourceLocation,
        int silkTouchLevel,
        boolean usedBypass,
        boolean wildStackerManaged,
        String eventId,
        String transactionId
    ) {
        super(player);
        this.entityType = entityType;
        this.spawnerAmount = spawnerAmount;
        this.sourceLocation = sourceLocation.clone();
        this.silkTouchLevel = silkTouchLevel;
        this.usedBypass = usedBypass;
        this.wildStackerManaged = wildStackerManaged;
        this.eventId = requireId(eventId, "eventId");
        this.transactionId = requireId(transactionId, "transactionId");
    }

    public EntityType getEntityType() { return entityType; }
    public int getSpawnerAmount() { return spawnerAmount; }
    public Location getSourceLocation() { return sourceLocation.clone(); }
    public int getSilkTouchLevel() { return silkTouchLevel; }
    public boolean usedBypass() { return usedBypass; }
    public boolean isWildStackerManaged() { return wildStackerManaged; }
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
