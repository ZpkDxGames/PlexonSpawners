# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. Version 3.3.0 owns managed **spawner stacking** natively while keeping WildStacker optional for **entity/mob stacking** only.

Target stable version: `3.3.0`.

Last verified stable rollback before this release: `3.1.1`.

## Native spawner stacking

One physical managed spawner represents one persisted logical Plexon stack. The default maximum is 64 and vertical auto-stack searches only a bounded local area.

```yaml
managed:
  stacking:
    enabled: true
    max-stack-size: 64
    auto-stack:
      enabled: true
      vertical:
        enabled: true
        range: 8
      nearby:
        enabled: false
        radius: 1
    compatibility:
      require-same-entity-type: true
      require-same-owner: true
      require-same-tier: true
      require-same-access-mode: true
```

Compatible placements prefer an existing managed vertical target deterministically. Different owners, tiers, entity types, or access policies do not merge by default. A full target does not consume overflow.

## WildStacker responsibility

After 3.3 migration, responsibility is deliberately split:

```text
PlexonSpawners
✓ native spawner stacking and persistence
✓ placement / merge / split / withdraw
✓ tiers and stack-aware upgrades
✓ ownership and access
✓ runtime status and redstone lock
✓ nearby logical population cap
✓ stack title/display

WildStacker (optional)
✓ entity/mob stacking
✓ logical entity amount and stack representation
✗ long-term spawner-stack authority
✗ Plexon tier/upgrades
```

WildStacker remains lifecycle-cached through its public API/reflective compatibility bridge. Normal 3.3 runtime spawning uses `ManagedSpawner.stackAmount()` as the spawner authority; WildStacker is used to count/resize logical **entity** stacks.

## Migration from WildStacker spawner stacks

Managed persistence advances to schema 2 with first-class `stackAmount` and migration state. Existing schema-1 rows load safely as native x1 records in `PENDING` state.

When WildStacker reports an existing spawner amount greater than one, Plexon first durably records that amount as `MIGRATING`, then normalizes WildStacker's spawner representation to x1 and finally marks the Plexon record `MIGRATED`. A partial restart resumes normalization instead of importing the same amount again.

Unsafe states are marked `CONFLICT` and stack-sensitive mutation fails closed.

```text
/pspawners migration status
/pspawners migration retry
```

See `docs/MIGRATION_3_3.md` before upgrading production.

## Break and withdraw semantics

Defaults:

- normal break: remove one logical spawner;
- sneak-break: remove the complete logical stack;
- control GUI withdrawal presets: 1, 8, 16, ALL;
- tier is preserved on recovered/withdrawn items;
- inventory overflow is dropped safely rather than deleted.

A normal break of x16 leaves x15 on the same physical block. Removing the final unit deletes the managed record and physical block.

## Tier upgrades

Tier remains a property of the entire logical stack. Same-tier merge is required by default.

Upgrade pricing is stack-aware:

```text
final cost = base tier upgrade cost × native Plexon stack amount
```

The GUI shows base cost, stack amount, and final cost, then re-resolves live stack/tier/access state immediately before the transaction.

## Bounded spawn scaling

Native stacks scale logical output without requiring one physical spawner or timer per logical unit.

```yaml
managed:
  stacking:
    spawning:
      scale-with-stack: true
      mode: BOUNDED_LINEAR
      max-logical-output-per-cycle: 64
      respect-nearby-logical-cap: true
      vanilla-physical-output-cap: 16
```

The requested logical contribution is:

```text
min(tier spawn-count × native stack amount, max-logical-output-per-cycle)
```

The nearby logical population cap is then applied as the final limiter. With WildStacker installed, Plexon determines the logical contribution and WildStacker represents it as a stacked entity. Without WildStacker, Plexon still functions and additionally limits physical vanilla output.

## Nearby logical stack cap

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

The check uses Bukkit's bounded nearby-entity query. An x70 and x29 Zombie entity stack count as 99 logical Zombies, not two physical entities. The tier's native `max-nearby-entities` remains a separate vanilla spawner property.

## Redstone lock

```yaml
managed:
  redstone-lock:
    enabled: true
    poll-interval-ticks: 20
```

A powered managed spawner freezes its single physical countdown and therefore pauses the entire logical stack. The frozen countdown is kept in runtime state and physical PDC and restored when power is removed. The implementation uses one shared reconciliation task, not one timer per logical spawner.

## Stack title/display

Native stack titles use Paper `TextDisplay` entities and update on state changes/chunk reconciliation rather than every tick.

```yaml
managed:
  stacking:
    display:
      enabled: true
      hide-title: false
      hide-single: true
      format: '<yellow>%entity% Spawner</yellow> <gray>x%amount%</gray>'
      show-tier: true
      tier-format: ' <dark_gray>•</dark_gray> <gray>%tier%</gray>'
      view-distance: 16.0
      vertical-offset: 1.25
```

Set `hide-title: true` to suppress every floating stack title while keeping native stacking enabled. `hide-single: true` hides titles only for x1.

## Runtime Status GUI

Right-click a managed spawner to open Spawner Control. Runtime Status refreshes automatically through one shared GUI refresh task and shows:

- native stack amount / maximum;
- next spawn ticks and approximate seconds;
- tier spawn count and stack multiplier;
- requested bounded logical output;
- cycle output cap;
- nearby logical population cap;
- redstone lock/signal state;
- WildStacker entity integration status.

The same GUI exposes access, upgrades, and stack withdrawal.

## Managed-spawner product model

A persisted managed record owns:

- stable UUID;
- world/block coordinates;
- creature type;
- owner UUID;
- tier;
- access policy;
- placement timestamp;
- lifetime attributed spawns;
- native logical stack amount;
- migration state.

The authoritative index is persisted to `plugins/PlexonSpawners/managed-spawners.db` schema 2. The physical `CreatureSpawner` carries recovery PDC schema 2. Managed item schema remains 2 and valid older schema-1 items remain readable as tier 1.

Chunk reconciliation is identity-safe: a physical block must prove the same managed UUID and exact world/block identity before persisted state is reapplied.

## Ownership and access

- `OWNER_ONLY`: owner/admin can inspect, manage and break.
- `PUBLIC_USE`: everyone may inspect/use; only owner/admin may manage or break.
- `PUBLIC`: everyone may inspect/use/break; only owner/admin may change tier/access.

Native auto-stack requires equal owner and access mode by default to prevent cross-player ownership merges.

## Physical mutation protection

Managed stacks are protected from explosions by default, and piston movement of managed spawners is cancelled. This prevents one physical block representing many logical spawners from being duplicated or destroyed by vanilla block-mutation paths.

## Spawner provenance

Accepted Bukkit `SPAWNER` spawns use the bounded managed chunk index to attribute entities to the exact managed source. Downstream Plexon plugins should consume `PlexonSpawnersApi` instead of lore checks or world-wide history scans.

## Persistence and performance

The runtime remains bounded:

- one in-memory block index;
- one chunk index;
- one shared persistence coordinator and single-thread writer;
- coalesced dirty revisions and atomic file replacement;
- one shared redstone reconciliation task;
- one shared GUI refresh task;
- event-driven stack mutations and displays;
- bounded local auto-stack and nearby-entity queries;
- cached WildStacker compatibility handles;
- no one-task-per-spawner/display/GUI architecture;
- no global world entity/spawner scans in spawn processing;
- no synchronous file/database I/O in normal placement, break, or spawn listeners.

A synchronous persistence barrier is used only for the cross-plugin WildStacker ownership handoff so imported stack amount is durable before provider normalization.

## Requirements

- Paper 26.2 build 121 or compatible fork
- Java 25
- PlexonCore 2.0.4 recommended/current ecosystem baseline
- WildStacker optional; keep it only when entity stacking is desired or during legacy spawner-stack migration

## Commands

- `/pspawners admin`
- `/pspawners status`
- `/pspawners diagnostics`
- `/pspawners migration status`
- `/pspawners migration retry`
- `/pspawners reload`
- `/pspawners give <player> <mob> [amount] [tier]`
- `/pspawners essence set`
- `/pspawners essence give <player> [amount]`

## Permissions

- `plexonspawners.admin`
- `plexonspawners.admin.gui`
- `plexonspawners.admin.reload`
- `plexonspawners.admin.give`
- `plexonspawners.admin.essence`
- `plexonspawners.bypass.silk`
- `plexonspawners.bypass.access`

## Public API and events

`com.plexon.spawners.api.PlexonSpawnersApi` remains registered through Bukkit `ServicesManager`.

Synchronous public events remain:

- `PlexonSpawnerRecoveredEvent`
- `PlexonSpawnerEssenceAwardedEvent`
- `PlexonSpawnerPlacedEvent`

See `docs/API.md`.

## Schemas

Version 3.3.0 uses:

```text
config schema = 8
managed persistence schema = 2
managed physical PDC schema = 2
managed item schema = 2
```

Missing config defaults are materialized without overwriting administrator values.

## Migration documents

- `docs/MIGRATION_3_0.md`: 2.3 -> managed 3.0.
- `docs/MIGRATION_3_1.md`: logical nearby stack cap/config visibility.
- `docs/MIGRATION_3_2.md`: WildStacker stacked-output preservation/redstone runtime controls.
- `docs/MIGRATION_3_3.md`: native Plexon spawner-stack ownership and WildStacker handoff.

## Building and release verification

CI provisions the pinned PlexonCore 2.0.4 API, then runs:

```text
gradle clean test check jar --no-daemon
```

The stable artifact is:

```text
build/libs/PlexonSpawners-3.3.0.jar
```

Stable publication rebuilds exact `main` from `release/stable`, requires zero test failures/errors/skips, verifies Java class major 69, configuration schema 8, native stack classes, and dependency isolation, then publishes and re-downloads:

- `PlexonSpawners-3.3.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

GitHub source/build certification is separate from live PlexonCraft runtime certification. CI never reports live runtime PASS without real server evidence.

## Rollback

At the start of the 3.3 campaign, `v3.2.0-rc.1` existed only as a prerelease. The last verified stable rollback is therefore `v3.1.1` at `e9c50532ba0c227153ddb69f70a073e04d326a01`, JAR SHA-256 `5dadb49f91b40d24a3d4ff17acb7f2a5f96fdfb8d01854eafefb6d520ffb310c`.

Because 3.3 upgrades managed persistence to schema 2, restore the corresponding pre-3.3 server/database backup before rolling back to a binary that understands only schema 1.
