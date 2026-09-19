# PlexonSpawners 4.0.0 — Singular Break Parity Remediation Evidence

## Scope

This document records source-level remediation for the release-blocking physical 1x break defect.
It does not claim live certification or migration status.

## Root cause

WildStacker can expose a valid transient/non-cached singular StackedSpawner. Cache membership is not
a validity requirement. The previous resolver incorrectly rejected that representation.

The final-unit path also could lose required pre-removal state because 1 -> 0 destroys the stack/block
before a next-tick reread.

## Source correction

The 4.0 coordinator:

1. observes BlockBreakEvent at LOWEST without taking mutation authority;
2. accepts valid transient/non-cached WildStacker objects;
3. snapshots spawned type and getDropItem(1) before destruction;
4. uses SpawnerUnstackEvent#getAmount() when emitted;
5. refreshes the authoritative recovery snapshot to the exact emitted amount;
6. prefers SpawnerDropEvent for native-drop finalization;
7. uses next-tick state reconciliation for event-gap/final-unit cases;
8. uses one BreakCompletionGate terminal result;
9. pays nothing when the protected/unchanged state proves no logical removal.

No Plexon stack registry, stack database, placement engine, upgrade system, placed-spawner GUI or
WildStacker reflection is introduced.

## Runtime gate

The exact final candidate still requires the complete matrix in:

.release/RUNTIME_CERTIFICATION_4.0.0.template

Until that exact JAR passes:

RUNTIME=PENDING
MIGRATION=PENDING
STABLE_PUBLICATION=BLOCKED
