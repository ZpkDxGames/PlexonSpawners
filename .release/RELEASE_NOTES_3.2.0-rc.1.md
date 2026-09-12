# PlexonSpawners 3.2.0-rc.1

`3.2.0-rc.1` is the WildStacker runtime-compatibility and redstone-control candidate for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

## WildStacker stacked output

PlexonSpawners now preserves WildStacker's stacked-spawner output through the exact nearby logical-cap path.

On WildStacker's standard spawner-override flow, a cycle that cannot safely merge its entire contribution into an existing target is no longer converted into loose one-by-one output. PlexonSpawners cancels the risky direct `EntityStackEvent` merge, lets WildStacker construct its fresh stacked entity, reads that pending entity's logical stack amount through the public API, and trims it with `StackedEntity#setStackAmount(...)` before Bukkit admits it to the world.

That means a local logical population at 97 with a cap of 99 can produce a WildStacker `x2` output instead of loose mobs or an overshoot. Whole contributions that fit retain WildStacker's optimized direct stack path.

WildStacker remains optional. The integration is automatic when its public API is available; there is no duplicate PlexonSpawners toggle for WildStacker's own entity-stacking setting. If the required WildStacker API degrades, stack-sensitive operations continue to fail closed.

Non-overridden/custom WildStacker paths that do not publicly expose a pending logical contribution remain conservatively whole-cycle gated rather than knowingly overshooting the cap.

## Live runtime information

The managed-spawner control GUI now includes a central **Runtime Status** panel with:

- exact current ticks until the next spawn;
- approximate seconds until the next spawn;
- redstone lock state;
- current redstone signal state;
- WildStacker automatic stacked-output status and logical spawner amount;
- configured logical population cap and radius.

Click the Runtime Status item to refresh the live values.

## Redstone spawner lock

Schema 7 adds:

```yaml
managed:
  redstone-lock:
    enabled: true
    poll-interval-ticks: 20
```

When a managed spawner receives direct or indirect redstone power, PlexonSpawners freezes its live spawn countdown instead of merely cancelling the spawned mob. The pre-lock countdown is retained in memory and physical PDC, the physical spawner is moved into a long hold delay, and the exact countdown is restored when power is removed.

The PDC copy protects the frozen countdown across normal chunk reconciliation and provides recovery data after an interrupted runtime. Chunk unload and plugin shutdown restore the physical countdown before leaving the active runtime. A shared loaded-spawner reconciliation task plus redstone/block events and final spawn-time gates prevent per-spawner scheduler proliferation.

## Compatibility and migration

- Configuration schema: `7`
- Managed persistence schema: unchanged at `1`
- Managed item schema: unchanged at `2`
- No managed-spawner database migration is required
- Existing configuration overrides remain authoritative
- Stable rollback: `v3.1.1` at `e9c50532ba0c227153ddb69f70a073e04d326a01`
- Stable rollback JAR SHA-256: `5dadb49f91b40d24a3d4ff17acb7f2a5f96fdfb8d01854eafefb6d520ffb310c`

## Verification boundary

The source/CI gate verifies compilation, tests, Java 25/class major 69, Paper 26.2 metadata, config schema 7, required runtime classes, PlexonCore isolation, checksum generation and release provenance.

This release candidate still requires live PlexonCraft validation with the production WildStacker configuration: stacked spawn output, near-cap trimming, automatic resume, redstone freeze/unfreeze, timer display, chunk unload/reload, restart behavior and farm TPS/MSPT.

**Runtime certification: NOT EXECUTED**
