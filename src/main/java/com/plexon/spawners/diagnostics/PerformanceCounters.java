package com.plexon.spawners.diagnostics;

/**
 * Main-thread gameplay counters used for diagnostics and profiling correlation.
 * No logging, scheduling, allocation, or persistence occurs on increment paths.
 */
public final class PerformanceCounters {
    private long blockBreakEventsSeen;
    private long nonSpawnerFastRejects;
    private long disabledRejects;
    private long worldRejects;
    private long acceptedSpawnerBreaks;
    private long wildStackerNotInstalled;
    private long wildStackerNotStacked;
    private long wildStackerSuccess;
    private long wildStackerCancelled;
    private long wildStackerDegraded;
    private long qualifiedRecoveries;
    private long essenceRolls;
    private long essenceWins;
    private long essenceLogicalAmountAwarded;
    private long essenceItemStacksCreated;
    private long essenceGroundEntitiesCreated;
    private long blockPlaceEventsSeen;
    private long managedPlacementSuccesses;
    private long vanillaSpawnerPlacementRejects;
    private long nearbyStackCapChecks;
    private long nearbyStackCapBlocked;
    private long nearbyStackCapLogicalEntitiesCounted;
    private long nearbyStackCapWildStackerLookups;
    private long nearbyStackCapFailClosed;

    public void blockBreakSeen() { blockBreakEventsSeen++; }
    public void nonSpawnerFastReject() { nonSpawnerFastRejects++; }
    public void disabledReject() { disabledRejects++; }
    public void worldReject() { worldRejects++; }
    public void acceptedSpawnerBreak() { acceptedSpawnerBreaks++; }
    public void wildStackerNotInstalled() { wildStackerNotInstalled++; }
    public void wildStackerNotStacked() { wildStackerNotStacked++; }
    public void wildStackerSuccess() { wildStackerSuccess++; }
    public void wildStackerCancelled() { wildStackerCancelled++; }
    public void wildStackerDegraded() { wildStackerDegraded++; }
    public void qualifiedRecovery() { qualifiedRecoveries++; }
    public void essenceRoll() { essenceRolls++; }
    public void essenceWin() { essenceWins++; }
    public void essenceLogicalAmountAwarded(final int amount) { essenceLogicalAmountAwarded += amount; }
    public void essenceItemStacksCreated(final int count) { essenceItemStacksCreated += count; }
    public void essenceGroundEntitiesCreated(final int count) { essenceGroundEntitiesCreated += count; }
    public void blockPlaceSeen() { blockPlaceEventsSeen++; }
    public void managedPlacementSuccess() { managedPlacementSuccesses++; }
    public void vanillaSpawnerPlacementReject() { vanillaSpawnerPlacementRejects++; }
    public void nearbyStackCapCheck() { nearbyStackCapChecks++; }
    public void nearbyStackCapBlocked() { nearbyStackCapBlocked++; }
    public void nearbyStackCapLogicalEntitiesCounted(final int amount) { nearbyStackCapLogicalEntitiesCounted += amount; }
    public void nearbyStackCapWildStackerLookup() { nearbyStackCapWildStackerLookups++; }
    public void nearbyStackCapFailClosed() { nearbyStackCapFailClosed++; }

    public Snapshot snapshot() {
        return new Snapshot(
            blockBreakEventsSeen,
            nonSpawnerFastRejects,
            disabledRejects,
            worldRejects,
            acceptedSpawnerBreaks,
            wildStackerNotInstalled,
            wildStackerNotStacked,
            wildStackerSuccess,
            wildStackerCancelled,
            wildStackerDegraded,
            qualifiedRecoveries,
            essenceRolls,
            essenceWins,
            essenceLogicalAmountAwarded,
            essenceItemStacksCreated,
            essenceGroundEntitiesCreated,
            blockPlaceEventsSeen,
            managedPlacementSuccesses,
            vanillaSpawnerPlacementRejects,
            nearbyStackCapChecks,
            nearbyStackCapBlocked,
            nearbyStackCapLogicalEntitiesCounted,
            nearbyStackCapWildStackerLookups,
            nearbyStackCapFailClosed
        );
    }

    public record Snapshot(
        long blockBreakEventsSeen,
        long nonSpawnerFastRejects,
        long disabledRejects,
        long worldRejects,
        long acceptedSpawnerBreaks,
        long wildStackerNotInstalled,
        long wildStackerNotStacked,
        long wildStackerSuccess,
        long wildStackerCancelled,
        long wildStackerDegraded,
        long qualifiedRecoveries,
        long essenceRolls,
        long essenceWins,
        long essenceLogicalAmountAwarded,
        long essenceItemStacksCreated,
        long essenceGroundEntitiesCreated,
        long blockPlaceEventsSeen,
        long managedPlacementSuccesses,
        long vanillaSpawnerPlacementRejects,
        long nearbyStackCapChecks,
        long nearbyStackCapBlocked,
        long nearbyStackCapLogicalEntitiesCounted,
        long nearbyStackCapWildStackerLookups,
        long nearbyStackCapFailClosed
    ) {}
}
