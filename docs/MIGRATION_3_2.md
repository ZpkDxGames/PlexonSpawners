# PlexonSpawners 3.2 Migration

This document covers the `3.1.1` -> `3.2.0-rc.1` upgrade.

## Compatibility

- Paper: 26.2
- Java: 25
- PlexonCore: 2.0.4
- Configuration schema: 6 -> 7
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2

No `managed-spawners.db` conversion is required. Existing managed UUIDs, ownership, tier, access, lifetime spawn totals, provenance and item identity remain authoritative.

## New redstone controls

Schema 7 materializes:

```yaml
managed:
  redstone-lock:
    enabled: true
    poll-interval-ticks: 20
```

With `enabled: true`, direct or indirect redstone power freezes a managed spawner's live countdown. PlexonSpawners stores the pre-lock delay in memory and in the physical spawner PDC, then applies an internal hold delay. Removing power restores the frozen countdown.

`poll-interval-ticks` is bounded to `1..200`. It controls one shared reconciliation task over managed spawners in loaded chunks. It does not create one scheduler per spawner. Redstone/block events and Paper/Bukkit spawn gates provide additional immediate protection.

## WildStacker output behavior

WildStacker integration is automatic when its public API is available. No PlexonSpawners option is required to turn WildStacker's own entity stacking on.

The standard WildStacker spawner-override path now remains stack-native even when the nearby logical cap has only partial room remaining. PlexonSpawners prevents an unsafe direct merge, lets WildStacker construct its pending stacked entity, then reduces that stack to the exact remaining logical capacity before it enters the world.

Example with `maximum-amount: 99`:

- nearby logical population = 97;
- WildStacker pending output = x4;
- PlexonSpawners permits a fresh WildStacker x2 stack;
- resulting logical population = 99;
- further managed contributions are blocked until population falls below 99.

For a non-overridden/custom WildStacker path where the public event/API surface does not expose a safe pending contribution, PlexonSpawners remains conservative and can reject the whole at-risk contribution rather than knowingly exceed the cap.

## Runtime status GUI

Right-click a managed spawner and inspect **Runtime Status**. It reports:

- ticks until next spawn;
- approximate seconds until next spawn;
- redstone lock state;
- actual powered/unpowered state;
- WildStacker output state and logical spawner amount;
- logical nearby cap and radius.

Click the status item to refresh the values.

## Upgrade procedure

1. Back up the current PlexonSpawners JAR and `plugins/PlexonSpawners/`.
2. Install `PlexonSpawners-3.2.0-rc.1.jar`.
3. Start Paper 26.2 and verify PlexonCore, PlexonSpawners and WildStacker load normally.
4. Confirm `config-version: 7` and the `managed.redstone-lock` section are visible in the physical `config.yml`.
5. Verify a managed spawner shows the Runtime Status panel and a decreasing next-spawn tick count.
6. Power the spawner. Confirm the countdown freezes and no spawns occur.
7. Remove power. Confirm the same countdown resumes.
8. Verify WildStacker spawner output remains stacked, including a near-cap partial contribution such as 97 -> 99.
9. Test chunk unload/reload and a clean server restart while using the redstone lock.
10. Observe TPS/MSPT under a representative active farm before promoting the RC to stable.

## Rollback

Stable rollback is `v3.1.1` at `e9c50532ba0c227153ddb69f70a073e04d326a01`.

Rollback JAR SHA-256:

`5dadb49f91b40d24a3d4ff17acb7f2a5f96fdfb8d01854eafefb6d520ffb310c`

The managed persistence and item schemas do not change in 3.2, so source rollback is compatible. Restore the pre-upgrade plugin-data backup as well if an exact operational-state rollback is required.

GitHub source/build certification does not imply live PlexonCraft runtime certification.
