# Changelog

## 3.4.0 - Stacking, Entity Aggregation & Essence Reliability

- Restored adjacent native spawner placement as an out-of-box behavior for new 3.4 configurations: `managed.stacking.auto-stack.nearby.enabled` now defaults to `true` with radius `1`.
- Preserved explicit administrator overrides during config schema-9 migration; existing 3.3 installations with an explicit `nearby.enabled: false` are not silently rewritten.
- Replaced the 3.3 spawned-entity path that could suppress WildStacker's immediate compatible merge with explicit Plexon logical-contribution orchestration and a provider-backed entity-stack contract.
- Added bounded deterministic nearby target selection: nearest compatible entity stack first, stable UUID tie-break second.
- Added cached WildStacker public-API support for logical amount inspection, provider compatibility checks, target stack growth, source stack removal and rollback-safe mutation handling.
- Removed blanket cancellation of matching WildStacker `EntityStackEvent`s so production `stack-interval: 0` remains usable while WildStacker spawner stacking stays disabled.
- Added a safe physical fallback backend for absent/degraded entity-stack providers; physical output remains bounded by `vanilla-physical-output-cap` and uses `CreatureSpawnEvent.SpawnReason.SPAWNER`.
- Kept the nearby logical population cap authoritative and added behavior tests for exact cap edges such as nearby `x95`, max `99`, requested `x8` -> admitted `x4`.
- Preserved `BOUNDED_LINEAR` and `LINEAR` as distinct modes with overflow-safe arithmetic.
- Rebuilt Essence reward decision logic around a deterministic pure policy. Native stack breaks now perform one configured eligibility roll per logical spawner unit removed and aggregate successful rewards before creating physical ItemStacks.
- Added deterministic 0%/100%/partial Essence tests, ONE-vs-ALL quantity tests, default rule/delivery tests, and production override coverage for Blaze, Creeper, Enderman, Wither Skeleton and Iron Golem.
- Preserved qualified Silk Touch recovery as mutually exclusive with failure Essence unless future configuration explicitly changes that contract.
- Preserved safe GROUND/INVENTORY Essence delivery, inventory-overflow ground fallback, PDC item identity and safe stack splitting.
- Preserved managed persistence schema `2`, physical managed-spawner PDC schema `2`, managed item schema `2`, schema-1 compatibility and restart-safe WildStacker migration states.
- Preserved redstone lock, TextDisplay stack labels, ONE/ALL break, tier-preserving withdraw, stack-aware upgrade pricing and physical mutation guards.
- Advanced configuration schema from `8` to `9` and added `managed.stacking.spawning.entity-aggregation` controls.
- Updated Java 25 / Paper 26.2 distribution verification for new 3.4 release-critical classes.
- Reset stable release provenance baseline to immutable `v3.3.0` source `737bc629d95da3acafd74c679a9d1b13a4d772db` and JAR SHA-256 `60a6af1b9db3bfb786122cf02cc3e9b8178548b6ee745e832b0140fb3304bdf5`.
- Stable publication remains source/CI certified separately from live PlexonCraft runtime certification; live runtime is reported `NOT_EXECUTED` unless real host evidence is supplied.

## Historical changelog through 3.3.0

The complete pre-3.4 changelog is preserved verbatim in [`CHANGELOG_LEGACY_THROUGH_3.3.md`](CHANGELOG_LEGACY_THROUGH_3.3.md). Historical GitHub tags and releases remain immutable.
