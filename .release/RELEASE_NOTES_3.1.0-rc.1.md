# PlexonSpawners 3.1.0-rc.1

`3.1.0-rc.1` is the WildStacker-aware nearby logical stack-cap candidate for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

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

The guard is local and bounded. It does not scan worlds or all loaded chunks, does not schedule a global entity scanner, performs no disk/database I/O in spawn events, and uses cached reflection only.

## Compatibility and migration

- Configuration schema: 6
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2
- Existing tier `max-nearby-entities`: unchanged and separate
- WildStacker absent: physical matching entities count as 1
- WildStacker degraded: stack-sensitive spawn decisions fail closed

See `docs/MIGRATION_3_1.md` before deployment.

## Candidate boundary

This prerelease is intended for PlexonCraft runtime verification. Required live checks include startup, `x99` same-type pause, automatic resume below 99, different-type isolation, overlapping same-type managed spawners, stacked spawner behavior, restart survival, and farm TPS/MSPT observation.

**Runtime certification: NOT EXECUTED**

Do not promote this candidate to stable based only on CI.
