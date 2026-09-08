# PlexonSpawners 2.2.0

PlexonSpawners 2.2.0 makes the plugin a first-class PlexonCore module without changing the stable spawner gameplay contract established by 2.0.2/2.1.0.

## PlexonCore integration

- Compatible PlexonCore 1.x present: module `spawners` registers and reaches `READY` in `CORE` mode.
- Core absent, disabled, incompatible or unavailable: PlexonSpawners continues in safe `STANDALONE` mode.
- Core is a compile-only dependency and is never shaded into PlexonSpawners.
- Runtime integration is resolved once during lifecycle initialization; block breaks do not perform Core lookup/polling.

## Public event API

2.2.0 adds three stable synchronous Bukkit events:

- `PlexonSpawnerRecoveredEvent` after a typed managed spawner is actually produced.
- `PlexonSpawnerEssenceAwardedEvent` after the configured logical Essence amount is delivered.
- `PlexonSpawnerPlacedEvent` after a managed spawner's creature type is successfully restored on placement.

Each event includes non-empty transaction/event IDs. Break recovery and Essence outcomes from the same accepted break share one transaction ID while retaining distinct event IDs.

## Preserved gameplay

- Authoritative break ownership and HIGHEST-priority interception remain unchanged.
- `loadbefore: WildStacker` remains and stacked spawners are reduced one unit at a time.
- Silk Touch remains authoritative for OP/admin players unless both explicit bypass configuration and permission are present.
- Physical Essence remains exact-item and PDC-backed.
- Managed spawner `managed_spawner` / `spawner_type` identity remains compatible.
- XP, Creative rules, world restrictions, per-mob Essence values, admin GUI and 2.1.0 presentation are preserved.
- The plugin remains database-free and stateless.

## Diagnostics

Use `/pspawners diagnostics` (or `/pspawners info`) to inspect Core mode/version/range, module state, break/Silk/Essence settings, WildStacker status, public API registration and public event readiness.

With Core present also validate `/plexon modules`, `/plexon integrations`, and `/plexon diagnostics`.

## Upgrade

For the live 2.0.2 server:

1. Stop the server.
2. Back up `PlexonSpawners-2.0.2.jar` and `plugins/PlexonSpawners/`.
3. Replace the old JAR with `PlexonSpawners-2.2.0.jar`.
4. Keep the existing plugin data/configuration directory.
5. Start the server and run diagnostics.
6. Perform one controlled Silk recovery, no-Silk Essence, and managed placement test.

No database migration or rollback is required.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 1.0.0 / API 1.x optional at runtime

## Release assets

- `PlexonSpawners-2.2.0.jar`
- `SHA256SUMS.txt`
