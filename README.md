# PlexonSpawners 4.0

PlexonSpawners is the PlexonCraft policy and administration layer around **WildStacker**.

## Ownership model

WildStacker is a required dependency and the single authority for placed spawner quantities, stacking/merging, persistence, placement, spawner item representation, unstack operations, entity stacking, item stacking, limits and generic stack runtime state.

PlexonSpawners owns only Plexon-specific policy and UX:

- qualifying Silk Touch recovery using WildStacker's authoritative spawner item;
- configurable non-Silk rewards: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, or `NONE`;
- independent per-logical-spawner reward rolls and per-mob overrides;
- a duplication-safe player withdrawal GUI;
- a draft-based administrator policy GUI with validation, backups and stale-session protection.

There is no Plexon-native stack registry, stack database, placement/merge engine, entity aggregation backend, fallback stack implementation, tier/upgrades system, ownership model, redstone stack lock, or parallel stack persistence loop in 4.0.

## Requirements

- Paper 26.2
- Java 25
- WildStacker public API `2026.2`

WildStacker is declared under `depend`, not `softdepend`; PlexonSpawners does not start without it.

## Commands

```text
/pspawners admin
/pspawners status
/pspawners reload
```

`/pspawners admin` requires a player. Status and reload support console use.

## Permissions

```text
plexonspawners.admin
plexonspawners.admin.gui
plexonspawners.admin.status
plexonspawners.admin.reload
plexonspawners.bypass.silk
plexonspawners.gui
```

## Administrator GUI

The admin interface edits an isolated draft. Clicking controls does not mutate live settings or write `config.yml`. Save validates the full draft, rejects stale revisions, creates a timestamped backup, writes through a same-directory temporary file with atomic replacement where supported, reloads runtime policy, then advances the in-memory configuration revision.

The GUI covers break/Silk policy, Creative behavior, Essence, custom non-Silk rewards, per-mob overrides, enabled worlds, withdrawal presets, message toggles, diagnostics, save/discard/reload and sanitized held-item reward templates. See [`docs/ADMIN_GUI.md`](docs/ADMIN_GUI.md).

## Reward semantics

WildStacker's `SpawnerUnstackEvent#getAmount()` is the exact logical quantity used for reward rolls. A removal of eight logical spawners produces eight Essence rolls and, in combined mode, eight independent custom-item rolls. Successful rolls are aggregated before delivery.

Silk recovery remains separate. A qualifying Silk break receives WildStacker's `StackedSpawner#getDropItem(amount)` representation and does not become a custom non-Silk item.

## Important WildStacker break-policy requirement

PlexonSpawners observes WildStacker's public `SpawnerUnstackEvent` and `SpawnerDropEvent`. Production WildStacker settings must allow non-Silk player breaks to reach that unstack pipeline. PlexonSpawners deliberately does not edit WildStacker configuration or reconstruct rejected breaks.

## Configuration migration

Schema 11 adds admin/reward settings. Existing schema 10 configurations use a targeted 10→11 migration; they are **not** sent through the pre-4.0 reset path. Existing break settings, Essence values/overrides, worlds and withdrawal presets remain intact. Old Essence-enabled behavior maps to `ESSENCE`; disabled Essence maps to `NONE` unless a mode was already explicitly configured.

For legacy 3.x migration and stack ownership cutover, read [`docs/MIGRATION_4_0.md`](docs/MIGRATION_4_0.md).

## Build

```bash
gradle clean test check jar --no-daemon
```

Distribution verification rejects restored legacy stack packages/classes and shaded WildStacker runtime classes.

## Stable release gate

`v4.0.0` must not be published from CI evidence alone. The real PlexonCraft host must pass migration reconciliation (or documented `MIGRATION NOT_REQUIRED`), WildStacker ownership checks, Silk/Essence/custom reward tests, admin/withdrawal GUI abuse tests, coexistence checks and TPS/MSPT comparison first.
