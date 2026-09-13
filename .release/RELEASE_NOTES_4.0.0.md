# PlexonSpawners 4.0.0 — WildStacker-Authoritative Policy Release

## Product boundary

WildStacker is the required and exclusive engine for spawner stacks, quantities, spawner items, placement, merging, unstacking, mob/entity stacks, dropped-item stacks, limits, persistence, upgrades/tiers and normal placed-spawner interaction.

PlexonSpawners owns only Plexon-specific recovery/reward policy and its administrator GUI. The deleted 3.x managed stack subsystem, native placement/merge logic, stack database, entity aggregation/fallback engine, tiers/upgrades/access state, redstone logical-stack lock, custom stack displays and PlexonCore bridge remain absent.

## Final interaction handoff

PlexonSpawners no longer opens its own withdrawal interface when interacting with placed spawners. It does not cancel or consume the normal spawner right-click path. WildStacker receives the interaction normally and remains the sole owner of its native tier/upgrade GUI.

The PlexonSpawners administrator GUI remains available through `/pspawners admin` for Plexon-owned break and reward policy.

## 4.0 administrator/reward enhancement

- `/pspawners admin` opens a permission-protected policy dashboard.
- `/pspawners give <player> <mobtype> <amount>` delegates spawner-item creation to WildStacker.
- Admin edits are isolated drafts; no click writes live config.
- Save performs validation, stale-revision rejection, timestamped backup, same-directory temporary serialization, atomic replacement where supported, runtime reload and revision advancement.
- Non-Silk modes: `ESSENCE`, `CUSTOM_ITEM`, `ESSENCE_AND_CUSTOM_ITEM`, `NONE`.
- Essence and custom item schemes roll independently per exact `SpawnerUnstackEvent#getAmount()` logical unit and aggregate successful delivery.
- Per-mob mode/amount/chance overrides inherit defaults and can be reset.
- Custom reward templates receive a stable PlexonSpawners identity; held-item capture is sanitized rather than preserving arbitrary PDC.
- Existing WildStacker-authoritative Silk recovery is retained.
- Schema 10 upgrades through the targeted schema 11 migration; the final GUI handoff does not create another schema version.

## Required final deployment gate

The broader 4.0 runtime candidate was already tested successfully on PlexonCraft. Do not publish stable until the exact final CI-built JAR passes the focused handoff smoke:

- right-click a WildStacker-managed spawner: no Plexon withdrawal GUI opens and WildStacker's expected tier/upgrade interaction works;
- `/pspawners admin` still opens and functions;
- `/pspawners give <online-player> zombie 2` (or equivalent) still delivers WildStacker-compatible spawners;
- one representative break/reward regression still works with WildStacker logical quantity authoritative.

Migration status for this final campaign is:

```text
NOT_REQUIRED
```

Do not infer the focused runtime PASS from unit tests or GitHub Actions.

## Stable evidence

Before publishing `v4.0.0`, record:

- exact final feature/candidate SHA;
- canonical test totals and GitHub Actions run/artifact IDs;
- focused runtime smoke result;
- final `main` SHA;
- final `release/stable` SHA;
- `v4.0.0` tag target;
- public GitHub release asset size and SHA-256;
- provenance verification that the public release JAR matches the accepted final source/build boundary.

## Current release state

```text
BROAD 4.0 RUNTIME CANDIDATE PASSED
FINAL CLICK-HANDOFF SMOKE PENDING
MIGRATION NOT_REQUIRED
PR NOT YET MERGED
V4.0.0 NOT YET PUBLISHED
```
