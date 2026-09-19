# PlexonSpawners 4.0.0 — Full Stable Release

PlexonSpawners 4.0.0 is the full stable successor to 3.4.0. It is not an RC, preview, snapshot,
candidate tag, or prerelease.

## Stable architecture

WildStacker is the required and exclusive authority for placed spawner quantities/persistence,
placement, merging, unstacking, spawner-item representation, stack limits, tiers/upgrades, the native
placed-spawner GUI, entity stacking and item stacking.

PlexonSpawners owns the PlexonCraft-specific policy layer: Silk recovery, ESSENCE/CUSTOM_ITEM/
ESSENCE_AND_CUSTOM_ITEM/NONE outcomes, exact reward templates, logical-unit reward rolls, world
scope, administration, diagnostics, a read-only API, post-commit events and optional PlexonCore 2.1.0
lifecycle/health/scheduling integration.

The removed 3.x native stack engine, managed stack database runtime, placement/merge engine, fallback
entity aggregation, Plexon tier/upgrade engine, redstone lock, stack display and withdrawal GUI are
not present in the 4.0 runtime JAR.

## Reliability

4.0.0 includes schema 12, immutable runtime generations, revision/generation-safe admin drafts,
bounded asynchronous persistence, atomic replacement/rollback, stale-session rejection, fail-closed
future schemas and exact Paper ItemStack byte serialization. Unsafe inventory interaction paths are
rejected and foreign PDC is preserved when Plexon identity is applied.

## Requirements

- Paper 26.2.build.121-stable
- Java 25
- WildStacker API/runtime 2026.2-compatible line
- PlexonCore 2.1.0 optional

PlexonCore compile/test SHA-256:
7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c

## Full stable source certification

The release is built from the exact source shared by `main` and `release/stable`. The stable
workflow rebuilds, tests, verifies the distribution, publishes a non-prerelease GitHub Release and
re-downloads the public assets for checksum verification.

Stable release invariant:

- tests: 55
- failures: 0
- errors: 0
- Java class major: 69
- JAR size: 149,813 bytes
- JAR SHA-256: ac823f3c3cd50e4967f2a7796cfa1e9a8ab3d4a7e3ca5a606a595baa28693eb5

## Upgrade and deployment

4.0.0 is a stable software release. Environment-specific deployment validation is separate from
release SemVer status.

Servers upgrading from 3.x must inventory legacy `managed-spawners.db` state before cutover. If
legacy logical quantity exists, convert it into WildStacker-authoritative state before replacing the
3.x runtime. See `docs/MIGRATION_4_0.md`.

The runtime certification template remains an operational post-release checklist; it is not an RC or
snapshot gate.

## Rollback baseline

Previous stable:
- tag: v3.4.0
- source: cd9966c79add8c98dcdb389d2fc722ce66d795ec
- JAR SHA-256: 6fd9b6119371820173307853a7b58243ebf1f28e083212dcc6287c01d68f3c58
