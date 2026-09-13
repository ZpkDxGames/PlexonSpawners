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

## Final singular break-parity remediation

Live runtime testing invalidated the previous broad 4.0 acceptance because the last physical `1x` spawner could disappear without the configured Plexon recovery/reward result.

The final 4.0 source remediation:

- accepts WildStacker's valid transient/non-cached singular `StackedSpawner` representation instead of using `isCached()` as a validity gate;
- captures physical `BlockBreakEvent` intent before WildStacker's `HIGHEST` mutation path without cancelling or replacing WildStacker's break handling;
- snapshots spawned type and WildStacker's authoritative recovery item before the destructive `1 -> 0` transition;
- keeps `SpawnerUnstackEvent#getAmount()` authoritative whenever emitted;
- keeps `SpawnerDropEvent` as the preferred native-drop interception/finalization signal;
- adds next-tick state reconciliation for the singular event-gap/final-unit path;
- uses one exactly-once terminal gate so native events and fallback cannot duplicate recovery, Essence or custom rewards;
- treats unchanged/protected breaks as denied with zero payout;
- preserves WildStacker's right-click manage/tier GUI and namespaced `/pspawners give` delegation.

No new configuration or migration is introduced. Schema remains `11` and migration status remains `NOT_REQUIRED`.

See `docs/RUNTIME_SINGULAR_BREAK_PARITY.md` for the source-level defect boundary and runtime gate.

## Required final deployment gate

Do not publish stable until the exact final CI-built JAR passes all of the following on PlexonCraft:

- singular `1x` Silk recovery;
- singular `1x` non-Silk Essence;
- singular custom/BOTH reward;
- current creative recovery/reward policy;
- `2x -> 1x` regression;
- final `1x -> 0` regression;
- upgraded singular type/tier preservation;
- protected/cancelled break with zero payout;
- WildStacker native right-click manage/tier GUI;
- `/pspawners give <online-player> zombie 2` (or equivalent).

Migration status for this final campaign is:

```text
NOT_REQUIRED
```

Do not infer live runtime PASS from unit tests or GitHub Actions.

## Stable evidence

Before publishing `v4.0.0`, record:

- exact final feature/candidate SHA;
- canonical test totals and GitHub Actions run/artifact IDs;
- full singular + stacked live runtime matrix;
- final `main` SHA;
- final `release/stable` SHA;
- `v4.0.0` tag target;
- public GitHub release asset size and SHA-256;
- provenance verification that the public release JAR matches the accepted final source/build boundary.

## Current release state

```text
SOURCE REMEDIATION IN PROGRESS
SINGULAR RUNTIME FAILURE INVALIDATED PRIOR BROAD ACCEPTANCE
EXACT REMEDIATED CANDIDATE LIVE MATRIX REQUIRED
MIGRATION NOT_REQUIRED
V4.0.0 NOT YET PUBLISHED
```
