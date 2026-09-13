# Changelog

## 4.0.0 — WildStacker Authority + Policy Administration

### Architecture
- WildStacker remains the hard dependency and sole authority for spawner/entity/item stacking, placement, persistence, merging, quantities and authoritative spawner items.
- PlexonSpawners continues to use the pinned public `WildStackerAPI:2026.2` directly; no reflection bridge or shaded WildStacker runtime is introduced.
- The removed 3.x native stack registry/database, physical fallback, entity aggregation, tiers/upgrades, redstone lock and duplicate persistence remain absent.

### Administrator experience
- Added `/pspawners admin` with `plexonspawners.admin.gui`.
- Added a 27-slot policy dashboard with dedicated break, Essence, custom reward, mob override, withdrawal, world and message screens.
- Admin changes live in isolated draft sessions instead of saving config on every click.
- Added config revision protection so one administrator cannot silently overwrite another administrator's later save/reload.
- Added full-draft validation, timestamped config backups, temporary-file writes, atomic replace where supported, runtime reload verification and rollback-oriented failure handling.
- Added sanitized held-item reward templates that copy supported visual fields without retaining arbitrary foreign PDC.

### Rewards
- Added explicit non-Silk modes: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, `NONE`.
- Added configurable custom non-Silk reward items with stable PlexonSpawners PDC identity.
- Essence and custom rewards roll independently once per exact logical spawner unit reported by WildStacker and aggregate successful rewards before delivery.
- Added inventory/ground delivery, overflow handling and long-based aggregation.
- Added per-mob reward-mode, Essence and custom reward overrides with inheritance/reset behavior.
- Creative recovery, Essence and custom rewards are explicit independent policy flags.

### Existing behavior preserved
- Qualifying Silk Touch recovery still uses WildStacker's authoritative `getDropItem(amount)` result.
- Player withdrawal still resolves WildStacker state live, revalidates every click, uses public unstack APIs and preserves the final logical unit.
- No reward is granted by the withdrawal GUI.

### Configuration
- Schema bumped from 10 to 11.
- Added a targeted 10→11 migration that preserves existing 4.0 break/Essence/world/withdrawal settings rather than invoking the pre-4.0 reset.
- Old Essence-enabled behavior maps to non-Silk mode `ESSENCE`; disabled Essence maps to `NONE` when no explicit mode exists.

### Validation and release
- Expanded architecture, break-decision, reward-roll, config-migration and draft-session tests.
- Stable publication remains gated by real PlexonCraft migration/runtime certification.

Historical 3.x details remain in the prior release notes and legacy changelog files.
