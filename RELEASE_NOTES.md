# PlexonSpawners 2.3.1 — PlexonCore Integration Hotfix

PlexonSpawners 2.3.1 fixes the startup/module-registration flaw that could leave version 2.3.0 visible to PlexonCore as `Legacy/Standalone` even while PlexonCore was installed.

## PlexonCore binding

- PlexonCore 1.x is now a required runtime dependency.
- PlexonSpawners loads at `POSTWORLD` after PlexonCore's `STARTUP` initialization.
- Startup resolves `PlexonCoreAPI` from Bukkit `ServicesManager` and verifies that the service registration is owned by the real PlexonCore plugin.
- Core API `>=1.0 <2.0` is validated before gameplay initialization.
- Core module, integration, text, GUI, item, scheduler, persistence, configuration and diagnostics services are verified before PlexonSpawners registers gameplay listeners.
- Module `spawners` must be owned successfully before the plugin can reach READY.
- A stale `spawners` registration left by an older disabled PlexonSpawners instance can be reclaimed safely.
- Active or foreign module registrations are never force-removed.
- Registration/linkage failures now stop PlexonSpawners with a precise server-log error instead of silently enabling standalone mode.
- Module health and provider health are published through PlexonCore's module and integration registries.
- If PlexonCore is disabled while PlexonSpawners is active, PlexonSpawners disables itself safely rather than running detached.

## Deep-scan reliability fix

Managed spawner placement previously modified the placed `CreatureSpawner` from a `BlockPlaceEvent` listener running at `MONITOR`. 2.3.1 moves that mutation to `HIGHEST` with `ignoreCancelled = true`, preserving protection-plugin cancellation and keeping MONITOR observers read-only.

## Preserved from 2.3.0

- lifecycle-cached WildStacker integration;
- exact one-unit stacked-spawner recovery with fail-closed compatibility behavior;
- immutable gameplay settings snapshot;
- cached managed-spawner templates and O(1) PDC entity lookup;
- minimum-stack bulk Essence delivery;
- strict Silk Touch qualification and optional explicit bypass;
- existing 2.x managed item compatibility;
- public PlexonSpawners service API and synchronous recovery/Essence/placement events;
- database-free, stateless spawner runtime and no global chunk/entity scans.

## Compatibility

- Paper 26.2
- Java 25
- PlexonCore 1.0.0 / API 1.x **required**
- WildStacker optional
- no database migration
- no gameplay config schema reset

## Upgrade from 2.3.0

1. Stop the server completely.
2. Back up the current PlexonSpawners JAR and `plugins/PlexonSpawners/` directory.
3. Confirm `PlexonCore-1.0.0.jar` is installed and loads successfully.
4. Replace the old PlexonSpawners JAR with `PlexonSpawners-2.3.1.jar`.
5. Perform a full server start; do not use a plugin hot-reload for the first verification.
6. Run `/plexon modules` and confirm `PlexonSpawners — READY` rather than `Legacy/Standalone`.
7. Run `/pspawners diagnostics` and confirm `Mode: CORE`, Core API `1.0`, and module `READY`.
8. Validate one Silk recovery, one failed-Silk Essence outcome, one managed placement and one stacked-spawner recovery.

If startup still fails, retain the full PlexonSpawners exception from the server log. 2.3.1 intentionally exposes Core-binding failures instead of hiding them behind standalone mode.

## Release assets

- `PlexonSpawners-2.3.1.jar`
- `SHA256SUMS.txt`
