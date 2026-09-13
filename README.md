# PlexonSpawners 4.0

PlexonSpawners is the PlexonCraft-specific policy layer around **WildStacker**.

## Ownership model

WildStacker is a required dependency and the single authority for:

- placed spawner stacking and logical stack amounts;
- spawner merge/unstack behavior;
- spawner item representation and placement;
- mob/entity stacking;
- dropped-item stacking;
- stack limits, merge radius, persistence, and stack display behavior.

PlexonSpawners owns only two player-facing policies:

1. **Spawner recovery:** qualifying Silk Touch recovers WildStacker's authoritative spawner item; an unqualified player break awards configured Spawner Essence instead.
2. **Spawner withdrawal:** right-click a WildStacker-managed spawner to withdraw logical units through a small GUI. The final logical spawner remains represented by the physical block and must be removed by breaking it.

There is no Plexon-native stack registry, stack database, placement engine, entity aggregation backend, fallback stack implementation, tier/upgrades system, ownership/access model, redstone stack lock, or stack persistence loop in 4.0.

## Requirements

- Paper 26.2
- Java 25
- WildStacker compatible with API `2026.2` (production build must be verified during runtime certification)

WildStacker is declared under `depend`, not `softdepend`; PlexonSpawners does not start without it.

## Commands

```text
/pspawners status
/pspawners reload
```

## Permissions

```text
plexonspawners.admin
plexonspawners.admin.status
plexonspawners.admin.reload
plexonspawners.bypass.silk
plexonspawners.gui
```

## Important WildStacker break-policy requirement

PlexonSpawners observes WildStacker's public `SpawnerUnstackEvent` to obtain the exact logical amount successfully removed and `SpawnerDropEvent` to enforce the recovery output. WildStacker's production break settings must therefore allow non-Silk player breaks to reach WildStacker's unstack pipeline; if WildStacker itself rejects a non-Silk break before unstacking, PlexonSpawners cannot convert that rejected operation into Essence without reimplementing WildStacker's break engine.

Do not guess configuration keys from another WildStacker version. Inspect and back up the exact production WildStacker configuration before cutover.

## 3.x upgrade

4.0 intentionally deletes the 3.x native stacking architecture. Read [`docs/MIGRATION_4_0.md`](docs/MIGRATION_4_0.md) before replacing a production JAR. Live 3.x managed-stack quantities must be inventoried and reconciled against WildStacker before old data is retired.

On first 4.0 startup, a legacy configuration file is backed up as `config-pre-4.0-backup.yml` and reset to the clean schema. Retained break/Essence policy values are copied where safe; removed stack-system sections are not carried forward.

## Build

```bash
gradle clean test check jar --no-daemon
```

The distribution verification task requires the new 4.0 classes and fails if banned 3.x stack classes or shaded WildStacker classes are present.

## Stable release gate

`v4.0.0` must not be published from CI evidence alone. The real PlexonCraft host must pass migration reconciliation (if needed), WildStacker ownership checks, Silk/Essence tests, withdrawal abuse tests, coexistence checks, and TPS/MSPT comparison first.
