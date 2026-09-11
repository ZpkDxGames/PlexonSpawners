# PlexonSpawners 3.0.0

Stable GitHub repository closure of the accepted PlexonSpawners 3.0 managed-spawner line, including the RC2 Paper 26.2 startup correction and the final identity-safety fix found during the full source audit.

## Stable product boundary

- Persistent managed spawners retain stable UUID, world/block coordinates, creature type, owner, tier, access policy, placement time and lifetime attributed-spawn count.
- `managed-spawners.db` remains schema 1 with explicit header validation, coalesced revisions, one single-thread writer and atomic snapshot replacement.
- Physical managed spawners retain recovery PDC; new managed items remain schema 2 while legacy 2.x schema-1 items remain readable as tier 1.
- `OWNER_ONLY`, `PUBLIC_USE` and `PUBLIC` access policies remain authoritative.
- Five bounded tiers continue to control delay, spawn count, nearby cap, activation range and spawn range.
- Spawner Essence upgrades retain reserve/apply/rollback/refund behavior.
- First-party `SPAWNER` provenance remains bounded by the managed chunk index and exposes stable source UUIDs through `PlexonSpawnersApi`.
- WildStacker compatibility remains fail closed.

## Reliability corrections

### Paper 26.2 `EntityType.UNKNOWN` startup guard

The RC1 candidate could fail plugin enable while building the entity lookup because Paper 26.2 rejects `EntityType.UNKNOWN#getKey()`. RC2 made key access sentinel-safe, rejects `UNKNOWN` from managed records/items and fails closed when an invalid UNKNOWN physical spawner is encountered.

### Managed physical identity reconciliation

The final source audit found that chunk reconciliation trusted only a persisted block coordinate. A stale database row could therefore reapply its UUID/owner/tier/access state to an unrelated replacement spawner at the same coordinates.

Stable 3.0 now reapplies registry state only when the physical `CreatureSpawner` proves the same managed UUID and exact world/block identity. Missing, corrupt, vanilla or mismatched physical identity removes only the stale registry entry; the replacement block is not rewritten.

## Compatibility and provenance

- Paper `26.2.build.121-stable`
- Java 25 / class major 69
- PlexonCore 2.0.4, checksum `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`
- accepted Phase 2 source: `be65cb636bf21f6aeef6a0f1d87c470882df472d`
- accepted final RC2 source: `bcedd2c71db785ad42eec2ddd7416a76d9fe02da`
- historical RC2 JAR SHA-256: `d037f695b498d35453f7dbdd9415b07c70c6293dab557cd1fa0720501e57e337`
- stable rollback: `v2.3.1` at `0ec54a04ecb77374874edf889b20286144c32a88`
- rollback JAR SHA-256: `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8`

## Stable publication verification

Stable publication runs only from `release/stable` when that branch points to exact current `main`. The workflow rebuilds and retests exact source, verifies the installable JAR and Java bytecode, publishes the JAR plus checksum/test/provenance evidence, downloads every public release asset, and verifies checksum plus exact-source/lineage/stability provenance before completing.

Live PlexonCraft migration, placement/break, ownership, upgrade, restart, WildStacker, cross-plugin provenance, Spark/MSPT and soak validation remains an operational deployment follow-up. GitHub stable provenance records `runtime_certification=NOT_EXECUTED` when that evidence has not been executed; CI never infers live runtime PASS.
