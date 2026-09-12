# PlexonSpawners 3.1 Migration

This document covers upgrades from stable `3.0.0` to stable `3.1.0`.

## Compatibility

- Paper: 26.2
- Java: 25
- PlexonCore: 2.0.4
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2
- Configuration schema: 5 -> 6

No managed-spawner database conversion is required. Existing UUID identity, ownership, tier, access, placement time, lifetime-spawn counts and provenance remain authoritative.

## New configuration

Schema 6 adds the following keys only when they are missing:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

Existing administrator values are not replaced. Runtime parsing clamps radius to `1.0..32.0` and maximum amount to `1..1000000`.

The search shape is the axis-aligned Bukkit nearby-entity box centered on the managed spawner block. `maximum-amount` is a logical population limit: a WildStacker `x99` mob stack contributes 99. This setting is separate from each tier's existing `max-nearby-entities` value.

## WildStacker behavior

WildStacker remains optional. Without it, matching physical living entities count as one each. With it ready, PlexonSpawners uses cached reflective calls to `getEntityAmount` and `getSpawnersAmount` and lifecycle-cached public events.

The standard WildStacker `spawners.spawners-override.enabled: true` flow is the primary integration path. When the maximum possible cycle contribution fits, the optimized stacked flow is left untouched. Near the threshold, PlexonSpawners switches that cycle to a unit-granular path, cancels the direct entity-stack event, and lets Paper pre-spawn gating consume only the remaining capacity.

If WildStacker is detected but its required API is degraded, the guard fails closed. It never falls back to counting an unknown WildStacker stack as one. Dynamic WildStacker guard hooks are unregistered immediately when compatibility degrades.

On a non-overridden Bukkit spawner flow where a partial pending contribution is not safely exposed, an at-risk whole cycle is cancelled rather than knowingly overshooting the configured cap.

## Deployment

1. Stop PlexonCraft and back up `plugins/PlexonSpawners/` plus the current `PlexonSpawners-3.0.0.jar`.
2. Install the stable `PlexonSpawners-3.1.0.jar` from GitHub Releases.
3. Start Paper 26.2 and verify PlexonCore, PlexonSpawners and WildStacker load without compatibility degradation.
4. Confirm config schema 6 contains the new section with the intended local values.
5. If performing live operational certification, test same-type cap/resume, different-type isolation, overlapping managed spawners, WildStacker stacked spawners and restart persistence.
6. Observe TPS/MSPT under an active farm if production performance evidence is required.

## Rollback

Stable rollback is `v3.0.0` at `df5ba1970add67a46dc1afc0d844578144a88df1`; its JAR SHA-256 is `61978a50fcc39ccb2b025e2fe4b49ca8c28b2e849bdd9fb9d0eab9d89771e564`.

Because 3.1 does not change the managed persistence or item schemas, normal rollback is source-compatible. Restore the pre-upgrade plugin-data backup if operational testing changed production state and an exact state rollback is required.

GitHub source/build certification does not imply live runtime certification.
