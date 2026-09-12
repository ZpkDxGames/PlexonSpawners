# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. Stable 3.1 adds a WildStacker-aware nearby logical population cap while preserving the 3.0 managed-spawner identity, ownership/access, tier, Essence, persistence and provenance contracts.

Current stable version: `3.1.0`.

## 3.1 nearby logical stack cap

3.1 adds a runtime-only population guard for Plexon-managed spawners. It does not persist a disabled state and does not replace the existing per-tier `max-nearby-entities` setting.

Default configuration:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

The radius uses Bukkit's bounded axis-aligned nearby-entity query around the center of the managed spawner block. With the default `same-type-only: true`, only living entities matching the managed spawner's `EntityType` contribute. WildStacker entities contribute their real logical stack amount: an `x70` and an `x29` Zombie stack count as 99, not two physical entities.

When the logical amount reaches 99, that managed spawner stops contributing. Once the population drops below 99, it becomes eligible automatically on the next spawn attempt. Multiple nearby managed spawners independently observe the same local population; different creature types do not block one another by default.

The WildStacker bridge remains optional and reflection-cached. When WildStacker is absent, each matching physical living entity counts as one. When WildStacker is installed but its required API is degraded, the spawn guard fails closed rather than treating a large stack as one entity. Dynamic WildStacker guard hooks are unregistered immediately when compatibility degrades so a broken reflective path is not repeatedly invoked.

WildStacker's standard `spawners.spawners-override.enabled: true` path is intercepted before its direct `StackedEntity#increaseStackAmount(...)` contribution. A whole contribution takes the fast path only when its maximum possible amount fits under the cap. Near the ceiling, direct stack growth is cancelled and Paper `PreSpawnerSpawnEvent` is used as a unit-granular gate so `97 / 99` can reach 99 without becoming 104. On a non-overridden path where a safe partial contribution is not exposed by the public event contract, PlexonSpawners rejects the whole at-risk cycle rather than knowingly allowing overshoot.

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

Five conservative tiers ship by default. A tier controls minimum/maximum spawn delay, spawn count, native nearby-entity cap, required player range and spawn range.

`managed.nearby-stack-cap.maximum-amount` is separate from tier `max-nearby-entities`: the former is a logical WildStacker-aware local population guard; the latter remains the existing native/physical spawner tuning.

Upgrades consume the exact PDC-backed Spawner Essence item. The transaction validates access and inventory first, reserves Essence, applies registry + physical state, and rolls back/refunds if physical application fails.

## Spawner provenance

For accepted Bukkit `SPAWNER` spawns, PlexonSpawners performs a bounded chunk-index lookup for a matching managed source, marks the entity with PDC provenance and records the stable source-spawner UUID.

Downstream Plexon plugins should consume `PlexonSpawnersApi` instead of lore checks, entity-history scans or per-hit database queries.

## Persistence and performance

The runtime deliberately avoids per-spawner scheduling:

- one in-memory block index;
- one chunk index;
- one shared persistence coordinator;
- one single-thread snapshot writer;
- coalesced dirty revisions;
- atomic file replacement;
- chunk reconciliation only for already indexed managed records;
- nearby stack-cap checks only on managed spawn attempts, using a bounded local query;
- cached WildStacker reflection, with no method discovery per nearby entity.

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
- WildStacker optional; its standard spawner-override mode is the primary stack-cap integration path

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

Read `docs/MIGRATION_3_0.md` when upgrading from 2.3.1 and `docs/MIGRATION_3_1.md` when moving from 3.0.0 to 3.1.0. Existing 3.0 configuration is migrated additively to config schema 6; administrator customizations are not rewritten.

Stable rollback for 3.1.0 is `v3.0.0` at `df5ba1970add67a46dc1afc0d844578144a88df1`, JAR SHA-256 `61978a50fcc39ccb2b025e2fe4b49ca8c28b2e849bdd9fb9d0eab9d89771e564`. The managed persistence schema remains 1 and the managed item schema remains 2.

## Building and release verification

CI provisions the immutable PlexonCore 2.0.4 API using a pinned SHA-256 and runs the full Gradle test/check/JAR contract. Stable publication is restricted to `release/stable` when it points to exact current `main`.

The stable runtime artifact is:

```text
build/libs/PlexonSpawners-3.1.0.jar
```

The GitHub stable release publishes the JAR, `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, downloads all public release assets again, verifies the checksum, exact source SHA, stable baseline and distribution contract, and only then completes.

GitHub source/build certification is separate from live PlexonCraft runtime certification. Live startup, managed placement/break, `x99` cap/resume, different-type isolation, overlapping spawners, stacked spawners, restart and MSPT/TPS checks are not inferred from CI; release provenance records `runtime_certification=NOT_EXECUTED` unless that operational evidence has been supplied.
