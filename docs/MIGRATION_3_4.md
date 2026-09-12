# Migrating to PlexonSpawners 3.4.0

PlexonSpawners 3.4.0 is a reliability release for native spawner stacking, spawned-entity aggregation and Essence. It preserves the 3.3 persistence/PDC/item schemas at version 2; no managed-spawner data rewrite is required.

## Responsibility split

Production should keep a single spawner authority:

```yaml
# WildStacker
entities:
  enabled: true
  stack-interval: 0
  linked-entities:
    enabled: false

spawners:
  enabled: false
```

Keep WildStacker entity stacking enabled if you want logical mob stacks. Keep WildStacker spawner stacking, spawner upgrades and linked-spawner entities disabled. PlexonSpawners owns spawner stack amount, placement merge, tiers/upgrades, break/withdraw, spawn contribution, the nearby logical cap, redstone lock and Essence.

## Configuration schema 9

The bundled 3.4 configuration uses `config-version: 9`.

New installations default to adjacent native auto-stack:

```yaml
managed:
  stacking:
    auto-stack:
      enabled: true
      vertical:
        enabled: true
        range: 8
      nearby:
        enabled: true
        radius: 1
```

Schema migration is additive. Existing administrator values are not overwritten. In particular, 3.3 shipped `managed.stacking.auto-stack.nearby.enabled: false`; PlexonSpawners cannot safely distinguish an untouched old default from an intentional administrator choice, so an existing explicit `false` remains `false`. Set it to `true` manually if you want adjacent same-level placement merge on an upgraded installation.

## Spawned-entity aggregation

3.4 adds:

```yaml
managed:
  stacking:
    spawning:
      entity-aggregation:
        enabled: true
        radius: 8.0
        prefer-existing-stack: true
        backend: AUTO
```

`AUTO` uses WildStacker's public entity API when the provider is healthy. Plexon calculates the exact logical contribution and nearby-cap allowance, selects a compatible target using bounded nearby lookup, and asks the provider-backed entity representation to apply it. If the provider is absent or degraded, output falls back to bounded physical entities subject to `vanilla-physical-output-cap`.

No world-wide entity scan, per-entity scheduler, file I/O or reflection discovery is performed on the hot spawn path. Reflection method discovery is cached at WildStacker lifecycle boundaries.

## Nearby logical cap

The nearby cap remains authoritative when a logical entity backend is healthy:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

For example, if a nearby Zombie stack is `x95`, the cap is `99`, and a managed spawner cycle requests `x8`, only `x4` is admitted.

If the WildStacker API becomes degraded at runtime, Plexon exposes the degraded provider state and uses bounded physical fallback instead of silently deleting the spawn output.

## Essence contract

Essence remains first-party Plexon functionality. WildStacker does not qualify or award Essence.

Default recovery meaning remains:

- a sufficiently qualified Silk Touch break recovers a spawner item;
- an unqualified break does not recover the spawner item;
- when Essence is enabled, the unqualified operation evaluates the configured Essence rule.

3.4 makes native logical-stack quantity explicit: **one configured Essence eligibility roll is performed per logical spawner unit removed**. Successful rewards are aggregated before ItemStacks are created.

Example: breaking ALL from an `x8` stack, with reward amount `2`, and three successful rolls produces `6` Essence total.

Delivery remains `GROUND` or `INVENTORY`; inventory overflow safely falls back to ground. Qualified recovery does not also receive failure Essence.

## Runtime verification after upgrade

Recommended in-game checks:

1. place a compatible spawner beside an existing compatible spawner and verify one physical block remains with logical amount increased;
2. test a vertical merge;
3. confirm incompatible type/owner/tier/access combinations do not consume the placed item;
4. restart and verify logical stack amount is preserved;
5. keep WildStacker entity stacking on with spawners off and verify a managed spawn contribution merges into a nearby compatible mob stack;
6. verify the `99` nearby-cap edge does not overshoot;
7. temporarily set `essence.default-chance: 100.0`, perform an unqualified break, verify the reward, then restore production configuration;
8. verify Silk-qualified recovery yields the spawner instead of failure Essence;
9. verify ONE versus ALL logical-stack breaks award using the removed logical quantity;
10. power a logical stack with redstone and verify spawn output freezes.

The GitHub release evidence certifies source, tests and distribution. Live PlexonCraft runtime certification must be recorded separately and must not be inferred from CI.
