# Migrating PlexonSpawners 2.3.1 → 3.0

## Scope

PlexonSpawners 3.0 introduces durable physical managed-spawner state. This is a major data/runtime change and must be staged before production promotion.

## Before upgrading

1. Stop the server completely.
2. Back up the current `PlexonSpawners-2.3.1.jar`.
3. Back up the complete `plugins/PlexonSpawners/` directory.
4. Preserve the v2.3.1 release/checksum as the rollback artifact.
5. Do not remove existing custom `config.yml`, `messages.yml`, Essence ItemStack data or per-mob rules.

## Configuration migration

3.0 advances the stock configuration schema to `config-version: 5` and adds the `managed:` section.

Existing customized configuration values remain authoritative. The migration only changes the stock recovered-spawner lore to include `%tier%` when it still exactly matches the previous stock template.

## Existing managed items

2.x managed spawner items used schema 1 or no explicit schema marker. 3.0 continues accepting those items and maps them to tier 1.

New 3.x items use schema 2 and store a `spawner_tier` value.

Do not rewrite old item lore or metadata manually. Item identity is PDC-backed.

## Physical spawners

3.0 does not perform a global scan of every world/chunk to discover historical physical spawners. This is intentional for performance and ownership correctness.

A physical spawner becomes a 3.x persistent managed spawner when it enters the 3.x managed lifecycle, such as a managed item placement. The plugin then stores:

- stable spawner UUID;
- world and block coordinates;
- creature type;
- owner;
- tier;
- access state;
- placement time;
- lifetime attributed-spawn count.

State is persisted in `managed-spawners.db` and mirrored as recovery PDC on the physical `CreatureSpawner`.

## Runtime validation matrix

Before stable promotion, validate:

1. place a schema-1 2.x managed item and confirm tier 1 ownership;
2. place a new schema-2 item and confirm its tier;
3. restart and confirm registry/PDC state persists;
4. unload/reload its chunk and confirm indexed reconciliation;
5. test `OWNER_ONLY`, `PUBLIC_USE` and `PUBLIC` with owner, non-owner and admin;
6. upgrade through Essence and verify exact cost consumption;
7. test insufficient Essence;
8. force/observe a physical apply failure in staging and verify tier rollback + Essence refund;
9. recover a higher-tier spawner with qualifying Silk and confirm tier is retained;
10. test failed-Silk Essence behavior;
11. test WildStacker one-unit handling when installed;
12. verify a managed-spawner-origin mob is reported through `PlexonSpawnersApi`;
13. verify normal/vanilla spawner-origin mobs are not falsely attributed;
14. run Spark/MSPT comparison against 2.3.1 under representative spawner activity;
15. run at least a 30-minute soak while watching memory, scheduler activity, writer queue/state and exceptions.

## Rollback

If the candidate fails runtime certification:

1. stop the server;
2. preserve the failed 3.x data directory for diagnosis;
3. restore the full pre-upgrade `plugins/PlexonSpawners/` backup;
4. restore `PlexonSpawners-2.3.1.jar`;
5. start the server and verify the 2.3.1 behavior matrix.

Do not attempt to make 2.3.1 consume `managed-spawners.db`; it predates the 3.x physical registry.

## Stable promotion boundary

`3.0.0-rc.1` is not stable solely because CI passes. Stable `3.0.0` requires the real PlexonCraft runtime gates above and no known HIGH/CRITICAL defect.
