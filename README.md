# PlexonSpawners 4.0

PlexonSpawners is the PlexonCraft recovery/reward policy and administration layer around **WildStacker**.

## Ownership model

WildStacker is a required dependency and the sole authority for placed spawner quantities,
stacking/merging, persistence, placement, spawner-item representation, unstacking, limits,
upgrades/tiers, normal placed-spawner interaction, entity stacking and item stacking.

PlexonSpawners owns only Plexon-specific policy and integration:

- qualifying Silk recovery using WildStacker's authoritative spawner item;
- non-Silk reward modes: ESSENCE, CUSTOM_ITEM, ESSENCE_AND_CUSTOM_ITEM, NONE;
- independent per-logical-unit reward rolls and per-mob overrides;
- exact custom reward ItemStack templates;
- an isolated-draft administrator GUI;
- explicit Survival world scope;
- /pspawners give delegation to WildStacker;
- read-only policy API and post-commit events;
- optional thin PlexonCore 2.1 lifecycle/health/scheduling integration.

PlexonSpawners does not open a player-facing placed-spawner GUI. WildStacker's native interaction,
tier and upgrade GUI remains authoritative.

There is no Plexon native stack registry, managed stack database runtime, placement/merge engine,
entity aggregation backend, fallback stack implementation, tier/upgrade engine, redstone stack lock,
stack display service, withdrawal GUI or parallel stack persistence loop in 4.0.

## Requirements

- Paper 26.2.build.121-stable
- Java 25
- WildStacker API 2026.2, hard runtime dependency
- PlexonCore 2.1.0 optional

The Core compile/test artifact is pinned to SHA-256:

7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c

Core is never stack authority.

## Commands

/pspawners admin
/pspawners give <player> <minecraft:mobtype> <amount>
/pspawners status
/pspawners reload

/pspawners admin requires a player. Status/reload support console use. Give delegates item creation
to WildStacker.

## Permissions

- plexonspawners.admin
- plexonspawners.admin.gui
- plexonspawners.admin.give
- plexonspawners.admin.status
- plexonspawners.admin.reload
- plexonspawners.bypass.silk

The Silk bypass permission defaults to false and only applies when
breaking.allow-silk-bypass-permission is also enabled.

## Schema 12

config-version: 12 is the final 4.0 schema.

Dead withdrawal keys/messages are retired. Fresh installs use an explicit Survival allowlist.
Schema-11 empty legacy allowlists migrate to explicit scope.mode: ALL to preserve old behavior rather
than silently narrowing it; production certification must explicitly verify the intended Survival
world names/UUIDs.

Exact captured reward items use Paper byte serialization encoded as Base64. Human-readable fields
are summaries/fallback migration input once exact-data exists.

Future schemas fail closed.

## Administrator GUI

GUI edits are isolated drafts bound to runtime generation + config revision. Clicks never perform disk
I/O. Save validates and captures an immutable draft on the primary thread, performs backup/write/
atomic replacement on bounded I/O, then commits a prepared runtime generation on the primary thread.
Revision advances only after commit succeeds. Stale/competing sessions fail closed.

See docs/ADMIN_GUI.md.

## Reward semantics

WildStacker's exact logical removal amount drives one reward roll per logical unit. Combined mode
performs independent Essence and custom-item rolls. Successful rewards aggregate before delivery.

Silk recovery remains separate and uses WildStacker's authoritative getDropItem(amount) representation.

## Public API/events

PlexonSpawnersApi is registered through Bukkit ServicesManager and exposes policy/integration reads
only. It has no placement, merge, unstack or stack-amount mutation methods.

Post-commit events report finalized break, recovery and reward outcomes.

## Migration gate

Production 3.4 -> 4.0 migration status is PENDING until the old managed state is inventoried.

Use:

python3 tools/legacy_managed_inventory.py plugins/PlexonSpawners/managed-spawners.db \
  --csv legacy-spawners.csv --json legacy-spawners.json

If old_logical_total is nonzero, stable publication remains blocked until a one-shot WildStacker
conversion proves old_logical_total == new_logical_total with per-location/checkpoint evidence.

If no legacy quantity requires conversion, NOT_REQUIRED still needs live evidence. It is never inferred
from source code or CI.

See docs/MIGRATION_4_0.md.

## Build

gradle clean test check jar --no-daemon

The Build workflow verifies Java class major 69, schema 12, exact Core pin, WildStacker hard dependency,
banned legacy architecture absence, non-shading and source/test/distribution evidence.

## Stable release gate

v4.0.0 remains blocked until the exact final candidate passes the complete runtime template in
.release/RUNTIME_CERTIFICATION_4.0.0.template, including singular/stacked breaks, exact reward
round-trip, Survival scope, migration evidence and at least 30 minutes of Spark/runtime soak.

The stable workflow requires certified source SHA == main == release/stable and deterministic rebuilt
JAR SHA-256 == the exact live-tested candidate SHA-256.
