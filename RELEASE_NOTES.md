# PlexonSpawners 2.3.1 — Core 2.0.4 Stabilization

PlexonSpawners 2.3.1 is a conservative source-stabilization release before the planned premium rewrite. Gameplay mechanics and GUI design remain on the 2.3 line.

## Core lifecycle

- Pins exact PlexonCore 2.0.4 and its release SHA-256 in CI.
- Supports Core API `>=1.0 <3.0` and rejects unsupported Core 3.
- Uses owner-aware Core 2 module state transitions.
- Uses owner-scoped unregister cleanup so an old/reloaded instance cannot remove a replacement registration.
- Preserves safe standalone behavior when Core is absent or unavailable.

## Regression contracts

The 2.3.1 suite verifies source-level invariants for:

- managed-spawner PDC identity and schema rejection;
- placement fail-closed behavior for vanilla/corrupt metadata;
- prevention of simultaneous vanilla and managed break drops;
- inventory-full Essence fallback to ground drops;
- no synchronous file/database I/O in placement/break listeners;
- reload invalidation of cached managed-item templates;
- centralized WildStacker provider refresh;
- Core 2.0.4 acceptance, Core 3 rejection and standalone fallback.

## Preserved 2.3 behavior

- Managed spawner item identity remains PDC-backed.
- WildStacker remains fail closed on unavailable/cancelled provider operations.
- No database or persistent spawner-location index is introduced.
- No global chunk/entity scans or repeating per-spawner tasks are introduced.
- Public recovery, placement and Essence events remain unchanged.

## Certification boundary

`SOURCE STABILIZED / CORE 2.0.4 COMPATIBLE`.

A premium/major mechanics and UX rewrite is still required after this maintenance wave. Runtime gameplay validation, provider integration, restart/soak and Spark profiling are NOT EXECUTED in the GitHub-only certification environment.

## Release assets

- `PlexonSpawners-2.3.1.jar`
- `SHA256SUMS.txt`
