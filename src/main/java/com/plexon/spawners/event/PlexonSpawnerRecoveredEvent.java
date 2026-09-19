package com.plexon.spawners.event;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnerRecoveredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final EntityType entityType;
    private final int logicalAmount;
    private final ItemStack authoritativeItem;
    private final long transactionId;

    public PlexonSpawnerRecoveredEvent(
        final Player player,
        final EntityType entityType,
        final int logicalAmount,
        final ItemStack authoritativeItem,
        final long transactionId
    ) {
        this.player = player;
        this.entityType = entityType;
        this.logicalAmount = logicalAmount;
        this.authoritativeItem = authoritativeItem.clone();
        this.transactionId = transactionId;
    }

    public Player getPlayer() { return player; }
    public EntityType getEntityType() { return entityType; }
    public int getLogicalAmount() { return logicalAmount; }
    public ItemStack getAuthoritativeItem() { return authoritativeItem.clone(); }
    public long getTransactionId() { return transactionId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
