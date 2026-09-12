# PlexonSpawners 3.2.0-rc.1

`3.2.0-rc.1` is the WildStacker runtime-compatibility, live-status and redstone-lock candidate for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

## WildStacker output

Managed spawners now preserve WildStacker's stacked output through the exact nearby logical-cap path. A near-cap cycle no longer intentionally degrades into loose one-by-one mobs: PlexonSpawners blocks an unsafe direct merge, allows WildStacker to build its pending stacked entity, and trims that logical stack to the exact remaining capacity before it enters the world.

With a cap of 99, a population of 97 and a pending x4 contribution can therefore become a WildStacker x2 output and stop at exactly 99.

The integration remains automatic and optional. If WildStacker is absent, physical entities count normally. If WildStacker is detected but its required public API degrades, stack-sensitive operations fail closed.

## Runtime status

The managed-spawner GUI now reports the current ticks (and approximate seconds) until next spawn, redstone lock/signal state, WildStacker output state and logical spawner amount, plus the configured logical cap/radius. Click the status item to refresh the live values.

## Redstone lock

Schema 7 adds:

```yaml
managed:
  redstone-lock:
    enabled: true
    poll-interval-ticks: 20
```

Powered managed spawners freeze their countdown. The exact pre-lock delay is retained in memory and physical PDC, an internal hold delay prevents ticking into a spawn, and removing power restores the frozen countdown. Chunk unload and orderly shutdown restore the physical timer, while PDC provides interrupted-runtime recovery data.

The implementation uses one shared loaded-spawner reconciliation task, redstone/block event reconciliation and final spawn-time safety gates; it does not schedule one repeating task per spawner.

## Compatibility

- Configuration schema: `7`
- Managed persistence schema: unchanged at `1`
- Managed item schema: unchanged at `2`
- Existing custom configuration values remain authoritative
- No managed database migration is required
- Stable rollback: `v3.1.1` at `e9c50532ba0c227153ddb69f70a073e04d326a01`
- Rollback JAR SHA-256: `5dadb49f91b40d24a3d4ff17acb7f2a5f96fdfb8d01854eafefb6d520ffb310c`

See `docs/MIGRATION_3_2.md` before deployment.

GitHub source/build certification is distinct from live PlexonCraft runtime certification. This RC still requires in-game validation against the production WildStacker configuration before stable promotion.

**Runtime certification: NOT EXECUTED**
