# Changelog

## 4.0.0 — WildStacker Authority + Policy Administration

### Architecture
- WildStacker remains the hard dependency and sole authority for spawner/entity/item stacking, placement, persistence, merging, quantities, upgrades/tiers and normal placed-spawner interaction.
- PlexonSpawners continues to use the pinned public `WildStackerAPI:2026.2` directly; no reflection bridge or shaded WildStacker runtime is introduced.
- The removed 3.x native stack registry/database, physical fallback, entity aggregation, tiers/upgrades, redstone lock and duplicate persistence remain absent.
- Removed the Plexon player-facing placed-spawner withdrawal GUI and its listener/holder/policy implementation so WildStacker's native spawner interaction is unobstructed.

### Administrator experience
- Added `/pspawners admin` with `plexonspawners.admin.gui`.
- Admin changes live in isolated draft sessions instead of saving config on every click.
- Added config revision protection so one administrator cannot silently overwrite another administrator's later save/reload.
- Added full-draft validation, timestamped config backups, temporary-file writes, atomic replace where supported, runtime reload verification and rollback-oriented failure handling.
- Added sanitized held-item reward templates that copy supported visual fields without retaining arbitrary foreign PDC.
- The administrator GUI remains separate from player spawner interaction; it manages Plexon-owned policy only.

### Commands
- `/pspawners give <player> <mobtype> <amount>` delegates authoritative spawner-item creation to WildStacker.
- `/pspawners admin`, `/pspawners status` and `/pspawners reload` remain available.

### Rewards
- Added explicit non-Silk modes: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, `NONE`.
- Added configurable custom non-Silk reward items with stable PlexonSpawners PDC identity.
- Essence and custom rewards roll independently once per exact logical spawner unit reported by WildStacker and aggregate successful rewards before delivery.
- Added inventory/ground delivery, overflow handling and long-based aggregation.
- Added per-mob reward-mode, Essence and custom reward overrides with inheritance/reset behavior.
- Creative recovery, Essence and custom rewards are explicit independent policy flags.

### Existing behavior preserved
- Qualifying Silk Touch recovery still uses WildStacker's authoritative `getDropItem(amount)` result.
- Break/reward policy continues to consume WildStacker's authoritative logical quantities.
- WildStacker now exclusively receives normal placed-spawner right-click interaction and may open its native tier/upgrade GUI.

### Configuration
- Schema remains `11`; no new migration exists solely for withdrawal-GUI cleanup.
- The targeted 10→11 migration remains intact.
- Existing v11 `gui:` or withdrawal-message keys are harmless legacy keys and no longer create a player placed-spawner GUI entrypoint.

### Validation and release
- Architecture contracts now prove the Plexon withdrawal GUI classes are absent, no `PlayerInteractEvent`/`RIGHT_CLICK_BLOCK` path remains, the admin GUI remains present and the distribution cannot repackage the retired classes.
- The broader 4.0 runtime candidate was accepted on PlexonCraft; stable publication still requires the focused final click-handoff smoke using the exact final CI-built JAR.
- Final campaign migration status: `NOT_REQUIRED`.

Historical 3.x details remain in the prior release notes and legacy changelog files.
