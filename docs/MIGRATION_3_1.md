# PlexonSpawners 3.1 Migration

This document covers upgrades from stable `3.0.0` / `3.1.0` to stable `3.1.1`.

## Compatibility

- Paper: 26.2
- Java: 25
- PlexonCore: 2.0.4
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2
- Configuration schema: 6 (`5 -> 6` when upgrading from 3.0.0)

No managed-spawner database conversion is required. Existing UUID identity, ownership, tier, access, placement time, lifetime-spawn counts and provenance remain authoritative.

## Configuration visibility hotfix

`3.1.0` bundled the complete schema-6 defaults, but an existing sparse `config.yml` could keep those defaults implicit. Bukkit resolves missing paths through bundled defaults, so a normal configuration lookup could behave as if a value existed even when administrators could not see it in the file on disk.

`3.1.1` detects missing explicit values and materializes the bundled defaults into the physical `config.yml`. Existing values remain authoritative; the operation fills missing keys rather than replacing administrator overrides.

After first startup on 3.1.1, the file exposes the complete managed section, including:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

Runtime parsing clamps radius to `1.0..32.0` and maximum amount to `1..1000000`.

The search shape is the axis-aligned Bukkit nearby-entity box centered on the managed spawner block. `maximum-amount` is a logical population limit: a WildStacker `x99` mob stack contributes 99. This setting is separate from each tier's existing `max-nearby-entities` value.

## WildStacker behavior

WildStacker remains optional. Without it, matching physical living entities count as one each. With it ready, PlexonSpawners uses cached reflective calls to `getEntityAmount` and `getSpawnersAmount` and lifecycle-cached public events.

The standard WildStacker `spawners.spawners-override.enabled: true` flow is the primary integration path. When the maximum possible cycle contribution fits, the optimized stacked flow is left untouched. Near the threshold, PlexonSpawners switches that cycle to a unit-granular path, cancels the direct entity-stack event, and lets Paper pre-spawn gating consume only the remaining capacity.

If WildStacker is detected but its required API is degraded, the guard fails closed. It never falls back to counting an unknown WildStacker stack as one. Dynamic WildStacker guard hooks are unregistered immediately when compatibility degrades.

On a non-overridden Bukkit spawner flow where a partial pending contribution is not safely exposed, an at-risk whole cycle is cancelled rather than knowingly overshooting the configured cap.

## Deployment

1. Stop PlexonCraft and back up `plugins/PlexonSpawners/` plus the current JAR.
2. Install stable `PlexonSpawners-3.1.1.jar` from GitHub Releases.
3. Start Paper 26.2 and verify PlexonCore, PlexonSpawners and WildStacker load without compatibility degradation.
4. Re-open `plugins/PlexonSpawners/config.yml` and confirm the complete schema-6 defaults have been materialized while your existing overrides remain unchanged.
5. Confirm `managed.nearby-stack-cap` contains the intended local values.
6. If performing live operational certification, test same-type cap/resume, different-type isolation, overlapping managed spawners, WildStacker stacked spawners and restart persistence.
7. Observe TPS/MSPT under an active farm if production performance evidence is required.

## Rollback

Stable rollback for the 3.1.1 hotfix is `v3.1.0` at `8aaf1b7078edf5e9076af02035d204e123b3958a`; its JAR SHA-256 is `00281428501747d3ae16304a5e376006bde01a81f69181d4c327a65c71506d65`.

Because 3.1.1 does not change the managed persistence or item schemas, normal rollback is source-compatible. Restoring 3.1.0 will not remove keys already materialized into `config.yml`; restore the pre-upgrade plugin-data backup if an exact configuration-file rollback is required.

GitHub source/build certification does not imply live runtime certification.
