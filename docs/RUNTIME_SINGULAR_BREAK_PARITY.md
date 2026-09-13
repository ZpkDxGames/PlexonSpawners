# PlexonSpawners 4.0.0 — Singular Break Parity Remediation Evidence

## Scope

This document records the source-level remediation for the release-blocking physical `1x` spawner break defect. It does not claim live PlexonCraft certification.

## Root-cause proof

WildStacker 2026.2 has two relevant behaviors:

1. `WStackedSpawner(CreatureSpawner)` starts at logical amount `1`.
2. `SystemHandler#getStackedSpawner(...)` returns a newly created `WStackedSpawner` even when `isCached()` is false; cache membership only controls whether WildStacker stores that object.

PlexonSpawners 4.0.0 previously contradicted that contract in `WildStackerBridge#resolve(...)` by returning a resolved spawner only when `stacked.isCached()` was true. A normal physical singular/default spawner could therefore be treated as unresolved by Plexon's own pre/post checks.

The more direct final-unit failure was the old break transaction shape:

- `SpawnerUnstackEvent` stored a live `StackedSpawner` reference.
- no immutable recovery item or spawned-type snapshot was captured;
- the next-tick fallback re-read WildStacker/block-backed state after the `1 -> 0` removal;
- WildStacker removes the stack object during `runUnstack(...)` when the new logical amount is below `1`;
- its block-break listener later turns the physical block to air.

That post-removal read is safe enough for the observed `2x+ -> remaining stack` path because the physical spawner survives, but it is not safe for the final singular unit.

## Implemented correction

The 4.0.0 branch now uses one short-lived exactly-once break transaction:

1. observe physical `BlockBreakEvent` at `LOWEST` without taking mutation authority;
2. resolve WildStacker's public `StackedSpawner`, including valid transient/non-cached `1x` objects;
3. snapshot spawned type, policy decision and a defensive clone of WildStacker's `getDropItem(1)` before mutation;
4. enrich the same transaction from `SpawnerUnstackEvent#getAmount()` and replace the recovery snapshot with the exact authoritative amount when that event is present;
5. let `SpawnerDropEvent` remain the preferred native-drop interception/finalization signal;
6. otherwise reconcile the next tick from the captured pre-break amount and WildStacker's post-break state;
7. finalize through one atomic terminal gate so event + fallback paths cannot both pay.

No Plexon stack registry, stack database, PDC quantity, placement engine, upgrade system, right-click spawner GUI or WildStacker reflection was introduced.

## Protection behavior

Intent capture is deliberately not treated as success. WildStacker handles physical breaking at `HIGHEST` with `ignoreCancelled = true`; Plexon captures at `LOWEST`, then pays only if WildStacker events or the next-tick state prove a logical decrease/removal.

A protected/cancelled break that leaves the spawner and amount unchanged terminates as `DENIED_OR_UNCHANGED` with no recovery item, Essence or custom reward.

## Runtime status

Source/CI evidence and live evidence remain separate.

The exact candidate produced from this remediation must still be tested on PlexonCraft for:

- `1x` Silk recovery;
- `1x` non-Silk Essence;
- `1x` custom/BOTH reward;
- creative policy;
- `2x -> 1x`;
- final `1x -> 0`;
- upgraded singular type/tier preservation;
- protected/cancelled break;
- WildStacker right-click GUI regression;
- `/pspawners give` regression.

Until that exact CI-built JAR passes the live matrix:

```text
RUNTIME = NOT EXECUTED
STABLE PUBLICATION = BLOCKED
MIGRATION = NOT_REQUIRED
```
