# PlexonSpawners 3.x → 4.0 Migration

## Purpose

4.0 transfers all generic stacking ownership to WildStacker. This is not an in-place feature-toggle upgrade. The old Plexon managed-stack runtime is deleted from the final JAR.

## Before cutover

Stop before replacing the production plugin and create rollback copies of:

1. the current PlexonSpawners JAR;
2. the entire `plugins/PlexonSpawners/` directory, including any SQLite/PDC-era data;
3. the production WildStacker configuration and relevant data;
4. the world/server backup needed to restore placed spawner state.

Record the exact Java version, Paper build, WildStacker version, old PlexonSpawners version, TPS/MSPT baseline, and current `main`/release artifact hashes.

## Mandatory managed-stack inventory

Inspect the 3.x persistence and loaded worlds before deleting old data. Produce:

```text
managed physical spawners = ...
sum(old Plexon logical quantities) = ...
```

If the total is zero and no placed spawner carries legacy Plexon managed state, migration may be classified `NOT_REQUIRED` with evidence.

If any managed logical stack exists, do not install the clean final 4.0 JAR until those quantities have been converted into WildStacker-authoritative stacks. Use a one-shot migration utility/temporary maintenance build if necessary. Do not ship that legacy conversion subsystem in final `v4.0.0`.

Reconcile:

```text
sum(old Plexon logical quantities)
==
sum(new WildStacker logical quantities)
```

Document every intentionally invalid/removed record. Silent loss and silent duplication are release blockers.

## WildStacker configuration audit

Back up the exact installed WildStacker config. Verify on the production build—not from guessed keys—that:

- spawner stacking is enabled;
- entity stacking is enabled;
- item stacking is enabled;
- intended spawner stack limits and merge radius are correct;
- placement and spawner-item representation are WildStacker's responsibility;
- player non-Silk breaks are allowed to reach WildStacker's unstack pipeline so PlexonSpawners can apply the Essence policy;
- no WildStacker setting causes a second independent recovery drop after PlexonSpawners policy is applied.

The 2026.2 public source shows that player breaking ultimately calls `StackedSpawner#runUnstack(breakAmount, player)` and then invokes the spawner provider/drop event after success. Production behavior must be verified against the installed build.

## Config reset

On first 4.0 startup, PlexonSpawners detects `config-version < 10`, creates `config-pre-4.0-backup.yml`, writes the clean schema, and copies only retained break/Essence policy values where safe. Removed `managed`, tier, upgrade, cap, migration, display, spawn-scaling, and redstone sections do not survive in the active config.

## Runtime certification

With the candidate installed on PlexonCraft:

### WildStacker authority
1. Place identical spawners within WildStacker merge range.
2. Confirm one authoritative logical stack.
3. Add more units and confirm the amount changes.
4. Restart and confirm the amount persists.
5. Prove PlexonSpawners created no parallel registry/database record.

### Silk recovery
1. Record the WildStacker logical amount.
2. Break with qualifying Silk Touch.
3. Record the exact amount removed.
4. Confirm the only recoverable spawner item is WildStacker-compatible and represents exactly that amount.
5. Place the item again and confirm WildStacker recognizes/merges it.

### Essence fallback
1. Record the logical amount.
2. Break without qualifying Silk.
3. Confirm no recoverable spawner item appears.
4. Confirm Essence roll count equals the exact logical amount WildStacker removed.
5. Relog/restart and confirm no duplication.

### Withdrawal GUI
Test stacks `1`, `2`, `9`, and `17`; presets; all available; full inventory; rapid repeated clicks; and a stale GUI while another player changes the stack. The physical block must always retain one logical spawner after GUI withdrawal.

### Coexistence/performance
Spawn mobs from stacks and drop stackable items. Confirm WildStacker alone stacks them and PlexonSpawners does not cancel/rewrite those operations. Compare TPS/MSPT to the pre-cutover baseline and inspect task/database activity for removed 3.x loops.

## Rollback

If any gate fails, stop the server cleanly and restore the matching old JAR, old PlexonSpawners data, WildStacker config, and world/data backup as required. Do not attempt an ad-hoc downgrade after a destructive quantity conversion without restoring the corresponding pre-migration state.
