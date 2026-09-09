# PlexonSpawners

Core-native spawner handling and physical Spawner Essence for the Plexon plugin family, with a complete standalone fallback when PlexonCore is absent.

## Version 2.3.0

PlexonSpawners 2.3.0 is the stable performance/reliability line built on the 2.2.0 Core/public-event contract. The release keeps the plugin deliberately small, database-free, and proportional to real spawner interactions only.

- Keeps the shared `BlockBreakEvent` path at an immediate material/settings reject for ordinary mining.
- Resolves and caches optional WildStacker compatibility at lifecycle boundaries instead of rediscovering its API on every accepted spawner break.
- Preserves fail-closed stacked-spawner behavior and exact one-unit unstacking.
- Caches per-EntityType managed spawner item templates, including exact BlockStateMeta and PDC identity.
- Resolves managed `spawner_type` PDC values through an O(1) lookup instead of scanning `EntityType.values()` on placement.
- Precomputes EntityType display names and runtime Essence rules.
- Compiles gameplay settings into an immutable runtime snapshot and resolves configured loaded-world UUIDs for cheap world checks.
- Makes public-outcome transaction UUIDs lazy and reduces repeated `Location`/permission/item allocations on accepted breaks.
- Caches Spawner Essence maximum stack size and performs bounded minimum-stack bulk inventory delivery.
- Warns about dangerous high physical ground-drop configurations without silently reducing the configured logical Essence award.
- Expands `/pspawners diagnostics` with integration/cache/runtime state.

## Core modes

With a compatible PlexonCore 1.x runtime installed, `/plexon modules` should report `PlexonSpawners — READY` and `/pspawners diagnostics` should report `Mode: CORE`.

Without Core, the complete spawner feature set remains operational in `STANDALONE` mode. No database, polling, or per-break Core lookup is introduced.

## Gameplay contract

Qualifying Silk Touch breaks can drop a typed PlexonCraft-styled spawner item. Failed qualification can roll a configurable chance to produce physical **Spawner Essence**. Global Essence chance and amount can be overridden independently for individual mob types.

Spawner Essence remains a configurable exact ItemStack secured with the `spawner_essence` PDC identity. Managed spawners retain `managed_spawner` and `spawner_type` PDC identity and restore their entity type when placed. New 2.3.0 managed items may also carry an internal `spawner_schema` marker; existing 2.x managed items without it remain readable.

## WildStacker compatibility

With `breaking.take-ownership: true`, PlexonSpawners claims the managed break. If WildStacker owns a stack, exactly one unit is unstacked. `CANCELLED`, degraded, disabled, or otherwise unavailable compatibility outcomes fail closed: PlexonSpawners does not force-delete the stack or emit a reward event.

WildStacker discovery/class resolution occurs at plugin lifecycle boundaries. Gameplay calls reuse cached accessors rather than doing plugin/class/method discovery for every break.

## Silk Touch qualification

`breaking.required-silk-touch-level` remains authoritative for everyone, including OP/admin players. The optional bypass works only when both conditions are true:

- `breaking.allow-silk-bypass-permission: true`
- the player has `plexonspawners.bypass.silk`

The bypass permission itself is only queried when the configured Silk level is actually not met.

## Admin GUI

Open the editor with `/pspawners admin`. Existing Spawner Rules, Spawner Essence, and Mob Values administration remain available.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 1.0.0 / API 1.x optional at runtime
- WildStacker optional

## Commands

- `/pspawners admin`
- `/pspawners info`
- `/pspawners diagnostics`
- `/pspawners reload`
- `/pspawners give <player> <mob> [amount]`
- `/pspawners essence set`
- `/pspawners essence give <player> [amount]`

## Permissions

- `plexonspawners.admin`
- `plexonspawners.admin.gui`
- `plexonspawners.admin.reload`
- `plexonspawners.admin.give`
- `plexonspawners.admin.essence`
- `plexonspawners.bypass.silk`

## Public API and events

`com.plexon.spawners.api.PlexonSpawnersApi` remains registered through Bukkit `ServicesManager`.

The stable synchronous post-success events remain:

- `com.plexon.spawners.event.PlexonSpawnerRecoveredEvent`
- `com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent`
- `com.plexon.spawners.event.PlexonSpawnerPlacedEvent`

Events fire synchronously only after their corresponding successful logical outcome. See `docs/API.md`.

## Configuration and upgrades

2.3.0 keeps the conservative migration model: existing plugin data, customized messages, exact Essence item, per-mob values, world rules, Silk requirement, and customized spawner presentation are preserved. No database migration is required.

After upgrading, run `/pspawners diagnostics`, then perform controlled Silk recovery, failed-Silk Essence, managed placement, and stacked-spawner tests before production rollout.

## Performance validation

A production 2.3.0 rollout should include before/after Spark validation. Ordinary mining should show PlexonSpawners effectively absent from sustained cost; stacked-spawner recovery, Essence bursts, and managed placement should remain tightly bounded. Do not treat synthetic code changes alone as proof of MSPT improvement.

## Building

CI provisions the official `PlexonCore-1.0.0.jar` into Maven local after verifying its pinned SHA-256, then runs:

```bash
gradle clean check
```

The resulting installable artifact is `build/libs/PlexonSpawners-2.3.0.jar`.

Production publishing is verified and tag-driven. The release pipeline publishes `PlexonSpawners-2.3.0.jar` plus `SHA256SUMS.txt` for `v2.3.0` only after the release source passes the build/distribution checks.
