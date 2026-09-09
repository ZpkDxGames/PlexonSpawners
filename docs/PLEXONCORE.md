# PlexonCore Integration

PlexonSpawners 2.3.1 requires PlexonCore API `>=1.0 <2.0`.

## Module identity

```text
id: spawners
display: PlexonSpawners
required mode: CORE
required production state: READY
integration id: PLEXON_SPAWNERS
```

`Legacy/Standalone` is no longer an accepted runtime state. If PlexonCore is missing, disabled, incompatible, exposes an invalid API service, or rejects the `spawners` module registration, PlexonSpawners fails startup instead of enabling a detached gameplay runtime.

## Binding contract

Paper loads PlexonCore at `STARTUP`. PlexonSpawners declares `depend: [PlexonCore]` and loads at `POSTWORLD`.

During `onEnable`, PlexonSpawners:

1. requires the actual `PlexonCore` plugin to be enabled;
2. resolves `PlexonCoreAPI` through Bukkit `ServicesManager`;
3. verifies that the API service registration is owned by PlexonCore;
4. checks Core API `>=1.0 <2.0`;
5. verifies the module, integration, text, GUI, item, scheduler, persistence, config and diagnostics services are exposed;
6. registers module `spawners` as `STARTING`;
7. initializes its own config, item runtime, API, GUI, compatibility layer and gameplay listeners;
8. publishes `READY` only after initialization succeeds.

The plugin JAR does **not** shade PlexonCore classes. Core remains a provided runtime dependency, and CI enforces both the hard dependency metadata and Core-class isolation.

## Registration recovery

A server/plugin reload can leave an old `spawners` descriptor in Core when the old PlexonSpawners JavaPlugin instance is already disabled. 2.3.1 may reclaim only that stale self-registration.

It will never evict:

- an enabled PlexonSpawners instance;
- a descriptor owned by a different plugin name;
- a foreign module using a different owner.

If registration still cannot be obtained, startup fails with the registry rejection reason.

## Health publishing

PlexonSpawners publishes health to both:

- Core `ModuleRegistry` under `spawners`;
- Core `IntegrationRegistry` under `PLEXON_SPAWNERS`.

Startup/reload/compatibility outcomes can therefore surface as `STARTING`, `READY`, `DEGRADED`, `FAILED`, or `DISABLED` instead of silently becoming legacy.

A runtime WildStacker API linkage failure marks PlexonSpawners degraded while preserving its non-destructive fail-closed stacked-spawner behavior.

If PlexonCore itself is disabled while PlexonSpawners is active, PlexonSpawners disables itself safely. Continuing gameplay while detached from Core is deliberately unsupported in 2.3.1.

## Domain ownership

Core connectivity does not mean moving PlexonSpawners domain logic into PlexonCore. PlexonSpawners still owns its:

- spawner break/place rules;
- managed ItemStack/PDC identity;
- Spawner Essence behavior;
- public PlexonSpawners API/events;
- WildStacker compatibility;
- gameplay configuration.

PlexonSpawners remains database-free and does not create a persistent spawner-location index or background per-spawner tasks. Core services are validated as part of the shared runtime contract, but irrelevant services are not forced into the hot path.

## Diagnostics

After a full server restart, use:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/pspawners diagnostics
```

Expected state with PlexonCore 1.0.0:

```text
PlexonSpawners — READY | Core API | 2.3.1
Mode: CORE
Core API: 1.0
```

If PlexonSpawners fails to reach that state, use the startup exception as the authoritative reason; 2.3.1 no longer hides Core-binding errors behind `Legacy/Standalone`.
