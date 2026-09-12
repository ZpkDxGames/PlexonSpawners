# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. The 3.x line provides persistent managed identity, ownership/access, tiers, Essence upgrades, provenance, WildStacker integration and bounded runtime controls.

Current stable version: `3.1.1`.

Current candidate: `3.2.0-rc.1`.

## 3.2 WildStacker runtime compatibility

WildStacker integration is automatic when WildStacker is installed and its public API is available. PlexonSpawners does not duplicate WildStacker's own entity-stacking enable/disable configuration.

The standard WildStacker spawner-override path remains stack-native through PlexonSpawners' logical population cap. When an entire contribution fits, WildStacker keeps its optimized direct-stack path. Near the ceiling, PlexonSpawners blocks only an unsafe direct merge, lets WildStacker construct the pending stacked entity, reads its real logical amount, and trims that stack to the exact remaining capacity before the entity enters the world.

Example with a logical cap of 99: if nearby Zombies total 97 and WildStacker prepares an x4 spawn, PlexonSpawners permits an x2 WildStacker stack so the resulting logical population is exactly 99. It does not intentionally turn that cycle into loose one-by-one mobs.

The bridge uses lifecycle-cached reflection against WildStacker's public API (`getStackedEntity`, `getEntityAmount`, `getStackedSpawner`, `getSpawnersAmount`, and `StackedEntity#setStackAmount`). If WildStacker is detected but the required API becomes unavailable, stack-sensitive operations fail closed rather than treating an unknown stack as one entity.

Non-overridden/custom WildStacker paths that do not publicly expose a safe pending logical contribution remain conservatively whole-cycle gated instead of knowingly overshooting the configured cap.

## 3.2 redstone spawner lock

Configuration schema 7 adds:

```yaml
managed:
  redstone-lock:
    enabled: true
    poll-interval-ticks: 20
```

A powered managed spawner is actually paused: PlexonSpawners captures the live spawn delay, stores it in memory and physical PDC, moves the physical spawner to an internal hold delay, and restores the frozen countdown when power is removed. Direct and indirect redstone power are recognized.

The design uses one shared reconciliation task for managed spawners in loaded chunks, plus redstone/block events and spawn-time safety gates. It does not create a scheduler per spawner. Chunk unload and orderly plugin shutdown restore the physical countdown; PDC recovery also protects the frozen value if an interrupted runtime leaves the hold state behind.

## Live spawner information

Right-click a managed spawner to open the control GUI. The central **Runtime Status** item reports:

- ticks until the next spawn;
- approximate seconds until the next spawn;
- redstone lock state;
- actual powered/unpowered signal state;
- WildStacker automatic stacked-output status and logical spawner amount;
- logical nearby cap and radius.

Click Runtime Status to refresh the live values.

## Nearby logical stack cap

PlexonSpawners has an independent local population guard for managed spawners:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

The radius uses Bukkit's bounded axis-aligned nearby-entity query around the center of the managed spawner block. With `same-type-only: true`, only living entities matching the managed spawner's entity type contribute. WildStacker entities contribute their real logical amount: an x70 and x29 Zombie stack count as 99, not two physical entities.

At the cap the managed spawner contributes nothing. Once the logical population drops below the limit, it becomes eligible automatically on the next spawn cycle. Multiple nearby managed spawners independently observe the same local population; different creature types do not block one another by default.

`managed.nearby-stack-cap.maximum-amount` is separate from the tier's native `max-nearby-entities` value.

## Managed-spawner product model

A Plexon-managed physical spawner has durable state:

- stable spawner UUID;
- world/block coordinates;
- creature type;
- owner UUID;
- tier;
- access policy;
- placement timestamp;
- lifetime attributed spawns.

The authoritative index is persisted to `plugins/PlexonSpawners/managed-spawners.db` using schema 1. The physical `CreatureSpawner` also carries recovery PDC. Unsupported database schema is rejected instead of silently resetting data.

Chunk reconciliation is identity-safe: registry state is reapplied only when the physical spawner proves the same managed UUID and exact world/block identity. A vanilla, corrupt, missing or mismatched replacement spawner is not claimed by a stale registry row.

## Ownership and access

- `OWNER_ONLY`: owner/admin can inspect, manage and break.
- `PUBLIC_USE`: everyone may inspect/use; only owner/admin may manage or break.
- `PUBLIC`: everyone may inspect/use/break; only owner/admin may change tier/access.

## Tiers and upgrades

Five conservative tiers ship by default. A tier controls minimum/maximum spawn delay, spawn count, native nearby-entity cap, required player range and spawn range.

Upgrades consume the exact PDC-backed Spawner Essence item. The transaction validates access and inventory first, reserves Essence, applies registry + physical state, and rolls back/refunds if physical application fails. A powered spawner is immediately re-reconciled after a physical tier/access update so its redstone lock remains effective.

## Spawner provenance

For accepted Bukkit `SPAWNER` spawns, PlexonSpawners performs a bounded chunk-index lookup for a matching managed source, marks the entity with PDC provenance and records the stable source-spawner UUID.

Downstream Plexon plugins should consume `PlexonSpawnersApi` instead of lore checks, entity-history scans or per-hit database queries.

## Persistence and performance

The runtime is deliberately bounded:

- one in-memory block index;
- one chunk index;
- one shared persistence coordinator;
- one single-thread snapshot writer;
- one shared redstone-lock reconciliation task over loaded managed spawners;
- coalesced dirty revisions;
- atomic file replacement;
- chunk reconciliation only for already indexed managed records;
- nearby stack-cap checks only on managed spawn attempts, using a bounded local query;
- cached WildStacker public-API reflection, with no method discovery per nearby entity.

There are no global chunk/entity scans and no synchronous database/file writes in placement, break or spawn listeners.

## Recovery behavior

- qualifying Silk Touch can recover the typed managed spawner;
- failed qualification can roll Spawner Essence;
- tier is preserved when a 3.x managed spawner is recovered;
- 2.x schema-1 managed spawner items remain readable and map to tier 1;
- `EntityType.UNKNOWN` is rejected from managed records/items and invalid UNKNOWN physical breaks fail closed;
- WildStacker compatibility remains fail closed and removes only one spawner unit when its provider safely accepts the operation.

## Core modes

PlexonSpawners supports PlexonCore 2.0.4 and Core API range `>=1.0 <3.0`. When Core is unavailable/incompatible, the established standalone bridge remains available. `/pspawners diagnostics` exposes the active mode and module state.

## Requirements

- Paper 26.2 build 121 or compatible fork
- Java 25
- PlexonCore 2.0.4 recommended/current ecosystem baseline
- WildStacker optional; its standard spawner-override flow is the primary exact stacked-output integration path

## Commands

- `/pspawners admin`
- `/pspawners info`
- `/pspawners diagnostics`
- `/pspawners reload`
- `/pspawners give <player> <mob> [amount] [tier]`
- `/pspawners essence set`
- `/pspawners essence give <player> [amount]`

## Permissions

- `plexonspawners.admin`
- `plexonspawners.admin.gui`
- `plexonspawners.admin.reload`
- `plexonspawners.admin.give`
- `plexonspawners.admin.essence`
- `plexonspawners.bypass.silk`
- `plexonspawners.bypass.access`

## Public API and events

`com.plexon.spawners.api.PlexonSpawnersApi` is registered through Bukkit `ServicesManager`.

The synchronous public events remain:

- `PlexonSpawnerRecoveredEvent`
- `PlexonSpawnerEssenceAwardedEvent`
- `PlexonSpawnerPlacedEvent`

The 3.x API exposes managed-spawner lookup/snapshots, tier-aware item creation and spawn-origin lookup. See `docs/API.md`.

## Migration

- `docs/MIGRATION_3_0.md`: upgrade from the 2.3 line to managed 3.0.
- `docs/MIGRATION_3_1.md`: logical nearby stack cap and config visibility fixes.
- `docs/MIGRATION_3_2.md`: WildStacker stacked-output preservation, runtime status and redstone lock.

3.2 advances configuration schema to 7. Managed persistence remains schema 1 and managed items remain schema 2.

Stable rollback for the 3.2 candidate is `v3.1.1` at `e9c50532ba0c227153ddb69f70a073e04d326a01`, JAR SHA-256 `5dadb49f91b40d24a3d4ff17acb7f2a5f96fdfb8d01854eafefb6d520ffb310c`.

## Building and release verification

CI provisions the immutable PlexonCore 2.0.4 API using its pinned SHA-256 and runs the full Gradle test/check/JAR contract.

The 3.2 candidate runtime artifact is:

```text
build/libs/PlexonSpawners-3.2.0-rc.1.jar
```

GitHub source/build certification is separate from live PlexonCraft runtime certification. The 3.2 RC must still be verified on the production WildStacker configuration for stacked output, 97 -> 99 style cap trimming, automatic resume, redstone freeze/unfreeze, timer display, chunk unload/reload, restart and farm TPS/MSPT before stable promotion.
