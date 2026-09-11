# PlexonSpawners 3.0.0-rc.2 — Phase 3 Reliability Candidate

PlexonSpawners 3.0.0-rc.2 is the reliability correction for the Phase 2 3.0.0 candidate line, built from the exact failed `v3.0.0-rc.1` source boundary.

## RC2 reliability correction

- Fixes the Paper 26.2 startup failure caused by calling `EntityType#getKey()` for the sentinel `EntityType.UNKNOWN` while building the managed-spawner entity lookup.
- Skips `UNKNOWN` and any other non-keyed `EntityType` while constructing lookup aliases.
- Centralizes `EntityType#getKey()` behind a guarded helper so item serialization cannot accidentally call it for a sentinel value.
- Explicitly rejects `EntityType.UNKNOWN` from managed-spawner records and managed item creation.
- Fails closed if an `UNKNOWN` creature-spawner type is encountered during a break; the spawner is preserved and is not converted to the legacy PIG fallback.
- Adds Paper 26.2 regression coverage for the actual `UNKNOWN#getKey()` exception, startup lookup construction, serialization rejection, display-name iteration, managed-record validation, and fallback ordering.
- Does not alter the managed-spawner database schema, item schema, tier model, ownership model, migration rules, or WildStacker behavior.

## Product rebuild retained from RC1

- Authoritative persistent managed-spawner identity with stable UUIDs.
- Owner/access policy (`OWNER_ONLY`, `PUBLIC_USE`, `PUBLIC`).
- Five bounded configurable tiers controlling delay, spawn count, nearby cap, activation range and spawn range.
- Tier preservation when a qualified managed spawner is recovered.
- Schema-1 2.x managed spawner items remain readable as tier 1 while new items use schema 2.
- Plexon-style right-click control GUI with tier/stat/access visibility.
- Transactional Essence upgrades with physical-state rollback and Essence refund on failure.

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

- Paper 26.2 build 121 / Java 25.
- PlexonCore 2.0.4 integration and standalone compatibility contract.
- Existing PDC-backed Spawner Essence identity.
- Existing Silk Touch / Essence recovery behavior for valid entity types.
- Existing public recovery, placement and Essence events.
- WildStacker remains fail closed.

## Upgrade / migration

1. Stop the server and back up the existing PlexonSpawners JAR and plugin data directory.
2. Replace the failed RC1 JAR with `PlexonSpawners-3.0.0-rc.2.jar` in the runtime-certification environment.
3. Keep the existing configuration and data directory unchanged; RC2 introduces no schema migration.
4. Existing 2.x schema-1 managed spawner items remain readable and place as tier 1.
5. Existing valid `managed-spawners.db` records retain their UUID, entity type, owner, tier, access, timestamps and lifetime spawn counts.
6. An invalid persisted record whose entity type is literally `UNKNOWN` is rejected as corrupt rather than converted to a valid mob type.
7. Run `/pspawners diagnostics` and complete the runtime matrix below.

## Certification boundary

**RUNTIME CERTIFICATION REQUIRED**

This is a prerelease candidate, not a stable production certification. Before any stable promotion, execute at minimum:

- clean startup on Paper 26.2 build 121 / Java 25 / PlexonCore 2.0.4 with no `EntityType doesn't have key` exception;
- existing managed-spawner database and schema-1 item migration/read checks;
- placement / break / Silk / Essence matrix;
- explicit `UNKNOWN` rejection/fail-closed verification;
- ownership and tier preservation;
- restart, persistence and chunk reconciliation;
- WildStacker compatibility if installed;
- Skills / Jobs first-party provenance consumption;
- Spark/MSPT baseline versus candidate under representative spawner use;
- minimum 30-minute soak for memory, task, queue and persistence stability;
- cross-plugin protection behavior.

Stable promotion remains blocked until the integrated Phase 3 runtime gates pass with no HIGH or CRITICAL known defect.

## Rollback

Do not move or overwrite `v3.0.0-rc.1`. The stable rollback source/artifact baseline remains `v2.3.1` (`0ec54a04ecb77374874edf889b20286144c32a88`). Preserve a backup of the entire plugin data directory before runtime validation.
