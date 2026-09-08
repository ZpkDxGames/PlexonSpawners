# PlexonSpawners

Core-native spawner handling and physical Spawner Essence for the Plexon plugin family, with a complete standalone fallback when PlexonCore is absent.

## Version 2.2.0

PlexonSpawners 2.2.0 migrates the stable 2.1.0 gameplay path to PlexonCore without moving spawner gameplay into Core.

- Registers module `spawners` against PlexonCore API `>=1.0 <2.0` when compatible Core is present.
- Continues in `STANDALONE` mode when Core is absent, disabled, incompatible, or unavailable.
- Preserves authoritative spawner-break ownership, strict Silk Touch qualification, exact physical Essence, typed managed spawners, WildStacker-safe one-unit handling, XP/Creative/world rules, and stateless architecture.
- Preserves the existing `PlexonSpawnersApi` Bukkit service.
- Adds stable post-success `Recovered`, `EssenceAwarded`, and `Placed` Bukkit events with transaction/event IDs.
- Adds `/pspawners diagnostics` plus expanded `/pspawners info` runtime diagnostics.
- Keeps PlexonCore compile-only and verifies that Core runtime classes are never shaded into the plugin JAR.

## Core modes

With a compatible PlexonCore 1.x runtime installed, `/plexon modules` should report `PlexonSpawners — READY` and `/pspawners diagnostics` should report `Mode: CORE`.

Without Core, the complete spawner feature set remains operational in `STANDALONE` mode. No database, polling, or per-break Core lookup is introduced.

## Gameplay contract

Qualifying Silk Touch breaks can drop a typed PlexonCraft-styled spawner item. Failed qualification can roll a configurable chance to produce physical **Spawner Essence**. Global Essence chance and amount can be overridden independently for individual mob types.

Spawner Essence remains a configurable exact ItemStack secured with the `spawner_essence` PDC identity. Managed spawners retain `managed_spawner` and `spawner_type` PDC identity and restore their entity type when placed.

## WildStacker compatibility

With `breaking.take-ownership: true`, PlexonSpawners claims the managed break. If WildStacker owns a stack, exactly one unit is unstacked. `CANCELLED` or unavailable compatibility outcomes do not force-delete the stack or emit reward events.

## Silk Touch qualification

`breaking.required-silk-touch-level` remains authoritative for everyone, including OP/admin players. The optional bypass works only when both conditions are true:

- `breaking.allow-silk-bypass-permission: true`
- the player has `plexonspawners.bypass.silk`

## Admin GUI

Open the editor with `/pspawners admin`. Existing Spawner Rules, Spawner Essence, and Mob Values administration remain unchanged from 2.1.0.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 1.0.0 / API 1.x optional at runtime

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

2.2.0 adds:

- `com.plexon.spawners.event.PlexonSpawnerRecoveredEvent`
- `com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent`
- `com.plexon.spawners.event.PlexonSpawnerPlacedEvent`

Events fire synchronously only after their corresponding successful logical outcome. See `docs/API.md`.

## Configuration and upgrades

The direct supported live upgrade is 2.0.2 → 2.2.0. Existing `plugins/PlexonSpawners/` data, customized messages, exact Essence item, and customized spawner presentation should be retained. The 2.1.0 conservative presentation migration remains in place.

See `docs/MIGRATION_2_2.md` for production upgrade and rollback steps.

## Building

CI provisions the official `PlexonCore-1.0.0.jar` into Maven local after verifying its pinned SHA-256, then runs:

```bash
gradle clean check
```

The resulting installable artifact is `build/libs/PlexonSpawners-2.2.0.jar`.

Production publishing is tag-driven only: `v2.2.0` builds the exact tagged source and publishes the JAR plus `SHA256SUMS.txt`.
