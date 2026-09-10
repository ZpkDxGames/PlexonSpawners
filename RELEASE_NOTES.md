# PlexonSpawners 3.0.0-rc.1 — Phase 2 Premium Rebuild Candidate

PlexonSpawners 3.0.0-rc.1 is the first Phase 2 candidate built from the certified 2.3.1 / PlexonCore 2.0.4 baseline.

## Product rebuild

- Adds authoritative persistent managed-spawner identity with stable UUIDs.
- Adds owner/access policy (`OWNER_ONLY`, `PUBLIC_USE`, `PUBLIC`).
- Adds five bounded configurable tiers controlling delay, spawn count, nearby cap, activation range and spawn range.
- Preserves tier when a qualified managed spawner is recovered.
- Keeps schema-1 2.x managed spawner items readable as tier 1 while new items use schema 2.
- Adds a Plexon-style right-click control GUI with tier/stat/access visibility.
- Adds transactional Essence upgrades with physical-state rollback and Essence refund on failure.

## Persistence and performance

- Uses an in-memory block index plus chunk index; no global spawner scan is introduced.
- Uses one shared persistence coordinator and one bounded single-thread writer.
- Coalesces placement, access, tier and spawn-stat mutations into snapshot flushes.
- Uses atomic replacement for `managed-spawners.db`.
- Refuses unsupported database schema headers rather than silently resetting state.
- Reconciliation touches only already indexed records for the loaded chunk.
- No per-player, per-entity, per-block or per-spawner repeating task is introduced.

## First-party spawn provenance

- Accepted `SPAWNER` entity spawns are matched through bounded chunk-indexed lookups.
- Spawned entities receive PlexonSpawners PDC origin metadata and the stable source spawner UUID.
- Lifetime spawn statistics are incremented in memory and persisted through the shared batch flow.
- `PlexonSpawnersApi` exposes provenance and managed-spawner lookup so Skills, Jobs, Quests and Keys can consume first-party origin state without lore checks, history scans or per-hit database reads.

## Preserved compatibility

- Paper 26.2 / Java 25.
- PlexonCore 2.0.4 integration and standalone compatibility contract.
- Existing PDC-backed Spawner Essence identity.
- Existing Silk Touch / Essence recovery behavior.
- Existing public recovery, placement and Essence events.
- WildStacker remains fail closed.

## Upgrade / migration

1. Stop the server and back up the existing `PlexonSpawners` JAR and plugin data directory.
2. Replace the JAR with `PlexonSpawners-3.0.0-rc.1.jar` only in a staging/runtime-certification environment.
3. Keep the existing configuration directory. Stock configuration is migrated to config schema 5 without deleting custom settings.
4. Existing 2.x schema-1 managed spawner items remain readable and place as tier 1.
5. New physical managed spawners are indexed into `managed-spawners.db`; do not delete this file after beginning 3.x runtime validation.
6. Run `/pspawners diagnostics` and complete the runtime matrix below.

## Certification boundary

**RUNTIME CERTIFICATION NOT EXECUTED**

This is a prerelease candidate, not a stable production certification. Before promotion to `3.0.0`, execute at minimum:

- placement / break / Silk / Essence matrix;
- ownership and all access states;
- tier upgrade success, insufficient-funds and rollback/refund paths;
- restart, persistence and chunk reconciliation;
- representative migration from the 2.3.1 configuration and existing schema-1 items;
- WildStacker compatibility if installed;
- Skills / Jobs first-party provenance consumption;
- Spark/MSPT baseline versus candidate under representative spawner use;
- minimum 30-minute soak for memory, task, queue and persistence stability;
- cross-plugin protection behavior.

Stable promotion is blocked until these runtime gates pass with no HIGH or CRITICAL known defect.

## Rollback

Rollback source/artifact baseline: `v2.3.1` (`0ec54a04ecb77374874edf889b20286144c32a88`). Preserve a backup of the entire plugin data directory before first 3.x staging startup.
