# PlexonSpawners 3.0.0 — Premium-Tier Product Rebuild

## Status

Execution-grade Phase 2 specification. This document is not a completion claim; implementation, CI and runtime gates remain authoritative.

## Baseline

- Repository: `ZpkDxGames/PlexonSpawners`
- Source baseline: `v2.3.1`
- Baseline commit: `0ec54a04ecb77374874edf889b20286144c32a88`
- Paper: `26.2`
- Java: `25`
- PlexonCore: `2.0.4`
- Phase 2 target: `3.0.0`
- First candidate: `3.0.0-rc.1`

## Product objective

Turn the 2.3 maintenance implementation into the authoritative first-party managed-spawner product while preserving its proven hot-path and integration safety rules.

The product must provide:

1. typed placement and recovery;
2. persistent managed-spawner identity;
3. ownership and access policy;
4. bounded tier/upgrades;
5. concrete vanilla spawner tuning per tier;
6. spawn-origin provenance for Skills/Jobs/Quests/Keys consumers;
7. lifetime statistics without per-entity persistence writes;
8. chunk-bounded reconciliation after restarts;
9. safe player/admin control surfaces;
10. migration-safe recovery from the 2.x item contract;
11. diagnostics and release evidence.

## Non-goals

- No per-spawner repeating scheduler.
- No global world/entity/chunk scans.
- No synchronous file write in placement, break or spawn events.
- No direct economy ownership; TheosisEconomy/Vault remain external authority.
- No output-item storage system in 3.0 unless a real product requirement appears; vanilla spawners do not have an output inventory and fake storage would create unnecessary overlap.
- No persistent hologram entity farm. Visual status belongs in the control GUI unless a later bounded hologram design proves safe.

## Authoritative state

`plugins/PlexonSpawners/managed-spawners.db` is the durable managed-spawner index.

Each record stores:

- stable spawner UUID;
- world UUID;
- block coordinates;
- EntityType;
- owner UUID;
- tier;
- access mode;
- placed timestamp;
- lifetime spawned-mob count.

The placed `CreatureSpawner` also receives recovery PDC containing stable identity, owner, tier and access. The file index is authoritative; block PDC is a bounded recovery/reconciliation copy, not a reason to scan the world.

## Persistence strategy

- Read once during enable.
- Maintain an in-memory block index and chunk index.
- Mutations only mark the registry dirty.
- One shared repeating coordinator requests a snapshot flush on a configurable interval.
- A single daemon writer performs atomic temp-file replacement.
- Spawn-stat increments are coalesced into that shared persistence cadence.
- Shutdown performs one final bounded flush.
- Unsupported on-disk schema fails loudly instead of resetting data.

## Chunk strategy

On `ChunkLoadEvent`, inspect only records already indexed for that exact chunk. Reapply PDC/tier state to valid spawner blocks and remove stale records whose physical block no longer exists. Never enumerate all spawners in all loaded chunks.

## Tier model

Tier profiles are configuration-backed and bounded. Each profile controls:

- min/max spawn delay;
- spawn count;
- max nearby entities;
- required player range;
- spawn range;
- Essence upgrade cost.

Tier 1 remains close to vanilla defaults. Higher tiers improve throughput gradually while preserving nearby caps and avoiding aggressive defaults.

## Ownership/access

Every new managed spawner receives the placing player as owner.

Access states:

- `OWNER_ONLY`: only owner/admin can use/manage/break;
- `PUBLIC_USE`: anyone may inspect/use; only owner/admin can modify/break;
- `PUBLIC`: anyone may inspect/use/break; only owner/admin may change tier/access.

Admins retain a bypass through `plexonspawners.admin` / dedicated access bypass permission.

## Upgrade transaction

Upgrade flow:

1. validate spawner still exists and actor can manage it;
2. resolve next configured tier;
3. verify exact PDC-backed Spawner Essence balance in inventory;
4. consume Essence;
5. update registry;
6. apply physical `CreatureSpawner` profile/PDC;
7. if physical apply fails, roll registry back and refund Essence;
8. mark one batched persistence revision.

No economy deposit/withdrawal is introduced.

## Item migration

2.x schema-1 managed spawner items remain readable and implicitly map to tier 1. New 3.x items use schema 2 with a `spawner_tier` PDC key. Recovery preserves tier.

## Spawn provenance

For accepted `CreatureSpawnEvent` events with reason `SPAWNER`:

- query only the managed registry chunks intersecting the configured bounded radius;
- require matching EntityType;
- require distance compatible with the source tier's spawn range;
- mark the spawned entity with PlexonSpawners PDC provenance and source spawner UUID;
- increment the source record's lifetime spawn counter in memory.

Expose provenance through `PlexonSpawnersApi` so downstream Plexon systems never need lore checks, entity history scans or per-hit database reads.

## Control GUI

Right-clicking a managed spawner opens a compact Plexon-style control surface when allowed. It shows:

- creature;
- owner;
- tier and tuned values;
- lifetime spawns;
- access state;
- next-tier cost;
- upgrade control for owner/admin;
- access-cycle control for owner/admin;
- stable close behavior.

Inventory clicks/drags are protected. Destructive or paid actions are transactional.

## Existing compatibility retained

- strict managed-item PDC identity;
- Silk Touch recovery rules;
- Essence fallback;
- WildStacker fail-closed behavior;
- PlexonCore 2.0.4 module lifecycle;
- public 2.x recovery/place/Essence events;
- Paper 26.2 / Java 25.

## CI gates

Candidate CI must execute:

```text
gradle clean check
verifyDistribution
git diff --check
JAR existence/contents
Core non-shading check
SHA-256 generation + verification
```

Add source-contract tests for:

- schema-1 item migration;
- schema-2 tier identity;
- no file I/O in high-frequency listeners;
- one shared persistence coordinator;
- no per-spawner task creation;
- chunk-indexed provenance lookup;
- owner/access gate;
- transactional Essence upgrade rollback path;
- Core 2.0.4 compatibility.

## RC policy

If GitHub CI is green but PlexonCraft runtime certification is unavailable, publish `v3.0.0-rc.1` as a prerelease and state exactly:

`RUNTIME CERTIFICATION NOT EXECUTED`

Missing runtime gates must include:

- placement/break matrix;
- ownership/access matrix;
- tier upgrade/refund behavior;
- restart/persistence/reconciliation;
- WildStacker behavior if installed;
- Skills/Jobs provenance integration;
- Spark/MSPT baseline comparison;
- minimum 30-minute soak.

## Stable gate

Do not promote `3.0.0` stable until the candidate passes actual PlexonCraft runtime validation with no HIGH/CRITICAL known defect.

## Rollback

Rollback target remains `v2.3.1`. Before candidate runtime deployment, back up the 2.3.1 JAR and the complete `plugins/PlexonSpawners/` directory. 3.x data must never be silently destroyed when rolling backward.