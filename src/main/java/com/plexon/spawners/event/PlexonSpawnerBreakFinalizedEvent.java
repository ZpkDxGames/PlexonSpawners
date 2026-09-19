package com.plexon.spawners.event;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Post-commit policy observation. It never grants stack mutation authority. */
public final class PlexonSpawnerBreakFinalizedEvent extends Event {
    public enum Outcome { RECOVERED, REWARDED, NO_REWARD_POLICY, ABORTED }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final EntityType entityType;
    private final int logicalAmount;
    private final Outcome outcome;
    private final long transactionId;

    public PlexonSpawnerBreakFinalizedEvent(
        final Player player,
        final EntityType entityType,
        final int logicalAmount,
        final Outcome outcome,
        final long transactionId
    ) {
        this.player = player;
        this.entityType = entityType;
        this.logicalAmount = logicalAmount;
        this.outcome = outcome;
        this.transactionId = transactionId;
    }

    public Player getPlayer() { return player; }
    public EntityType getEntityType() { return entityType; }
    public int getLogicalAmount() { return logicalAmount; }
    public Outcome getOutcome() { return outcome; }
    public long getTransactionId() { return transactionId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
