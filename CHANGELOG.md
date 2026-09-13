# Changelog

## 4.0.0 — WildStacker-Authoritative Architecture Reset

### Architecture
- WildStacker is now a hard dependency and the sole authority for spawner, entity, and item stacking.
- Removed PlexonSpawners native stack persistence, registry, placement/merge engine, entity aggregation, fallback backend, stack caps, tiers, upgrades, access/ownership, redstone stack control, displays, and migration runtime.
- Removed the reflection-heavy `WildStackerCompat` architecture and PlexonCore dependency.
- Pinned the WildStacker public API to `2026.2` as `compileOnly`; no WildStacker runtime classes are shaded.

### Player behavior
- Qualifying Silk Touch recovery uses WildStacker's authoritative spawner item.
- Non-Silk player removal awards Spawner Essence instead of a recoverable spawner item.
- Essence runs one configured eligibility roll per exact logical spawner removed and aggregates delivery.
- Added a compact right-click withdrawal GUI using `StackedSpawner#getDropItem(amount)` and `runUnstack(amount, player)`.
- The GUI never withdraws the final logical spawner represented by the physical block.

### Safety and performance
- Added live-state revalidation for every withdrawal click, stale-GUI rejection, rapid-click safety, and inventory overflow handling.
- Removed repeating stack persistence, per-spawner work, nearby entity aggregation, periodic GUI refresh, and gameplay-path reflection.
- Added negative distribution assertions preventing legacy 3.x stack classes from returning to the runtime JAR.

### Migration
- Legacy config is backed up and reset to schema 10.
- Production 3.x managed stacks require one-time inventory/reconciliation before stable cutover; no legacy stack migration subsystem ships in the final 4.0 runtime.

Historical 3.x details remain in the prior release notes and legacy changelog files.
