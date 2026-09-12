# PlexonSpawners 3.1.0

`3.1.0` is the stable WildStacker-aware nearby logical stack-cap release for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

## What changed

Managed spawners now have an independently configurable local logical population guard:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

A nearby WildStacker `x99` stack counts as 99, not one physical Bukkit entity. At or above the threshold, the managed spawner contributes nothing. Below the threshold it becomes eligible automatically again.

The standard WildStacker spawner-override path is protected before its direct stack increase. Whole contributions that provably fit retain the optimized path; at-risk cycles switch to granular Paper pre-spawn gating. If a non-overridden path cannot safely express a partial pending contribution, the at-risk whole cycle is rejected instead of exceeding the cap.

The WildStacker bridge remains lifecycle-cached and fail closed. If reflective compatibility degrades at runtime, PlexonSpawners unregisters its dynamic WildStacker guard hooks immediately rather than repeatedly invoking an invalid reflective path.

The guard is local and bounded. It does not scan worlds or all loaded chunks, does not schedule a global entity scanner, performs no disk/database I/O in spawn events, and uses cached reflection only.

## Compatibility and migration

- Configuration schema: 6
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2
- Existing tier `max-nearby-entities`: unchanged and separate
- WildStacker absent: physical matching entities count as 1
- WildStacker degraded: stack-sensitive spawn decisions fail closed
- Stable rollback: `v3.0.0` at `df5ba1970add67a46dc1afc0d844578144a88df1`
- Rollback JAR SHA-256: `61978a50fcc39ccb2b025e2fe4b49ca8c28b2e849bdd9fb9d0eab9d89771e564`

See `docs/MIGRATION_3_1.md` before deployment.

## Stable verification boundary

The stable publication workflow accepts only a version without a prerelease suffix, requires `release/stable` to point to exact current `main`, rebuilds and retests that exact source, verifies Java 25/class major 69, config schema 6, required runtime classes and PlexonCore isolation, publishes four immutable release assets, then downloads and verifies the public artifacts before succeeding.

GitHub source/build certification is distinct from live PlexonCraft runtime certification. Startup, `x99` same-type pause, automatic resume below 99, different-type isolation, overlapping managed spawners, stacked spawner behavior, restart survival and farm TPS/MSPT remain operational deployment checks when live host evidence is desired.

**Runtime certification: NOT EXECUTED**
