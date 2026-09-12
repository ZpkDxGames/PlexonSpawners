# PlexonSpawners 3.4.0 — Stacking, Entity Aggregation & Essence Reliability

PlexonSpawners 3.4.0 fixes three player-visible regressions in the 3.3 stable line while preserving the 3.3 native-stack persistence and migration boundary.

## Fixed

- **Adjacent native spawner placement now works by default.** New 3.4 configurations enable same-level nearby auto-stack with radius `1`, while still preserving entity type, owner, tier and access compatibility rules.
- **Spawner-spawned mobs can aggregate directly into an existing nearby compatible entity stack again.** Plexon owns the logical contribution and cap; WildStacker is only the optional entity-stack representation backend.
- **The 3.3 blanket WildStacker `EntityStackEvent` cancellation path is removed.** `stack-interval: 0` remains a valid production setup.
- **Essence is stack-aware and deterministic in tests.** Unqualified native-stack removal performs one eligibility roll per logical spawner unit removed and aggregates successful rewards before delivery.

## Architecture

- Added a pure `ManagedSpawnAggregationService` for exact nearby-cap planning and deterministic target selection: nearest compatible target first, UUID tie-break second.
- Added an `EntityStackBackend` contract and bounded `PhysicalFallbackBackend`.
- Extended the cached WildStacker public-API bridge with provider compatibility checks, target stack mutation, source removal and rollback on mutation failure.
- Provider reflection discovery remains lifecycle-cached; no reflection lookup is performed in the spawn hot path.
- Provider absence/degradation falls back to bounded physical output rather than silently deleting a spawn contribution.

## Safety preserved

- managed persistence schema: `2`
- managed physical PDC schema: `2`
- managed spawner item schema: `2`
- schema-1 read compatibility remains intact
- redstone logical-stack lock remains intact
- migration states remain restart-safe and fail closed for unresolved spawner ownership
- WildStacker spawner stacking/upgrades are not re-enabled
- nearby logical cap remains enabled by default at `99`
- no global world entity scans or per-entity/per-spawner schedulers

## Configuration

Configuration schema advances to `9`.

New bundled defaults:

```yaml
managed:
  stacking:
    auto-stack:
      nearby:
        enabled: true
        radius: 1
    spawning:
      entity-aggregation:
        enabled: true
        radius: 8.0
        prefer-existing-stack: true
        backend: AUTO
```

Upgrades are additive: an existing administrator-set `nearby.enabled: false` is not overwritten. See `docs/MIGRATION_3_4.md`.

## WildStacker production responsibility split

Keep:

```text
WildStacker entity stacking: ON
WildStacker spawner stacking: OFF
WildStacker spawner upgrades: OFF
WildStacker linked-spawner entities: OFF
PlexonSpawners native spawner stacking: ON
PlexonSpawners spawn contribution orchestration: ON
PlexonSpawners Essence: ON
```

## Release evidence

The stable release workflow rebuilds the exact `release/stable == main` source, requires a clean test suite and distribution verification, generates provenance/checksum evidence, creates immutable-intent tag `v3.4.0`, publishes the stable GitHub release, downloads all published assets again and verifies the public JAR checksum.

Live PlexonCraft runtime certification is intentionally reported separately from GitHub source certification.
