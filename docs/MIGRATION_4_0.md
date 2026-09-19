# PlexonSpawners 3.x -> 4.0 Migration

## Purpose

4.0 transfers stack/item/upgrade/interaction authority to WildStacker. The old Plexon managed stack
engine is absent from the final runtime JAR.

PlexonSpawners 4.0.0 is a full stable software release. Migration evidence is a production deployment
requirement for legacy 3.x state, not a prerelease or SemVer gate.

## Before cutover

Stop the server and preserve matching rollback copies of the 3.4 JAR, the complete
`plugins/PlexonSpawners` directory including `managed-spawners.db`, WildStacker data/config and the
relevant world/server restore point.

## Legacy inventory

The 3.4 persistence format is line-based. Schema 1 implies amount 1 per record; schema 2 stores the
logical stack amount in field 13.

```bash
python3 tools/legacy_managed_inventory.py \
  plugins/PlexonSpawners/managed-spawners.db \
  --csv migration/legacy-spawners.csv \
  --json migration/legacy-spawners.json
```

Record `managed_physical_spawners` and `old_logical_total`. The tool is read-only and rejects
unsupported headers, malformed records, duplicate IDs/locations and non-positive quantities.

## No-conversion path

If evidence proves no legacy logical quantity requires conversion, record:

```text
migration=NOT_REQUIRED
old_logical_total=0
new_logical_total=0
```

A missing file or assumption is not evidence.

## Conversion path

If `old_logical_total > 0`, do not replace the 3.x runtime with the clean 4.0 runtime until those
quantities have been converted to WildStacker authority.

The conversion mechanism must be dry-run capable, idempotent, resumable/checkpointed, fail-closed,
restart-safe and auditable per location. Preserve world UUID, coordinates, entity type, old amount,
new WildStacker amount, status and checkpoint/error evidence.

Required invariant:

```text
old_logical_total == new_logical_total
```

Restart after conversion and prove WildStacker persistence.

## Configuration migration

Pre-4.0 schemas below 10 create `config-pre-4.0-backup.yml` and rebuild into schema 12 while
preserving safe break/Essence settings. Schema 10 follows 10 -> 11 -> 12. Schema 11 creates
`config-v11-before-v12.yml`, converts explicit world scope and exact reward payloads, removes dead
withdrawal configuration/messages and advances to schema 12.

An empty old allowlist becomes explicit `ALL` to preserve prior behavior. Explicitly verify intended
Survival dimensions before opening the server to players. Future schemas fail closed.

## WildStacker audit

Verify WildStacker remains authoritative for quantities/persistence, spawner/entity/item stacking,
item representation, placement/merge/unstack, limits, tiers/upgrades and its native spawner GUI.
PlexonSpawners must not write WildStacker configuration.

## Post-release deployment validation

Use `.release/RUNTIME_CERTIFICATION_4.0.0.template` after installing the stable artifact. Validate
placement/merge persistence, singular/stacked breaks, exact reward round-trip, absence of parallel
Plexon stack state, WildStacker upgrade GUI, Survival scope, give-item recognition and a 30-minute
Spark soak.

This checklist does not change the stable release status.

## Rollback

After quantity conversion, rollback is not JAR-only. Restore the matching pre-migration 3.4 JAR,
PlexonSpawners data, WildStacker data/config and world/server restore point together.
