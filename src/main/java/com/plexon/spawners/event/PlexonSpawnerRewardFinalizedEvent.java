package com.plexon.spawners.event;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonSpawnerRewardFinalizedEvent extends Event {
    public enum RewardKind { ESSENCE, CUSTOM_ITEM }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final EntityType entityType;
    private final int logicalAmount;
    private final RewardKind rewardKind;
    private final long rewardAmount;
    private final long transactionId;

    public PlexonSpawnerRewardFinalizedEvent(
        final Player player,
        final EntityType entityType,
        final int logicalAmount,
        final RewardKind rewardKind,
        final long rewardAmount,
        final long transactionId
    ) {
        this.player = player;
        this.entityType = entityType;
        this.logicalAmount = logicalAmount;
        this.rewardKind = rewardKind;
        this.rewardAmount = rewardAmount;
        this.transactionId = transactionId;
    }

    public Player getPlayer() { return player; }
    public EntityType getEntityType() { return entityType; }
    public int getLogicalAmount() { return logicalAmount; }
    public RewardKind getRewardKind() { return rewardKind; }
    public long getRewardAmount() { return rewardAmount; }
    public long getTransactionId() { return transactionId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
