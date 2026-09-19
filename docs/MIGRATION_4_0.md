# PlexonSpawners 3.x -> 4.0 Migration

## Purpose

4.0 transfers all stack/item/upgrade/interaction authority to WildStacker. The old Plexon managed
stack engine is absent from the final runtime JAR.

Migration evidence is separate from source/CI evidence. Stable publication accepts only:

migration=PASS

or

migration=NOT_REQUIRED

with real production evidence.

## Before cutover

Stop the server and preserve matching rollback copies of:

1. current PlexonSpawners 3.4 JAR;
2. entire plugins/PlexonSpawners directory, including managed-spawners.db;
3. WildStacker configuration/data;
4. relevant world/server restore point.

Record current Paper, Java, WildStacker, PlexonCore, source/JAR hashes and performance baseline.

## Legacy managed-state inventory

The 3.4 managed-spawners.db format is a line-based schema:

- schema 1: each record implies old logical amount 1;
- schema 2: field 13 stores stackAmount.

The repository includes a read-only, fail-closed inventory exporter:

python3 tools/legacy_managed_inventory.py   plugins/PlexonSpawners/managed-spawners.db   --csv migration/legacy-spawners.csv   --json migration/legacy-spawners.json

Record:

managed_physical_spawners=N
old_logical_total=OLD_TOTAL

The tool rejects unsupported headers, corrupt record shapes, duplicate record IDs, duplicate physical
locations and non-positive logical amounts. It never writes plugin/server state.

Also inspect the loaded worlds/PDC state needed to rule out legacy records not represented by the file.

## NOT_REQUIRED path

NOT_REQUIRED is valid only when evidence proves there is no legacy Plexon logical quantity requiring
conversion.

The stable release workflow requires:

old_logical_total=0
new_logical_total=0

for NOT_REQUIRED.

A missing assumption is not evidence.

## PASS path

If OLD_TOTAL > 0, do not install the final clean 4.0 runtime until a separate one-shot conversion
mechanism has migrated the old quantities to WildStacker authority.

The conversion artifact/tool must remain separate from the final runtime and must be:

- dry-run capable;
- idempotent;
- resumable/checkpointed;
- fail-closed;
- restart-safe;
- auditable per location.

Per-location evidence must include:

- world UUID;
- x/y/z;
- entity type;
- old Plexon amount;
- resulting WildStacker amount;
- status;
- checkpoint/transaction ID;
- unresolved error, if any.

Required invariant:

old_logical_total == new_logical_total

Then restart and prove the new WildStacker amounts persist.

No silent loss or duplication is acceptable.

## Configuration migration

### Pre-4.0 schemas below 10

PlexonSpawners creates config-pre-4.0-backup.yml, writes clean schema 12 and preserves only safe
break/Essence settings. Legacy empty world allowlist semantics become explicit scope.mode=ALL.

### Schema 10

The supported path is 10 -> 11 -> 12. A backup is created before the conversion.

### Schema 11

A targeted 11 -> 12 migration creates config-v11-before-v12.yml, then:

- preserves active break/reward/admin/world behavior;
- converts the old world allowlist to explicit scope.mode/scope.worlds;
- converts declarative reward templates into exact serialized item payloads;
- removes the dead gui withdrawal section;
- removes dead withdrawal success/failure message keys;
- advances config-version to 12.

An empty legacy allowlist becomes explicit ALL to preserve previous behavior. This does not certify
production scope; PlexonCraft runtime certification must explicitly set and verify intended Survival
dimensions.

Future schemas fail closed.

## WildStacker production audit

Capture the exact installed WildStacker configuration and verify it remains authoritative for:

- spawner/entity/item stacking;
- quantities and persistence;
- item representation;
- placement/merge/unstack;
- limits;
- tiers/upgrades;
- native placed-spawner GUI;
- production upgrade ladder/economics.

PlexonSpawners must not write WildStacker configuration.

## Runtime certification after migration

Use .release/RUNTIME_CERTIFICATION_4.0.0.template.

At minimum verify:

- placement/merge and restart persistence;
- singular and stacked break matrix;
- exact reward item round-trip;
- no parallel Plexon registry/database;
- WildStacker native tier/upgrade GUI;
- explicit Survival scope;
- /pspawners give item recognition/merge;
- 30-minute Spark/runtime soak.

## Rollback

Rollback after quantity conversion is not JAR-only.

Stop the server and restore the matching pre-migration:

- 3.4 JAR;
- PlexonSpawners data;
- WildStacker data/config;
- relevant world/server restore point.

Do not mix pre- and post-conversion state.
