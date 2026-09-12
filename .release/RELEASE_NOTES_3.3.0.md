# PlexonSpawners 3.3.0 — Native Stacking Stable Release

PlexonSpawners 3.3.0 moves logical **spawner** stacking under first-party Plexon ownership. WildStacker remains optional and supported as the **entity/mob stacking** provider only.

## Native spawner stacking

- One physical `SPAWNER` block now owns one persisted Plexon logical stack amount.
- Default maximum stack size is `64` and is configurable.
- Placement auto-merges compatible managed spawners vertically within 8 blocks by default.
- Merge targeting is deterministic and bounded; the registry uses its chunk index rather than world/chunk-wide scans.
- Default compatibility requires equal entity type, owner, tier and access mode.
- Placement can commit the compatible amount represented by the held managed-spawner ItemStack in one transaction. For example, target `x60` + held `x8` with max `64` becomes target `x64` plus a valid `x4` remainder stack when the placed chunk has room.
- Overflow is never deleted. If another physical managed stack cannot be created because of the chunk safety cap, only the merged amount is consumed and the remainder stays in hand.
- Creative placement remains non-consuming by default; `creative.consume-on-place: true` enables exact logical consumption.

## Break and withdraw

- Normal break removes one logical unit by default and leaves the physical block when units remain.
- Sneak-break removes the complete logical stack by default.
- The managed control GUI provides withdrawal actions for `1`, `8`, `16` and `ALL` when valid.
- Withdrawn/recovered items preserve the stack tier and are split safely across Minecraft item-stack limits.
- Inventory overflow is dropped safely at the player/source location rather than discarded.

## Tier integration

- Tier remains a property of the whole logical stack.
- Same-tier merging is required by default.
- Upgrade pricing now uses `base tier upgrade cost × Plexon stack amount`.
- The GUI re-resolves current stack/tier/access state before charging and shows base cost, stack amount and final cost.

## Bounded spawning and nearby logical cap

- Requested logical cycle output is `tier spawn-count × Plexon stack amount` when scaling is enabled.
- Default `BOUNDED_LINEAR` output is capped to `max-logical-output-per-cycle` (64 by default).
- Optional `LINEAR` mode removes that per-cycle ceiling while still using overflow-safe arithmetic and the nearby logical cap when enabled.
- The existing nearby logical population cap remains the final limiter.
- When WildStacker entity stacking is enabled, Plexon determines the logical contribution and WildStacker represents it as an entity stack; WildStacker spawner stacking is not runtime authority.
- Without WildStacker, native stacking remains functional and physical output is additionally bounded by `vanilla-physical-output-cap` (default 16).
- Additional vanilla physical entities are spawned with `SPAWNER` reason so first-party Plexon provenance remains intact.

## WildStacker migration

- Managed database schema advances from 1 to 2 with first-class `stackAmount` and persisted migration state.
- Existing schema-1 records remain readable as native `x1` records pending one-time reconciliation.
- For a legacy WildStacker stack, Plexon durably records the imported logical amount before normalizing WildStacker's spawner representation.
- Migration is restart-safe through `PENDING`, `MIGRATING`, `MIGRATED`, `NOT_REQUIRED` and `CONFLICT` states.
- Unsafe/unknown migration states fail closed and block stack-sensitive mutation.
- `/pspawners migration status` lists unresolved states and `/pspawners migration retry` retries conflicts.
- WildStacker is now an optional soft dependency so its public API is ready before import when installed.

After migration, recommended responsibility is:

```text
WildStacker
✓ entity stacking
✗ spawner stacking
✗ spawner upgrades

PlexonSpawners
✓ native spawner stacking
✓ tiers/upgrades
✓ ownership/access
✓ runtime status
✓ redstone lock
✓ logical caps
✓ stack display
✓ placement/break/withdraw
```

## Display and runtime UX

- Native stack titles use lightweight Paper `TextDisplay` entities and refresh only when state changes or a relevant chunk/config is reconciled.
- `managed.stacking.display.hide-title: true` removes/hides every Plexon stack title.
- `hide-single: true` suppresses x1 titles without disabling stacking.
- Runtime Status now includes native stack amount/max, spawn count, native multiplier, requested output, configured/effective cycle bounds, nearby cap, redstone state and WildStacker entity-integration state.
- Open managed-spawner GUIs refresh through one shared task rather than one scheduler per GUI.

## Safety and lifecycle

- Redstone lock continues to freeze one physical countdown for the entire logical stack.
- Managed stacks are protected from explosion destruction by default and piston movement is blocked.
- Chunk load reconciliation restores PDC state, migration state and displays without keeping chunks loaded.
- Display entities are ownership-marked and duplicate titles are removed during reconciliation.
- Stack mutations are performed on the server thread and stale GUI transactions re-resolve current state before mutation.

## Configuration and schemas

- Configuration schema: `8`.
- Managed persistence schema: `2`.
- Managed physical PDC state schema: `2`.
- Managed item schema: `2` (unchanged; old valid managed items remain readable).
- Missing 3.3 defaults are materialized while administrator values are preserved.

See `docs/MIGRATION_3_3.md` before upgrading a production server.

## Build / certification boundary

The stable GitHub release is published only after exact-source CI rebuild/test/distribution verification succeeds with zero failures, zero errors and zero skipped tests. Live PlexonCraft runtime certification is reported separately and is **not inferred from CI**.

## Rollback

The actual repository baseline at the start of this campaign has `v3.2.0-rc.1` as a prerelease, not a stable release. The last verified stable rollback remains `v3.1.1` (`e9c50532ba0c227153ddb69f70a073e04d326a01`). Because 3.3 upgrades managed persistence from schema 1 to 2, restore a pre-3.3 `managed-spawners.db`/server backup before rolling back to 3.1.1.
