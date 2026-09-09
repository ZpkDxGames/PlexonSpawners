# PlexonSpawners 2.3.0 — Performance & Reliability

PlexonSpawners 2.3.0 is a focused performance, scalability and reliability release. It keeps the 2.2.0 gameplay/API contract while moving repeated integration discovery, item construction and entity lookup work out of gameplay hot paths.

## Performance

- WildStacker integration is discovered and validated at lifecycle boundaries instead of on every accepted spawner break.
- WildStacker API accessors are cached; stacked-object accessors are resolved once per compatible provider class and reused.
- Managed spawner items use per-EntityType cached templates and clone only the final awarded stack.
- Managed placement resolves stored `spawner_type` PDC keys through an O(1) lookup instead of scanning every `EntityType`.
- Entity display names and the admin spawnable-entity list are precomputed.
- World restrictions prefer resolved world UUIDs and only use normalized-name fallback when needed.
- Silk bypass permission checks only run when the held tool actually fails the configured Silk requirement.
- Transaction UUIDs are generated only when a successful public outcome needs one.
- Successful break outcomes reuse a single source `Location` snapshot.
- Essence max-stack size is cached.
- Bulk Essence awards create the minimum physical stack set and inventory delivery uses a single `Inventory#addItem` call.

## Reliability

- WildStacker remains fail closed. Missing/incompatible APIs, cancelled unstack operations and a disabled provider never cause PlexonSpawners to force-delete a possibly stacked spawner.
- WildStacker enable/disable lifecycle events refresh or invalidate the bridge safely without per-break plugin discovery.
- Existing 2.x managed spawners remain readable. New managed items include an internal optional schema marker for future compatibility.
- PDC-backed managed-spawner and Spawner Essence identity remains authoritative; visual lookalikes are not trusted.
- Large ground-delivery Essence configurations produce startup/reload warnings instead of silently changing configured rewards.
- Runtime settings are compiled into one immutable snapshot and swapped after parsing.
- Configuration validation warnings are exposed through startup logs and diagnostics.

## Diagnostics

`/pspawners diagnostics` now includes:

- Paper/Java/Core mode and Core API range;
- WildStacker state, resolution mode and cached API readiness;
- break ownership, Silk requirement and world-filter state;
- Essence delivery/defaults/override count/max stack size;
- managed-spawner template cache size;
- entity-key lookup size;
- configuration warning count;
- public API/event readiness.

## Compatibility

- Paper 26.2
- Java 25
- optional PlexonCore 1.x
- optional WildStacker compatibility preserved
- public `PlexonSpawnerRecoveredEvent`, `PlexonSpawnerEssenceAwardedEvent` and `PlexonSpawnerPlacedEvent` preserved
- no database migration
- no persistent spawner-location index
- no global chunk/entity scans
- no repeating per-spawner tasks

## Upgrade

1. Stop the server.
2. Back up the current PlexonSpawners JAR and `plugins/PlexonSpawners/` directory.
3. Replace the old JAR with `PlexonSpawners-2.3.0.jar`.
4. Keep the existing configuration/data directory.
5. Start the server and run `/pspawners diagnostics`.
6. Validate one Silk recovery, one failed-Silk Essence outcome, one managed placement and one stacked-spawner recovery.
7. Run a short Spark comparison under ordinary mining and stacked-spawner use before declaring the production rollout complete.

## Release assets

- `PlexonSpawners-2.3.0.jar`
- `SHA256SUMS.txt`
