# PlexonSpawners 3.0.0 — Stable Managed-Spawner Release

PlexonSpawners 3.0.0 is the stable GitHub closure of the accepted 3.0 managed-spawner architecture and final RC2 reliability line.

## Stable product boundary

- durable managed-spawner UUID/world/block/type/owner/tier/access/placement/lifetime-spawn state;
- `managed-spawners.db` schema 1 with explicit header validation, one shared coordinator, one single-thread writer and atomic snapshot replacement;
- managed item schema 2 with 2.x schema-1 item compatibility as tier 1;
- owner/access policies and five bounded configurable tiers;
- transactional PDC-backed Spawner Essence upgrades with tier rollback and Essence refund on physical-apply failure;
- bounded chunk-indexed first-party `SPAWNER` provenance and stable source UUIDs through `PlexonSpawnersApi`;
- WildStacker fail-closed behavior and one-unit handling;
- no global chunk/entity scans and no per-spawner repeating tasks.

## Final reliability corrections

### RC2 Paper 26.2 startup correction

RC1 could fail during plugin enable when the entity lookup called `EntityType.UNKNOWN#getKey()`. The accepted RC2 line skips/rejects sentinel/non-keyed entity types, centralizes guarded key access and fails closed on invalid UNKNOWN physical spawners rather than converting them to a valid mob type.

### Stable identity-safe reconciliation

The final source audit found that reconciliation trusted a persisted block coordinate without proving that the physical block still represented the same managed instance. A stale database record could therefore reapply old UUID/owner/tier/access state to a replacement spawner at the same coordinates.

Stable 3.0 requires valid managed recovery PDC proving the same UUID and exact world/block identity before registry state is reapplied. Missing, corrupt, vanilla or mismatched physical state removes only the stale registry record; the replacement block remains untouched.

## Provenance

- accepted Phase 2 source: `be65cb636bf21f6aeef6a0f1d87c470882df472d`
- accepted RC2 source: `bcedd2c71db785ad42eec2ddd7416a76d9fe02da`
- RC2 JAR SHA-256: `d037f695b498d35453f7dbdd9415b07c70c6293dab557cd1fa0720501e57e337`
- stable rollback: `v2.3.1` / `0ec54a04ecb77374874edf889b20286144c32a88`
- rollback JAR SHA-256: `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8`
- Paper `26.2.build.121-stable`
- Java 25 / class major 69
- PlexonCore 2.0.4 with pinned SHA-256 `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`

## Stable publication contract

Stable publication is allowed only from `release/stable` when it equals exact current `main`. The workflow rebuilds and retests exact source, verifies JAR contents/version/bytecode and Core isolation, publishes `PlexonSpawners-3.0.0.jar`, `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then downloads and verifies those public assets before the Release job can pass.

## Deployment certification

Live PlexonCraft migration, placement/break/access/upgrade/restart/persistence/WildStacker/provenance/Spark/MSPT/soak validation remains a deployment follow-up. GitHub release provenance records `runtime_certification=NOT_EXECUTED` when that evidence has not been run. CI never substitutes for live runtime evidence.
