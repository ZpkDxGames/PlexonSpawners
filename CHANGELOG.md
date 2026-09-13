# Changelog

## 4.0.0 — WildStacker Authority + Policy Administration

### Architecture
- WildStacker remains the hard dependency and sole authority for spawner/entity/item stacking, placement, persistence, merging, quantities, upgrades/tiers and normal placed-spawner interaction.
- PlexonSpawners continues to use the pinned public `WildStackerAPI:2026.2` directly; no reflection bridge or shaded WildStacker runtime is introduced.
- The removed 3.x native stack registry/database, physical fallback, entity aggregation, tiers/upgrades, redstone lock and duplicate persistence remain absent.
- Removed the Plexon player-facing placed-spawner withdrawal GUI and its listener/holder/policy implementation so WildStacker's native spawner interaction is unobstructed.
- Singular physical spawners now use WildStacker's valid transient `StackedSpawner` representation; cache membership is no longer treated as a validity requirement.

### Administrator experience
- Added `/pspawners admin` with `plexonspawners.admin.gui`.
- Admin changes live in isolated draft sessions instead of saving config on every click.
- Added config revision protection so one administrator cannot silently overwrite another administrator's later save/reload.
- Added full-draft validation, timestamped config backups, temporary-file writes, atomic replace where supported, runtime reload verification and rollback-oriented failure handling.
- Added sanitized held-item reward templates that copy supported visual fields without retaining arbitrary foreign PDC.
- The administrator GUI remains separate from player spawner interaction; it manages Plexon-owned policy only.

### Commands
- `/pspawners give <player> <mobtype> <amount>` delegates authoritative spawner-item creation to WildStacker through the namespaced command.
- `/pspawners admin`, `/pspawners status` and `/pspawners reload` remain available.

### Rewards and break safety
- Added explicit non-Silk modes: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, `NONE`.
- Added configurable custom non-Silk reward items with stable PlexonSpawners PDC identity.
- Essence and custom rewards roll independently once per exact logical spawner unit reported by WildStacker and aggregate successful rewards before delivery.
- Added inventory/ground delivery, overflow handling and long-based aggregation.
- Added per-mob reward-mode, Essence and custom reward overrides with inheritance/reset behavior.
- Creative recovery, Essence and custom rewards are explicit independent policy flags.
- Added a pre-removal physical break-intent snapshot so the final `1x -> 0` transition cannot lose the mob type or WildStacker recovery-item identity.
- Added exactly-once completion across `BlockBreakEvent`, `SpawnerUnstackEvent`, `SpawnerDropEvent` and next-tick reconciliation.
- Protected/unchanged breaks produce no spawner recovery, Essence or custom reward.

### Existing behavior preserved
- Qualifying Silk Touch recovery uses WildStacker's authoritative `getDropItem(amount)` representation.
- Break/reward policy consumes WildStacker's authoritative logical quantities, preferring `SpawnerUnstackEvent#getAmount()` when emitted.
- WildStacker exclusively receives normal placed-spawner right-click interaction and may open its native tier/upgrade GUI.
- No Plexon stack engine, stack persistence, placement interception or upgrade system was reintroduced.

### Configuration
- Schema remains `11`; no new migration exists for the singular transaction fix.
- The targeted 10→11 migration remains intact.
- Existing v11 `gui:` or withdrawal-message keys are harmless legacy keys and no longer create a player placed-spawner GUI entrypoint.
- Final campaign migration status: `NOT_REQUIRED`.

### Validation and release
- Architecture contracts cover transient singular resolution, physical break-intent capture, pre-removal WildStacker item snapshots and the absence of a second stack authority.
- Pure transaction tests cover exactly-once completion, singular removal, `2x -> 1x`, final `1x -> 0` and denied/unchanged reconciliation.
- Live PlexonCraft testing is still mandatory for the exact remediated candidate. The prior broad 4.0 runtime acceptance is not reusable after the singular blocker was discovered.
- Stable publication remains blocked until the exact CI-built remediated JAR passes the required singular + stacked runtime matrix.

Historical 3.x details remain in the prior release notes and legacy changelog files.
