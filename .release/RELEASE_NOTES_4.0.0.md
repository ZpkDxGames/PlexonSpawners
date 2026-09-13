# PlexonSpawners 4.0.0 — WildStacker-Authoritative Policy Release

## Product boundary

WildStacker is the required and exclusive engine for spawner stacks, quantities, spawner items, placement, merging, unstacking, mob/entity stacks, dropped-item stacks, limits and persistence.

PlexonSpawners owns only Plexon-specific recovery/reward policy and its GUIs. The deleted 3.x managed stack subsystem, native placement/merge logic, stack database, entity aggregation/fallback engine, tiers/upgrades/access state, redstone logical-stack lock, custom stack displays and PlexonCore bridge remain absent.

## 4.0 administrator/reward enhancement

- `/pspawners admin` opens a permission-protected policy dashboard.
- Admin edits are isolated drafts; no click writes live config.
- Save performs validation, stale-revision rejection, timestamped backup, same-directory temporary serialization, atomic replacement where supported, runtime reload and revision advancement.
- Non-Silk modes: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, `NONE`.
- Essence and custom item schemes roll independently per exact `SpawnerUnstackEvent#getAmount()` logical unit and aggregate successful delivery.
- Per-mob mode/amount/chance overrides inherit defaults and can be reset.
- Custom reward templates receive a stable PlexonSpawners identity; held-item capture is sanitized rather than preserving arbitrary PDC.
- Existing WildStacker-authoritative Silk recovery and player withdrawal safety are retained.
- Schema 10 upgrades through a targeted schema 11 migration rather than the pre-4.0 config reset.

## Required deployment gate

Do not publish or install this as stable without the 3.x-to-4.0 cutover procedure in `docs/MIGRATION_4_0.md` and the admin/reward runtime matrix in `docs/ADMIN_GUI.md`.

Stable evidence must prove:

- production WildStacker version/config audited and backed up;
- migration classified `PASS` or `NOT_REQUIRED` from real evidence;
- WildStacker spawner/entity/item authority works across restart;
- exact Silk recovery quantities and authoritative spawner item representation;
- non-Silk Essence, custom-item, combined and none modes;
- logical-unit roll semantics for stacked spawner removals;
- Creative matrix and one per-mob override;
- administrator draft/discard/save/stale-session behavior;
- config backup creation and persistence across restart;
- withdrawal full-inventory, rapid-click and stale-GUI safety;
- no Plexon native stack state returns;
- TPS/MSPT does not regress materially;
- public release JAR checksum matches accepted source provenance.

## Current release state

Until those gates are completed on PlexonCraft:

```text
RUNTIME PENDING
MIGRATION PENDING
PR NOT OPENED
MAIN NOT MERGED
RELEASE/STABLE NOT UPDATED
V4.0.0 NOT PUBLISHED
```

CI success is candidate evidence only and must not be reported as runtime acceptance.
