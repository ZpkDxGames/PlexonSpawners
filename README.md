# PlexonSpawners

PlexonCore-native spawner handling and physical Spawner Essence for the Plexon plugin family.

## Version 2.3.1

PlexonSpawners 2.3.1 is a startup/reliability hotfix for the 2.3 performance line. It fixes the condition where PlexonSpawners could be enabled as `Legacy/Standalone` even though PlexonCore was installed, leaving the plugin outside the Core module registry.

### Core integration hotfix

- PlexonCore 1.x is now a required runtime dependency, not an optional soft dependency.
- PlexonSpawners loads `POSTWORLD`, after PlexonCore's `STARTUP` initialization.
- Startup resolves the registered `PlexonCoreAPI` service and verifies that the service is owned by the actual PlexonCore plugin.
- Startup validates the Core module, integration, text, GUI, item, scheduler, persistence, configuration, and diagnostics services before gameplay listeners are registered.
- Module `spawners` must be successfully registered with Core before PlexonSpawners reaches READY.
- A stale `spawners` descriptor left by an old disabled PlexonSpawners instance can be reclaimed safely; active/foreign registrations are never evicted.
- Module lifecycle health is published as STARTING / READY / DEGRADED / FAILED / DISABLED instead of silently dropping to standalone operation.
- PlexonSpawners also publishes its provider/capability health through Core's integration registry.
- If PlexonCore becomes unavailable while PlexonSpawners is running, PlexonSpawners disables itself rather than continuing detached from Core.
- Core registration/linkage failures now fail startup with an explicit server-log error instead of appearing as `Legacy/Standalone`.

### Additional deep-scan fix

Managed spawner placement no longer mutates a `BlockPlaceEvent` at `MONITOR` priority. The listener now runs at `HIGHEST` with `ignoreCancelled = true`, preserving protection-plugin cancellation while avoiding world mutation from a monitor observer.

## Performance line retained from 2.3.0

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

## Expected Core state

With PlexonCore 1.0.0 / API 1.x installed and healthy:

```text
/plexon modules
PlexonSpawners — READY | Core API | 2.3.1

/pspawners diagnostics
Mode: CORE
Core API: 1.0
Module: READY
```

`Legacy/Standalone` is **not** a valid 2.3.1 production state. If Core cannot be bound, PlexonSpawners intentionally fails closed and leaves a precise startup error in the server log.

## Gameplay contract

Qualifying Silk Touch breaks can drop a typed PlexonCraft-styled spawner item. Failed qualification can roll a configurable chance to produce physical **Spawner Essence**. Global Essence chance and amount can be overridden independently for individual mob types.

Spawner Essence remains a configurable exact ItemStack secured with the `spawner_essence` PDC identity. Managed spawners retain `managed_spawner` and `spawner_type` PDC identity and restore their entity type when placed. New 2.3.x managed items may also carry an internal `spawner_schema` marker; existing 2.x managed items without it remain readable.

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
- PlexonCore 1.0.0 / API 1.x **required**
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

`com.plexon.spawners.api.PlexonSpawnersApi` remains registered through Bukkit `ServicesManager` after Core binding succeeds.

The stable synchronous post-success events remain:

- `com.plexon.spawners.event.PlexonSpawnerRecoveredEvent`
- `com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent`
- `com.plexon.spawners.event.PlexonSpawnerPlacedEvent`

Events fire synchronously only after their corresponding successful logical outcome. See `docs/API.md`.

## Configuration and upgrades

2.3.1 changes no gameplay configuration schema. Existing plugin data, customized messages, exact Essence item, per-mob values, world rules, Silk requirement, and customized spawner presentation are preserved. No database migration is required.

After replacing the JAR, perform a **full server restart** rather than a plugin reload. Then run `/plexon modules` and `/pspawners diagnostics` before testing Silk recovery, failed-Silk Essence, managed placement, and stacked-spawner recovery.

## Performance validation

The Core-binding hotfix does not claim an MSPT improvement by itself. The 2.3.0 hot-path optimizations remain intact; production performance should still be validated with Spark under ordinary mining, stacked-spawner recovery, Essence bursts, and managed placement.

## Building

CI provisions the official `PlexonCore-1.0.0.jar` into Maven local after verifying its pinned SHA-256, then runs:

```bash
gradle clean check
```

The resulting installable artifact is `build/libs/PlexonSpawners-2.3.1.jar`.

The distribution verifier also checks that `plugin.yml` hard-depends on PlexonCore and that PlexonCore runtime classes are not shaded into the module JAR.
