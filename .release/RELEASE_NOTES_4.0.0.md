# PlexonSpawners 4.0.0 — WildStacker-Authoritative Policy Release

## Architecture

WildStacker is the required and exclusive authority for spawner quantities, persistence, placement,
merging, unstacking, spawner items, stack limits, tiers/upgrades, normal placed-spawner interaction,
entity stacking and item stacking.

PlexonSpawners 4.0 owns only Plexon-specific break/recovery/reward policy, administration,
diagnostics, a read-only policy API and post-commit policy events.

The final runtime contains no 3.x ManagedSpawnerRegistry, native placement/merge engine, duplicate
stack persistence, Plexon tier/upgrade engine, redstone stack lock, stack display service, withdrawal
GUI or fallback entity stacking engine.

## Schema 12

- config-version: 12.
- Dead withdrawal GUI keys/messages are retired.
- Fresh installs use an explicit Survival allowlist.
- Schema-11 empty legacy allowlists migrate to explicit scope.mode: ALL to preserve old semantics;
  operators must explicitly configure the PlexonCraft production Survival scope before certification.
- Future schemas fail closed.

## Exact custom rewards

Paper exact item byte serialization is the authoritative stored format after capture/migration.
Template amount is normalized to one. Reward delivery clones the exact template, preserves foreign
PDC/components/attributes/enchantments/damage/potion state/item model, and adds Plexon identity
non-destructively.

## Atomic administration

The admin GUI keeps isolated drafts bound to a config revision and runtime generation. Save uses:

1. primary-thread permission/session/revision validation and immutable draft capture;
2. bounded I/O backup, serialization, temp write and atomic replacement;
3. primary-thread prepared runtime generation commit;
4. revision advancement only after successful commit;
5. asynchronous backup pruning/rollback handling.

Unsafe inventory click types are rejected and semantic actions are deferred/revalidated.

## Break coordination

The final singular-break remediation remains WildStacker-authoritative:

- physical break intent is observed before destructive handling without stealing mutation authority;
- transient/non-cached singular WildStacker objects remain valid;
- spawned type and authoritative recovery item are snapshotted before final 1 -> 0;
- SpawnerUnstackEvent#getAmount() remains authoritative when emitted;
- SpawnerDropEvent is the preferred native-drop finalization signal;
- next-tick reconciliation handles event-gap cases;
- one terminal gate prevents duplicate reward/recovery;
- protected/unchanged breaks pay nothing.

## API, events and Core

A small Bukkit ServicesManager API exposes policy/integration reads only. It has no stack mutation
surface. Post-commit events report finalized recovery/reward/break outcomes.

PlexonCore 2.1.0 is an optional thin lifecycle/health/scheduling integration. The compile/test artifact
is pinned to SHA-256:

7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c

Core never owns spawner quantities, persistence, placement, merging, items or upgrades.

## Migration gate

Migration status is deliberately PENDING until the production 3.4 state is inventoried.

Use tools/legacy_managed_inventory.py to export the old managed-spawners.db physical count and
logical total without mutating it. If any legacy logical quantity exists, stable publication remains
blocked until a separate one-shot WildStacker conversion is completed with per-location evidence and:

old_logical_total == new_logical_total

If no legacy managed quantity exists, NOT_REQUIRED still needs recorded evidence.

## Stable publication gate

v4.0.0 may publish only when the release workflow proves:

- certified source SHA equals the exact main == release/stable SHA;
- the successful Build workflow exists for that SHA;
- deterministic rebuilt JAR SHA-256 equals the live-tested candidate SHA-256;
- Paper/WildStacker/Core identities match certification;
- singular/stacked matrix PASS;
- exact reward item round-trip PASS;
- Survival scope PASS;
- 30-minute Spark/runtime soak PASS;
- migration PASS or NOT_REQUIRED with valid totals.

Any source change after live PASS invalidates the certificate.

Rollback baseline remains v3.4.0 at
cd9966c79add8c98dcdb389d2fc722ce66d795ec,
JAR SHA-256
6fd9b6119371820173307853a7b58243ebf1f28e083212dcc6287c01d68f3c58.
