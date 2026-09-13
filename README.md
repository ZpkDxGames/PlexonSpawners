# PlexonSpawners 4.0

PlexonSpawners is the PlexonCraft policy and administration layer around **WildStacker**.

## Ownership model

WildStacker is a required dependency and the single authority for placed spawner quantities, stacking/merging, persistence, placement, spawner item representation, unstack operations, entity stacking, item stacking, limits, upgrades/tiers and normal placed-spawner interaction.

PlexonSpawners owns only Plexon-specific policy and administration:

- qualifying Silk Touch recovery using WildStacker's authoritative spawner item;
- configurable non-Silk rewards: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, or `NONE`;
- independent per-logical-spawner reward rolls and per-mob overrides;
- a draft-based administrator policy GUI with validation, backups and stale-session protection;
- administrative WildStacker-compatible spawner giving through `/pspawners give`.

PlexonSpawners 4.0.0 does **not** open a player-facing GUI when a placed spawner is right-clicked. That interaction is deliberately left untouched so WildStacker's native spawner tier/upgrade interface can receive it normally.

There is no Plexon-native stack registry, stack database, placement/merge engine, entity aggregation backend, fallback stack implementation, tier/upgrades system, ownership model, redstone stack lock, or parallel stack persistence loop in 4.0.

## Requirements

- Paper 26.2
- Java 25
- WildStacker public API `2026.2`

WildStacker is declared under `depend`, not `softdepend`; PlexonSpawners does not start without it.

## Commands

```text
/pspawners admin
/pspawners give <player> <mobtype> <amount>
/pspawners status
/pspawners reload
```

`/pspawners admin` requires a player. Status and reload support console use. `/pspawners give` delegates authoritative spawner-item creation to WildStacker.

## Permissions

```text
plexonspawners.admin
plexonspawners.admin.gui
plexonspawners.admin.give
plexonspawners.admin.status
plexonspawners.admin.reload
plexonspawners.bypass.silk
```

## Administrator GUI

The admin interface edits an isolated draft. Clicking controls does not mutate live settings or write `config.yml`. Save validates the full draft, rejects stale revisions, creates a timestamped backup, writes through a same-directory temporary file with atomic replacement where supported, reloads runtime policy, then advances the in-memory configuration revision.

The supported administrator surface is for PlexonSpawners-owned break/reward policy. It is separate from player spawner interaction; WildStacker owns normal placed-spawner interaction and its tier/upgrade GUI. See [`docs/ADMIN_GUI.md`](docs/ADMIN_GUI.md).

## Reward semantics

WildStacker's `SpawnerUnstackEvent#getAmount()` is the exact logical quantity used for reward rolls. A removal of eight logical spawners produces eight Essence rolls and, in combined mode, eight independent custom-item rolls. Successful rolls are aggregated before delivery.

Silk recovery remains separate. A qualifying Silk break receives WildStacker's `StackedSpawner#getDropItem(amount)` representation and does not become a custom non-Silk item.

## Important WildStacker break-policy requirement

PlexonSpawners observes WildStacker's public `SpawnerUnstackEvent` and `SpawnerDropEvent`. Production WildStacker settings must allow non-Silk player breaks to reach that unstack pipeline. PlexonSpawners deliberately does not edit WildStacker configuration or reconstruct rejected breaks.

## Configuration migration

Schema 11 remains the 4.0 schema. Existing schema 10 configurations use the targeted 10→11 migration; there is no extra schema bump for removal of the player withdrawal GUI. Existing v11 `gui:` or withdrawal-message keys are harmless legacy keys and have no player spawner-click entrypoint in 4.0.0.

For legacy 3.x migration and stack ownership cutover, read [`docs/MIGRATION_4_0.md`](docs/MIGRATION_4_0.md).

## Build

```bash
gradle clean test check jar --no-daemon
```

Distribution verification rejects restored legacy stack packages/classes, the retired Plexon withdrawal GUI classes and shaded WildStacker runtime classes.

## Stable release gate

The broader 4.0 runtime candidate was already accepted on PlexonCraft. Before `v4.0.0` is published, the exact final CI-built JAR still requires the focused click-handoff smoke: normal spawner interaction must reach WildStacker, `/pspawners admin` and `/pspawners give` must remain functional, and one representative break/reward test must pass. Migration status for this final campaign is `NOT_REQUIRED`.
