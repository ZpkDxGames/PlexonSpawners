# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. The 3.0 line adds persistent managed spawners, ownership/access, bounded tiers, Essence upgrades and first-party spawn provenance while preserving the 2.x Silk/Essence and WildStacker safety contract.

> Current Phase 2 branch version: `3.0.0-rc.1`. This is a prerelease candidate until PlexonCraft runtime certification passes.

## 3.0 product model

A Plexon-managed physical spawner now has a stable identity and durable state:

- spawner UUID;
- world/block coordinates;
- creature type;
- owner UUID;
- tier;
- access policy;
- placement timestamp;
- lifetime attributed spawns.

The authoritative index is persisted to `plugins/PlexonSpawners/managed-spawners.db`. The physical `CreatureSpawner` also receives recovery PDC. Unsupported database schema is rejected instead of silently resetting data.

## Ownership and access

New managed spawners belong to the player who places them.

- `OWNER_ONLY`: owner/admin can inspect, manage and break.
- `PUBLIC_USE`: everyone may inspect/use; only owner/admin may manage or break.
- `PUBLIC`: everyone may inspect/use/break; only owner/admin may change tier/access.

Right-click a managed spawner to open its control GUI. The GUI shows creature, owner, tier tuning, lifetime spawns, access policy and next-tier upgrade cost.

## Tiers and upgrades

Five conservative tiers ship by default. A tier controls:

- minimum/maximum spawn delay;
- spawn count;
- nearby-entity cap;
- required player range;
- spawn range.

Upgrades consume the exact PDC-backed Spawner Essence item. The transaction validates access and inventory first, reserves Essence, applies registry + physical state, and rolls back/refunds if physical application fails.

## Spawner provenance

For accepted Bukkit `SPAWNER` spawns, PlexonSpawners performs a bounded chunk-index lookup for a matching managed source, marks the entity with PDC provenance and records the stable source-spawner UUID.

Downstream Plexon plugins should consume `PlexonSpawnersApi` instead of using lore checks, entity-history scans or per-hit database queries.

## Persistence and performance

The 3.0 runtime deliberately avoids per-spawner scheduling:

- one in-memory block index;
- one chunk index;
- one shared persistence coordinator;
- one single-thread snapshot writer;
- coalesced dirty revisions;
- atomic file replacement;
- chunk-load reconciliation only for records already indexed in that chunk.

There are no global chunk/entity scans and no synchronous file writes in placement, break or spawn listeners.

## Recovery behavior

The existing recovery model remains:

- qualifying Silk Touch can recover the typed managed spawner;
- failed qualification can roll Spawner Essence;
- tier is preserved when a 3.x managed spawner is recovered;
- 2.x schema-1 managed spawner items remain readable and map to tier 1;
- WildStacker compatibility remains fail closed and removes only one unit when its provider safely accepts the operation.

## Core modes

PlexonSpawners supports the current PlexonCore 2.0.4 baseline and Core API range `>=1.0 <3.0`. When Core is unavailable/incompatible, the established standalone bridge remains available; `/pspawners diagnostics` exposes the active mode and module state.

## Requirements

- Paper 26.2
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

The 2.x synchronous events remain compatible:

- `PlexonSpawnerRecoveredEvent`
- `PlexonSpawnerEssenceAwardedEvent`
- `PlexonSpawnerPlacedEvent`

The 3.0 API additionally exposes managed-spawner lookup/snapshots, tier-aware item creation and spawn-origin lookup. See `docs/API.md`.

## Migration from 2.3.1

Read `docs/MIGRATION_3_0.md` before staging the candidate. In summary: back up the old JAR and plugin directory, keep existing config/data, retain existing managed items, and allow 3.0 to create its managed registry only as new physical managed spawners are placed/recovered.

## Building

CI verifies the pinned PlexonCore 2.0.4 artifact and runs:

```bash
gradle clean check --stacktrace
```

The candidate runtime artifact is:

```text
build/libs/PlexonSpawners-3.0.0-rc.1.jar
```

A release-candidate branch may publish `v3.0.0-rc.1` only after CI succeeds. Stable `3.0.0` promotion remains blocked until the documented PlexonCraft runtime gates pass.
