# Migrating PlexonSpawners 2.3.1 → 3.0

## Scope

PlexonSpawners 3.0 introduces durable physical managed-spawner state. This is a major data/runtime change. Stable GitHub publication is reproducible from exact source, but live migration must still be staged and backed up before production deployment.

## Before upgrading a live server

1. Stop the server completely.
2. Back up the current `PlexonSpawners-2.3.1.jar`.
3. Back up the complete `plugins/PlexonSpawners/` directory.
4. Preserve the `v2.3.1` release/checksum as the rollback artifact.
5. Do not remove existing custom `config.yml`, `messages.yml`, Essence ItemStack data or per-mob rules.

Stable rollback source is `v2.3.1` at `0ec54a04ecb77374874edf889b20286144c32a88`; its JAR SHA-256 is `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8`.

## Configuration migration

3.0 advances the stock configuration schema to `config-version: 5` and adds the `managed:` section.

Existing customized configuration values remain authoritative. The migration only changes the stock recovered-spawner lore to include `%tier%` when it still exactly matches the previous stock template.

## Existing managed items

2.x managed spawner items used schema 1 or no explicit schema marker. 3.0 continues accepting those items and maps them to tier 1.

New 3.x items use schema 2 and store a `spawner_tier` value.

Do not rewrite old item lore or metadata manually. Item identity is PDC-backed.

## Physical spawners and durable identity

3.0 does not perform a global scan of every world/chunk to discover historical physical spawners. This is intentional for performance and ownership correctness.

A physical spawner becomes a 3.x persistent managed spawner when it enters the 3.x managed lifecycle, such as managed-item placement. The plugin then stores:

- stable spawner UUID;
- world and block coordinates;
- creature type;
- owner;
- tier;
- access state;
- placement time;
- lifetime attributed-spawn count.

State is persisted in `managed-spawners.db` schema 1 and mirrored as recovery PDC on the physical `CreatureSpawner`.

Stable 3.0 reconciliation is identity-safe. A persisted coordinate is not enough to claim a physical block: the block must carry valid managed recovery PDC proving the same UUID and exact world/block identity. If the physical block is missing, vanilla, corrupt or has a different managed identity, the stale registry record is removed and the replacement block is left untouched.

## Live deployment validation matrix

After installing stable 3.0 on a backed-up staging/production environment, validate:

1. clean startup on Paper 26.2 build 121 / Java 25 / PlexonCore 2.0.4 with no `EntityType doesn't have key` exception;
2. place a schema-1 2.x managed item and confirm tier 1 ownership;
3. place a new schema-2 item and confirm its tier;
4. restart and confirm registry/PDC state persists;
5. unload/reload its chunk and confirm indexed identity-safe reconciliation;
6. replace/remove a recorded managed spawner in a disposable test location and confirm stale registry state does not claim the replacement block;
7. test `OWNER_ONLY`, `PUBLIC_USE` and `PUBLIC` with owner, non-owner and admin;
8. upgrade through Essence and verify exact cost consumption;
9. test insufficient Essence;
10. force/observe a physical apply failure in staging and verify tier rollback + Essence refund;
11. recover a higher-tier spawner with qualifying Silk and confirm tier is retained;
12. test failed-Silk Essence behavior;
13. test WildStacker one-unit handling when installed;
14. verify a managed-spawner-origin mob is reported through `PlexonSpawnersApi`;
15. verify normal/vanilla spawner-origin mobs are not falsely attributed;
16. run Spark/MSPT comparison against the previous production baseline under representative spawner activity;
17. run at least a 30-minute soak while watching memory, scheduler activity, writer state and exceptions.

## Rollback

If live deployment fails:

1. stop the server;
2. preserve the failed 3.x data directory for diagnosis;
3. restore the full pre-upgrade `plugins/PlexonSpawners/` backup;
4. restore `PlexonSpawners-2.3.1.jar`;
5. start the server and verify the 2.3.1 behavior matrix.

Do not attempt to make 2.3.1 consume `managed-spawners.db`; it predates the 3.x physical registry.

## Certification boundary

GitHub stable closure and live runtime certification are separate gates.

Stable GitHub publication requires exact-current-`main` source, accepted Phase 2/RC2 ancestry, a clean test suite, verified Java/JAR distribution, immutable tag creation, public release asset download, checksum verification and provenance verification. If live PlexonCraft validation has not been executed, release provenance records `runtime_certification=NOT_EXECUTED`.

Live migration, gameplay, cross-plugin, Spark/MSPT and soak evidence remains required before treating a deployment as production-certified. CI never infers a live runtime PASS.
