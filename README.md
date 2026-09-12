# PlexonSpawners

PlexonSpawners is the first-party PlexonCraft managed-spawner system for Paper 26.2 / Java 25. Version **3.4.0** owns logical spawner stacks, placement merge, spawn contribution, nearby logical-cap enforcement, tiers/upgrades, redstone lock and Spawner Essence. WildStacker remains optional for **entity/mob stack representation only**.

Target stable version: `3.4.0`.

Stable baseline for this release: `v3.3.0` at `737bc629d95da3acafd74c679a9d1b13a4d772db`, JAR SHA-256 `60a6af1b9db3bfb786122cf02cc3e9b8178548b6ee745e832b0140fb3304bdf5`.

## 3.4 reliability boundary

3.4 corrects three regressions from the 3.3 stable line:

1. compatible spawners placed beside each other now auto-stack with the bundled defaults;
2. managed-spawner mob output can aggregate directly into an existing nearby compatible entity stack again;
3. Essence reward behavior is explicit, deterministic in tests and aware of how many logical spawner units were removed.

The 3.3 persistence boundary is preserved: managed persistence schema `2`, physical PDC schema `2`, managed item schema `2`, and schema-1 read compatibility remain unchanged.

## Native spawner stacking

One physical managed spawner represents one persisted logical Plexon stack. Default maximum is `64`.

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
        enabled: true
        radius: 1
    compatibility:
      require-same-entity-type: true
      require-same-owner: true
      require-same-tier: true
      require-same-access-mode: true
```

Compatible placement uses bounded indexed lookup and deterministic target selection. Different type, owner, tier or access mode does not merge by default. Overflow is never discarded: for example, an existing `x60` target plus held `x8` with max `64` becomes target `x64` and a safe `x4` remainder.

Existing upgraded installations retain explicit administrator values. Because Plexon cannot safely distinguish the old 3.3 shipped `nearby.enabled: false` from an intentional administrator choice, that existing explicit value is not silently rewritten. See `docs/MIGRATION_3_4.md`.

## WildStacker responsibility split

Production should keep exactly one spawner authority:

```text
PlexonSpawners
✓ native spawner stack amount and persistence
✓ placement merge / split / withdraw
✓ tiers and stack-aware upgrades
✓ ownership and access
✓ spawn contribution orchestration
✓ nearby logical population cap
✓ redstone lock
✓ Spawner Essence

WildStacker (optional)
✓ entity/mob stack representation and lifecycle
✗ spawner-stack authority
✗ spawner upgrades
✗ linked-spawner ownership
✗ Essence qualification/rewards
```

Recommended WildStacker split:

```yaml
entities:
  enabled: true
  stack-interval: 0
  linked-entities:
    enabled: false

spawners:
  enabled: false
```

3.4 does **not** restore WildStacker spawner authority to fix mob stacking.

## Direct spawned-entity aggregation

Native stacks calculate logical output from the physical/tier spawn count and Plexon's native logical spawner amount, then apply the cycle mode/cap and nearby logical remaining capacity.

```yaml
managed:
  stacking:
    spawning:
      scale-with-stack: true
      mode: BOUNDED_LINEAR
      max-logical-output-per-cycle: 64
      respect-nearby-logical-cap: true
      vanilla-physical-output-cap: 16
      entity-aggregation:
        enabled: true
        radius: 8.0
        prefer-existing-stack: true
        backend: AUTO
```

`AUTO` uses WildStacker's public entity API when healthy. Plexon searches only a bounded nearby area, chooses the nearest compatible target with a stable UUID tie-break, mutates that target through the provider API, and removes the pending source representation only after the target mutation succeeds. The 3.3 blanket `EntityStackEvent` cancellation was removed so `stack-interval: 0` remains usable.

If no compatible target exists, the newly admitted entity becomes the stack representation when the provider supports it. If WildStacker is absent or its API is degraded, Plexon falls back to bounded physical `SPAWNER` output rather than silently deleting or creating unbounded entities.

No world-wide entity scan, per-entity scheduler, per-spawner scheduler, file I/O, database I/O or reflection discovery occurs in the spawn hot path. Reflection handles are cached at provider lifecycle boundaries.

## Nearby logical population cap

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

Logical provider amounts are counted when available. If a nearby Zombie stack is `x95`, the maximum is `99`, and a cycle requests `x8`, only `x4` is admitted. The cap is not relaxed to make aggregation appear functional.

## Spawn modes

`BOUNDED_LINEAR` applies `max-logical-output-per-cycle` before the nearby cap. `LINEAR` intentionally removes that configured cycle ceiling; arithmetic saturates safely and the nearby cap remains authoritative when enabled.

## Spawner Essence

Essence is first-party Plexon functionality and does not depend on WildStacker spawner ownership.

Default recovery semantics:

- sufficiently qualified Silk Touch -> recover spawner item;
- unqualified recovery -> no spawner item;
- when Essence is enabled, evaluate the configured Essence reward instead;
- qualified recovery does not also receive failure Essence.

Default configuration:

```yaml
essence:
  enabled: true
  default-chance: 35.0
  default-amount: 1
  delivery: GROUND
```

3.4 explicitly performs **one Essence eligibility roll per logical spawner unit removed**. Successful amounts are aggregated before physical `ItemStack`s are created. An ALL break removing `x8`, with amount `2` and three successful rolls, yields `6` Essence total.

`GROUND` and `INVENTORY` delivery are supported. Inventory overflow safely falls back to ground. The Essence item keeps its PDC identity, respects maximum ItemStack size, splits large totals safely, and reloads the configured item template.

## Break, withdraw and upgrades

Defaults:

- normal break removes ONE logical unit;
- sneak-break removes ALL logical units;
- GUI withdraw presets are `1`, `8`, `16`, `ALL`;
- recovered/withdrawn units preserve tier;
- inventory overflow is dropped safely;
- tier upgrade cost is `base tier cost × logical stack amount`.

Stack-sensitive mutation remains fail-closed while migration state is `PENDING`, `MIGRATING` or `CONFLICT`.

## Migration safety

Existing 3.3 migration states remain readable:

```text
PENDING
MIGRATING
MIGRATED
NOT_REQUIRED
CONFLICT
```

A legacy WildStacker spawner amount is durably claimed before provider normalization and cannot be re-imported multiplicatively after restart. WildStacker spawner stacking should remain disabled after migration.

## Redstone lock

A powered managed physical spawner freezes the single countdown representing its entire logical stack. No logical spawn contribution or aggregation is admitted from the locked stack. Countdown recovery remains PDC-backed and one shared reconciliation task is used.

## Runtime Status GUI

Right-click a managed spawner to open Spawner Control. Existing runtime status exposes native stack amount/max, next-spawn countdown, tier spawn count, stack multiplier, requested logical output, cycle cap, nearby logical cap, redstone state and WildStacker entity integration state. The view refreshes through one shared task rather than a scheduler per GUI.

## Displays and physical mutation protection

Native stack titles use Paper `TextDisplay` entities and update on state changes/chunk reconciliation. `hide-title` and `hide-single` remain supported. Managed stacks remain protected against explosion destruction and piston movement by default.

## Persistence and performance

The runtime remains bounded:

- one in-memory block index and chunk index;
- one shared persistence coordinator and single-thread writer;
- coalesced dirty revisions and atomic replacement;
- bounded local placement and entity queries;
- cached optional-provider API handles;
- event-driven stack/display mutation;
- no global world entity/spawner scans in spawn processing;
- no synchronous file/database I/O in normal placement, break or spawn listeners.

## Requirements

- Paper 26.2 build 121 or compatible fork
- Java 25
- PlexonCore 2.0.4
- WildStacker optional for logical entity stacking; its spawner stacking/upgrades should remain disabled

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

## Public API and events

`com.plexon.spawners.api.PlexonSpawnersApi` remains registered through Bukkit `ServicesManager`.

Synchronous public events remain:

- `PlexonSpawnerRecoveredEvent`
- `PlexonSpawnerEssenceAwardedEvent`
- `PlexonSpawnerPlacedEvent`

See `docs/API.md`.

## Schemas

Version 3.4.0 uses:

```text
config schema = 9
managed persistence schema = 2
managed physical PDC schema = 2
managed item schema = 2
```

## Migration documents

- `docs/MIGRATION_3_0.md`: 2.3 -> managed 3.0
- `docs/MIGRATION_3_1.md`: logical nearby stack cap/config visibility
- `docs/MIGRATION_3_2.md`: WildStacker entity output/redstone runtime controls
- `docs/MIGRATION_3_3.md`: native Plexon spawner ownership and provider handoff
- `docs/MIGRATION_3_4.md`: adjacent placement, direct entity aggregation and stack-aware Essence

## Building and stable release verification

CI provisions the pinned PlexonCore 2.0.4 API and runs:

```text
gradle clean test check jar --no-daemon
```

The stable artifact is:

```text
build/libs/PlexonSpawners-3.4.0.jar
```

Stable publication requires `release/stable` to equal final `main`, rebuilds/retests that exact source, verifies Java class major `69`, config schema `9`, release-critical classes and dependency isolation, then publishes and re-downloads:

- `PlexonSpawners-3.4.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

The public download is checksum-verified after publication. GitHub source/build certification remains distinct from live PlexonCraft runtime certification; CI never invents a live runtime PASS.

## Rollback

The immutable stable baseline is `v3.3.0`. Never move or replace that tag or its release assets. Because 3.4 keeps the 3.3 persistence/PDC/item schemas unchanged, rollback must still respect the normal operational requirement to preserve compatible server data/backups.
