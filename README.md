# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. Stable 3.0 adds durable managed-spawner identity, ownership/access, bounded tiers, Essence upgrades and first-party spawn provenance while preserving the 2.x Silk/Essence and WildStacker safety contract.

Current stable version: `3.0.0`.

## 3.0 product model

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

New managed spawners belong to the player who places them.

- `OWNER_ONLY`: owner/admin can inspect, manage and break.
- `PUBLIC_USE`: everyone may inspect/use; only owner/admin may manage or break.
- `PUBLIC`: everyone may inspect/use/break; only owner/admin may change tier/access.

Right-click a managed spawner to open its control GUI. The GUI shows creature, owner, tier tuning, lifetime spawns, access policy and next-tier upgrade cost.

## Tiers and upgrades

Five conservative tiers ship by default. A tier controls minimum/maximum spawn delay, spawn count, nearby-entity cap, required player range and spawn range.

Upgrades consume the exact PDC-backed Spawner Essence item. The transaction validates access and inventory first, reserves Essence, applies registry + physical state, and rolls back/refunds if physical application fails.

## Spawner provenance

For accepted Bukkit `SPAWNER` spawns, PlexonSpawners performs a bounded chunk-index lookup for a matching managed source, marks the entity with PDC provenance and records the stable source-spawner UUID.

Downstream Plexon plugins should consume `PlexonSpawnersApi` instead of lore checks, entity-history scans or per-hit database queries.

## Persistence and performance

The 3.0 runtime deliberately avoids per-spawner scheduling:

- one in-memory block index;
- one chunk index;
- one shared persistence coordinator;
- one single-thread snapshot writer;
- coalesced dirty revisions;
- atomic file replacement;
- chunk reconciliation only for already indexed managed records.

There are no global chunk/entity scans and no synchronous file writes in placement, break or spawn listeners. Shutdown cancels the coordinator before forcing the registry's final persistence flush.

## Recovery behavior

- qualifying Silk Touch can recover the typed managed spawner;
- failed qualification can roll Spawner Essence;
- tier is preserved when a 3.x managed spawner is recovered;
- 2.x schema-1 managed spawner items remain readable and map to tier 1;
- `EntityType.UNKNOWN` is rejected from managed records/items and invalid UNKNOWN physical breaks fail closed;
- WildStacker compatibility remains fail closed and removes only one unit when its provider safely accepts the operation.

## Core modes

PlexonSpawners supports PlexonCore 2.0.4 and Core API range `>=1.0 <3.0`. When Core is unavailable/incompatible, the established standalone bridge remains available. `/pspawners diagnostics` exposes the active mode and module state.

## Requirements

- Paper 26.2 build 121 or compatible fork
- Java 25
- PlexonCore 2.0.4 recommended/current ecosystem baseline
- WildStacker optional

## Commands

- `/pspawners admin`
- `/pspawners info`
- `/pspawners diagnostics`
- `/pspawners reload`
- `/pspawners give <player> <mob> [amount]`
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

The 3.0 API additionally exposes managed-spawner lookup/snapshots, tier-aware item creation and spawn-origin lookup. See `docs/API.md`.

## Migration from 2.3.1

Read `docs/MIGRATION_3_0.md` before deployment. Stop the server and back up both the old JAR and the complete plugin data directory. Stable 3.0 creates the managed registry as physical managed spawners enter the 3.x lifecycle; it does not globally scan historical world chunks.

Stable rollback is `v2.3.1` at `0ec54a04ecb77374874edf889b20286144c32a88`. Because 2.3.1 predates `managed-spawners.db`, rollback requires restoring the complete pre-3.0 plugin-data backup rather than asking 2.3.1 to consume 3.0 state.

## Building and release verification

CI provisions the immutable PlexonCore 2.0.4 API using a pinned SHA-256 and runs the full Gradle test/check/JAR contract. Stable publication is allowed only from `release/stable` when it points to exact current `main`.

The stable runtime artifact is:

```text
build/libs/PlexonSpawners-3.0.0.jar
```

The GitHub stable release publishes the JAR, `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then downloads and verifies those public assets before the Release workflow can pass.

Live PlexonCraft migration, placement/break/access/upgrade/restart/WildStacker/provenance/Spark/soak validation remains a deployment follow-up and is not inferred from CI. GitHub provenance may therefore record `runtime_certification=NOT_EXECUTED`.
