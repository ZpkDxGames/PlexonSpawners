# PlexonSpawners 3.x → 4.0 Migration

## Purpose

4.0 transfers all generic stacking ownership to WildStacker. The old Plexon managed-stack runtime is deleted from the final JAR. The administrator GUI and reward additions do not change that boundary.

## Before cutover

Stop before replacing the production plugin and create rollback copies of:

1. the current PlexonSpawners JAR;
2. the entire `plugins/PlexonSpawners/` directory, including legacy data;
3. the production WildStacker configuration and relevant data;
4. the world/server backup needed to restore placed spawner state.

Record Java, Paper, WildStacker, old PlexonSpawners version, TPS/MSPT baseline and candidate hashes.

## Mandatory managed-stack inventory

Inspect the 3.x persistence and loaded worlds before deleting old data. Record:

```text
managed physical spawners = ...
sum(old Plexon logical quantities) = ...
```

If both prove there is no legacy Plexon managed stack state, migration may be classified `MIGRATION NOT_REQUIRED` with evidence.

If any managed logical stack exists, do not install the clean final 4.0 JAR until quantities are converted into WildStacker-authoritative stacks. A one-shot maintenance migration may be used, but the final 4.0 runtime must not contain the legacy stack engine.

Required reconciliation:

```text
sum(old Plexon logical quantities)
==
sum(new WildStacker logical quantities)
```

Silent loss or duplication is a release blocker.

## Configuration migration

Two config paths intentionally differ:

### Legacy pre-4.0 config (`config-version < 10`)

PlexonSpawners creates `config-pre-4.0-backup.yml`, writes the clean 4.0 schema and copies only retained break/Essence policy values where safe. Removed managed/tier/upgrade/cap/migration/display/spawn-scaling/redstone sections do not return.

### Existing 4.0 config (`config-version: 10`)

Schema 10 is upgraded in place to schema 11 through a targeted migration. A `config-v10-before-v11.yml` backup is created. Existing breaking values, enabled worlds, Essence values and mob overrides, withdrawal settings/presets and unrelated keys are preserved.

Compatibility mapping when no explicit new mode exists:

```text
essence.enabled: true  -> breaking.non-silk-reward-mode: ESSENCE
essence.enabled: false -> breaking.non-silk-reward-mode: NONE
```

New custom-drop/admin/message defaults are added without sending a valid schema-10 config through the destructive pre-4.0 reset flow.

## WildStacker configuration audit

Back up and inspect the exact production WildStacker config. Verify on the installed build that:

- spawner, entity and item stacking are enabled as intended;
- intended stack limits/merge behavior are owned by WildStacker;
- placement and spawner-item representation are WildStacker's responsibility;
- non-Silk player breaks reach WildStacker's unstack pipeline so PlexonSpawners can apply reward policy;
- no WildStacker setting produces a duplicate non-Silk recovery item;
- overlapping amount/upgrade menus and spawn-egg interaction do not conflict with PlexonSpawners where the production profile expects them disabled.

PlexonSpawners does not write WildStacker configuration.

## Runtime certification

### WildStacker authority
1. Place/merge identical spawners.
2. Confirm one WildStacker logical stack and correct amount.
3. Restart and confirm persistence.
4. Prove PlexonSpawners created no parallel registry/database record.

### Silk recovery
1. Break with exact qualifying Silk Touch.
2. Confirm the exact WildStacker logical amount removed.
3. Confirm the only spawner recovery item is WildStacker's authoritative representation.
4. Place it again and confirm WildStacker recognizes/merges it.

### Non-Silk rewards
Temporarily use deterministic 100% settings for runtime proof. Verify `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, and `NONE`, including a stacked removal where `SpawnerUnstackEvent#getAmount() > 1`. Confirm one reward roll per logical unit and no recoverable spawner-item leak.

### Creative and per-mob policy
Verify the configured Creative matrix exactly. Configure one mob override and prove it differs from another mob inheriting defaults.

### Administrator GUI
1. Open `/pspawners admin` as an authorized player.
2. Change multiple values and close without saving; live runtime and disk must remain unchanged.
3. Reopen, save a valid draft, verify a timestamped backup, immediate runtime application and persistence after restart.
4. Open sessions as admins A/B, let B save, then prove A's older save is rejected as stale.

See `docs/ADMIN_GUI.md` for the full GUI acceptance matrix.

### Withdrawal regression
Test stacks `1`, `2`, `9`, and `17`; presets; all available; full inventory; rapid clicks; and a stale GUI while the stack changes. The physical block must retain one logical spawner after GUI withdrawal, and GUI withdrawal must not grant break rewards.

### Coexistence/performance
Confirm WildStacker alone stacks entities/items and controls stack state. Compare TPS/MSPT to the pre-cutover baseline and inspect task/database activity for removed 3.x loops.

## Rollback

If any gate fails, stop the server cleanly and restore the matching old JAR, PlexonSpawners data, WildStacker config, and world/data backup as required. Do not perform an ad-hoc downgrade after quantity conversion without restoring corresponding pre-migration state.

Stable publication remains blocked until runtime evidence is `RUNTIME PASS` and migration evidence is `MIGRATION PASS` or `MIGRATION NOT_REQUIRED`.
