# PlexonSpawners 4.0.0 — WildStacker-Authoritative Architecture Reset

## What changed

WildStacker is now the required and exclusive engine for spawner stacks, spawner items, placement, mob stacks, dropped-item stacks, limits, merge radius, persistence, and generic stack display behavior.

PlexonSpawners 4.0 retains only:

- Silk Touch vs. Spawner Essence recovery policy for player breaks; and
- a lightweight right-click withdrawal GUI that reads and mutates WildStacker `StackedSpawner` state through the public API.

The legacy 3.x managed subsystem, native placement/merge logic, stack database, migration runtime, entity aggregation/fallback engine, tiers/upgrades/access state, redstone logical-stack lock, custom stack displays, and PlexonCore bridge are removed from the final runtime.

## Required deployment gate

Do not publish or install this stable release without the 3.x-to-4.0 cutover procedure in `docs/MIGRATION_4_0.md`.

Stable evidence must prove:

- production WildStacker version/config audited and backed up;
- any live Plexon-managed logical quantities reconciled into WildStacker or migration declared NOT_REQUIRED with evidence;
- WildStacker spawner/entity/item authority works after restart;
- exact Silk recovery and non-Silk Essence quantities;
- withdrawal GUI full-inventory, rapid-click, and stale-GUI safety;
- no Plexon native stack record remains;
- TPS/MSPT does not regress materially;
- public GitHub JAR checksum matches release provenance.

The stable release workflow requires an explicit `PASS` runtime-certification input and will refuse to release unless `release/stable` equals the exact `main` SHA.
