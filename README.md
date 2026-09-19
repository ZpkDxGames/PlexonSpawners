# PlexonSpawners 4.0

PlexonSpawners is the PlexonCraft recovery/reward policy and administration layer around
**WildStacker**.

## Release policy

PlexonSpawners production lines use full stable releases. The 4.0 line is `4.0.0` / `v4.0.0`,
not an RC, snapshot, preview or GitHub prerelease. See `docs/RELEASE_POLICY.md`.

## Ownership model

WildStacker is required and is the sole authority for placed spawner quantities, stacking/merging,
persistence, placement, spawner-item representation, unstacking, limits, upgrades/tiers, normal
placed-spawner interaction, entity stacking and item stacking.

PlexonSpawners owns Plexon-specific policy and integration:

- qualifying Silk recovery using WildStacker's authoritative spawner item;
- ESSENCE, CUSTOM_ITEM, ESSENCE_AND_CUSTOM_ITEM and NONE non-Silk modes;
- independent logical-unit reward rolls and per-mob overrides;
- exact custom reward ItemStack templates;
- isolated-draft administration;
- explicit Survival world scope;
- /pspawners give delegation to WildStacker;
- read-only policy API and post-commit events;
- optional thin PlexonCore 2.1 lifecycle/health/scheduling integration.

There is no Plexon native stack registry, managed stack database runtime, placement/merge engine,
entity aggregation fallback, tier/upgrade engine, redstone stack lock, stack display service,
withdrawal GUI or parallel stack persistence loop in 4.0.

## Requirements

- Paper 26.2.build.121-stable
- Java 25
- WildStacker API 2026.2, hard runtime dependency
- PlexonCore 2.1.0 optional

PlexonCore compile/test SHA-256:
7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c

## Commands

`/pspawners admin`
`/pspawners give <player> <minecraft:mobtype> <amount>`
`/pspawners status`
`/pspawners reload`

## Permissions

- plexonspawners.admin
- plexonspawners.admin.gui
- plexonspawners.admin.give
- plexonspawners.admin.status
- plexonspawners.admin.reload
- plexonspawners.bypass.silk

The Silk bypass permission defaults false and only applies when
`breaking.allow-silk-bypass-permission` is enabled.

## Schema 12

`config-version: 12` is the 4.0 schema. Dead withdrawal keys/messages are retired. Fresh installs use
an explicit Survival allowlist. Schema-11 empty legacy allowlists migrate to explicit
`scope.mode: ALL` to preserve old behavior. Future schemas fail closed.

Exact reward items use Paper byte serialization encoded as Base64.

## Administrator GUI

GUI edits are isolated drafts bound to runtime generation + config revision. Clicks do not perform
filesystem I/O. Save validates and captures an immutable draft on the primary thread, performs
backup/write/atomic replacement on bounded I/O, then commits a prepared runtime generation on the
primary thread. Revision advances only after commit succeeds. Stale sessions fail closed.

See `docs/ADMIN_GUI.md`.

## 3.x migration

4.0.0 is stable, but a production upgrade from 3.x must protect legacy quantity.

```bash
python3 tools/legacy_managed_inventory.py plugins/PlexonSpawners/managed-spawners.db \
  --csv legacy-spawners.csv --json legacy-spawners.json
```

If `old_logical_total` is nonzero, convert the old Plexon-managed quantity into
WildStacker-authoritative state before replacing the old runtime. See `docs/MIGRATION_4_0.md`.

## Build

```bash
gradle clean test check jar --no-daemon
```

The Build workflow verifies Java class major 69, schema 12, the exact PlexonCore pin, WildStacker
dependency boundaries, removed architecture absence, non-shading and source/test/distribution
evidence.

## Full stable release

`v4.0.0` is published only from an exact `main == release/stable` commit. The stable workflow
rebuilds the exact source, verifies the deterministic JAR hash and complete test suite, publishes a
non-prerelease GitHub Release and re-downloads public assets for checksum verification.

The runtime certification template is a post-release deployment checklist, not a prerelease gate.
