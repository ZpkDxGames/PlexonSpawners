# PlexonSpawners 2.3.1 Core Integration Audit

## Trigger

PlexonCore reported PlexonSpawners 2.3.0 as `Legacy/Standalone` instead of a registered `READY | Core API` module.

## Source findings

### 1. Silent standalone downgrade

2.3.0 treated PlexonCore as optional. Core discovery, service-linkage, API-compatibility, or module-registration problems could therefore leave PlexonSpawners enabled without a Core module registration. This made the Core dashboard symptom ambiguous and hid the original binding failure.

**2.3.1:** PlexonCore is required. Missing/disabled/incompatible/unowned Core API service or rejected module registration fails startup with the real reason.

### 2. Module registration rejection was non-fatal

A rejected `spawners` registration could leave gameplay initialization continuing while the Core registry still classified PlexonSpawners as legacy.

**2.3.1:** successful ownership of module `spawners` is a startup invariant.

### 3. Stale self-registration recovery was missing

A disabled prior PlexonSpawners JavaPlugin instance could retain the module id during reload-style lifecycle transitions.

**2.3.1:** reclaim is allowed only when the existing descriptor belongs to an older disabled PlexonSpawners instance. Enabled or foreign registrations are never evicted.

### 4. Core service contract was shallow

2.3.0 resolved the Core bridge but did not validate the complete Core API service surface before registering gameplay listeners.

**2.3.1:** startup validates module, integration, text, GUI, item, scheduler, persistence, configuration, and diagnostics services. Player-facing message rendering now consumes Core's shared text service with safe runtime placeholder insertion.

### 5. Core lifecycle loss was not fail-closed

If PlexonCore disappeared after startup, PlexonSpawners could remain active without the shared runtime it claimed to use.

**2.3.1:** disabling PlexonCore disables PlexonSpawners safely.

### 6. Placement listener mutated at MONITOR

Managed spawner placement wrote CreatureSpawner state from `BlockPlaceEvent` at MONITOR priority. MONITOR should observe, not mutate, and firing success too early can disagree with a later cancellation.

**2.3.1:** managed type application happens at HIGHEST; final uncancelled placement is observed at MONITOR, where the listener is read-only and only then publishes the success event/counter.

## Intentionally preserved

- Paper 26.2 / Java 25
- Core API range `>=1.0 <2.0`
- no PlexonCore shading
- optional WildStacker, lifecycle-cached and fail closed
- strict Silk Touch logic
- PDC managed-spawner/Essence identity
- public recovery, Essence, and placement events
- database-free/stateless spawner runtime
- no global chunk/entity scans or per-spawner repeating tasks
- 2.3.0 hot-path and Essence batching optimizations

## Verification

The 2.3.1 candidate must pass:

- Java 25 compile/check;
- Core-binding unit/metadata contract;
- JAR hard dependency on PlexonCore;
- no shaded `com/zpkdxgames/plexoncore/` classes;
- runtime JAR contract;
- whitespace check;
- checksum generation and verification.

Server validation after a full restart:

1. `/plexon modules` -> `PlexonSpawners — READY | Core API | 2.3.1`;
2. `/pspawners diagnostics` -> `Mode: CORE`, Core API `1.0`, Module `READY`;
3. Silk-qualified recovery;
4. failed-Silk Essence outcome;
5. managed spawner placement;
6. stacked-spawner recovery when WildStacker is installed;
7. protection-region cancellation test.

If startup fails, retain the full exception. 2.3.1 intentionally exposes Core binding failures instead of hiding them behind a standalone fallback.
