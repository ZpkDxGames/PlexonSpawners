# Migrating to PlexonSpawners 3.3.0

PlexonSpawners 3.3.0 changes the authority for **spawner stacking**. Read this before replacing an existing 3.1/3.2 installation that uses WildStacker.

## Responsibility after 3.3

PlexonSpawners owns physical managed spawners and their logical stack amount. WildStacker may remain installed for mob/entity stacking, but it must not remain a second long-term spawner-stack authority.

```text
Spawner stacking provider = PLEXON
Entity stacking provider  = WILDSTACKER (optional)
```

Do not intentionally run both logical spawner stack systems after migration.

## Before upgrading

1. Stop the server cleanly.
2. Back up the server, especially `plugins/PlexonSpawners/managed-spawners.db`, `config.yml`, worlds, and WildStacker data/configuration.
3. Keep WildStacker installed for the first 3.3 startup if existing managed spawners may currently have WildStacker amounts greater than one.
4. Replace the PlexonSpawners JAR with `PlexonSpawners-3.3.0.jar`.
5. Do not delete the existing PlexonSpawners data folder or `config.yml`.

## First startup

3.3 reads schema-1 managed records and treats them as Plexon `x1` records with migration state `PENDING`. For each loaded managed spawner, Plexon then checks WildStacker's public spawner amount if WildStacker is available.

If WildStacker reports `x1`, the record is marked migrated. If it reports `xN` where `N > 1` and the amount is within the configured native maximum, Plexon performs a two-phase handoff:

1. persist Plexon `stackAmount=N` with migration state `MIGRATING`;
2. force a synchronous persistence barrier;
3. normalize the WildStacker physical spawner representation down to one provider unit through its supported public unstack operation;
4. verify WildStacker now reports x1;
5. persist migration state `MIGRATED`.

This ordering prevents a restart from multiplying the same imported amount repeatedly. A partially completed handoff remains `MIGRATING` and resumes normalization instead of importing the amount again.

## Verify migration

Run:

```text
/pspawners migration status
```

Also review:

```text
/pspawners status
```

Verify:

- migration conflicts = 0;
- native stack counts match the previous logical spawner counts;
- the largest stack is plausible;
- entity stacking shows WildStacker ready when WildStacker is expected;
- no stack unexpectedly became x1 or multiplied;
- tier, owner and access policy are unchanged.

If a migration is unsafe or cannot be normalized, Plexon marks that spawner `CONFLICT`, logs its exact coordinates, and blocks stack-sensitive mutation rather than guessing. After correcting the provider/configuration issue, use:

```text
/pspawners migration retry
```

## Final WildStacker configuration

After all loaded/relevant managed spawners are migrated and diagnostics are clean:

1. disable WildStacker **spawner stacking**;
2. disable WildStacker **spawner upgrades / spawner upgrade GUI**;
3. keep WildStacker **entity stacking** enabled if desired;
4. restart the server cleanly;
5. verify `/pspawners status` and `/pspawners migration status` again.

PlexonSpawners native stacking does not require WildStacker to remain installed.

## New native-stack defaults

The following section is materialized into upgraded configuration files without replacing existing administrator values:

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
        enabled: false
        radius: 1
    compatibility:
      require-same-entity-type: true
      require-same-owner: true
      require-same-tier: true
      require-same-access-mode: true
    display:
      enabled: true
      hide-title: false
      hide-single: true
    breaking:
      normal-break:
        mode: ONE
      sneak-break:
        mode: ALL
    withdraw:
      enabled: true
      presets: [1, 8, 16]
    spawning:
      scale-with-stack: true
      mode: BOUNDED_LINEAR
      max-logical-output-per-cycle: 64
      respect-nearby-logical-cap: true
      vanilla-physical-output-cap: 16
```

Set `managed.stacking.display.hide-title: true` to keep native stack behavior but suppress floating titles completely.

## Runtime validation checklist

Before treating a production installation as runtime-certified, verify on Paper 26.2:

- place two compatible Zombie spawners vertically within 8 blocks -> one physical x2 stack;
- x64 target does not consume an additional placement;
- different owners do not auto-merge by default;
- different tiers do not auto-merge by default;
- x16 -> withdraw 8 -> x8 remains and eight valid tier-preserving items are delivered;
- normal break x16 -> x15 remains plus one valid item when recovery requirements are met;
- sneak-break x16 -> physical block removed and x16 value delivered safely;
- stack display updates on mutation;
- `hide-title: true` removes the title; `hide-single: true` hides x1 only;
- logical spawn output is bounded and the nearby cap remains authoritative;
- redstone power freezes the stack's single physical countdown and no output is produced;
- restart preserves stack amount, tier, owner, access, redstone state and display without duplicates;
- TPS/MSPT/entity/display counts remain stable under an active stacked farm.

## Schema / rollback

3.3 uses:

```text
config schema = 8
managed persistence schema = 2
managed physical PDC schema = 2
managed item schema = 2
```

The managed database schema changes because `stackAmount` and migration state are now persisted. Rolling back to a 3.1/3.2 binary that only understands persistence schema 1 requires restoring the pre-3.3 backup of `managed-spawners.db` and corresponding world/server state.

At the start of the 3.3 campaign, `v3.2.0-rc.1` exists only as a prerelease. The last verified stable GitHub rollback is therefore `v3.1.1` (`e9c50532ba0c227153ddb69f70a073e04d326a01`).
