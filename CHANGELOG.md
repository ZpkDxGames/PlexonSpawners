# Changelog

## 3.1.0-rc.1 - WildStacker Nearby Logical Stack Cap Candidate

- Added `managed.nearby-stack-cap` with defaults `enabled: true`, `radius: 8.0`, `maximum-amount: 99`, and `same-type-only: true`.
- Added bounded same-type population counting that uses WildStacker's real logical entity stack amount when available and physical amount `1` when WildStacker is absent.
- Extended the existing cached reflective WildStacker bridge with entity/spawner amount accessors and lifecycle-cached `SpawnerStackedEntitySpawnEvent` / `EntityStackEvent` interception.
- Added fast-path versus granular spawn decisions using the proven upper bound `spawn-count × WildStacker spawner stack amount`.
- Near the cap, disables WildStacker's direct stacked contribution, cancels the direct `EntityStackEvent`, and gates unit spawns through Paper `PreSpawnerSpawnEvent` so the standard overridden-spawner path does not intentionally overshoot.
- Added a conservative whole-cycle rejection fallback for non-overridden Bukkit `SpawnerSpawnEvent` contributions when the public event contract cannot safely expose a partial amount.
- Preserved managed UUID identity, persistence schema 1, item schema 2, ownership/access, tier tuning, provenance, break/unstack behavior and first-party persistence semantics.
- Kept tier `max-nearby-entities` separate from the new logical stack cap.
- Added guard diagnostics counters for checks, blocked attempts, logical entities counted, WildStacker amount lookups and fail-closed decisions.
- Bumped configuration schema from 5 to 6 using additive defaults only; existing administrator customizations are not rewritten.
- Added policy/settings/source-contract regression coverage and dedicated migration/release documentation.
- Updated distribution verification for the new runtime classes and added a dedicated exact-main prerelease publication workflow.

**RUNTIME CERTIFICATION NOT EXECUTED.** This candidate must be verified on PlexonCraft with Paper 26.2, WildStacker's standard spawner-override mode, managed `x99` cap/resume, different-type isolation, overlapping managed spawners, stacked spawners, restart and MSPT/TPS checks before stable promotion.

## 3.0.0 - Stable Managed-Spawner Release

- Promoted the accepted 3.0 managed-spawner architecture and RC2 runtime-reliability line to stable.
- Preserved persistent UUID/world/block/owner/tier/access/placement/lifetime-spawn state in `managed-spawners.db` schema 1.
- Preserved managed item schema 2 with 2.x schema-1 item compatibility as tier 1.
- Preserved bounded tier tuning, access policies, transactional Essence upgrade rollback/refund, chunk-indexed first-party spawn provenance and WildStacker fail-closed handling.
- Retained the RC2 Paper 26.2 startup correction that guards `EntityType.UNKNOWN`, rejects it from managed records/items and fails closed on invalid UNKNOWN physical breaks.
- Hardened chunk reconciliation so a persisted coordinate cannot claim an unrelated replacement spawner: physical PDC must prove the same managed UUID and exact world/block identity before registry state is reapplied.
- Added direct regression coverage for managed physical identity matching.
- Replaced the RC-specific publication path with canonical Build + exact-current-main stable Release workflows.
- Stable Release now rebuilds/retests exact source, verifies Java 25/class major 69 and dependency isolation, publishes JAR/checksum/test/provenance evidence, downloads the public assets and verifies them before succeeding.
- Stable rollback remains `v2.3.1` (`0ec54a04ecb77374874edf889b20286144c32a88`), with JAR SHA-256 `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8`.
- Live PlexonCraft migration, placement/break/access/upgrade/restart/WildStacker/provenance/Spark/soak certification remains an operational follow-up and is recorded as `NOT_EXECUTED` when not run; CI does not infer live runtime PASS.

## 3.0.0-rc.1 - Phase 2 Premium Managed-Spawner Candidate

- Added durable physical managed-spawner identity with stable UUID, world/block coordinates, owner, tier, access state, placement timestamp and lifetime attributed-spawn count.
- Added `managed-spawners.db` schema 1 with explicit header validation, atomic snapshot replacement, one shared persistence coordinator and one single-thread writer.
- Added in-memory block and chunk indexes; no global chunk/entity scan or per-spawner repeating task is introduced.
- Added five bounded configurable spawner tiers controlling spawn delay, spawn count, nearby cap, activation range and spawn range.
- Added `OWNER_ONLY`, `PUBLIC_USE` and `PUBLIC` access policies with owner/admin management and break enforcement.
- Added right-click managed-spawner control GUI with creature/owner/tier/stat/access visibility, stable close behavior and protected clicks/drags.
- Added transactional PDC-backed Spawner Essence upgrades with registry/physical rollback and Essence refund on physical-apply failure.
- Advanced managed item schema to 2 with `spawner_tier`, while preserving schema-1 2.x item compatibility as tier 1.
- Preserved tier on qualified recovered managed-spawner items.
- Added physical `CreatureSpawner` recovery PDC for id/owner/tier/access/placement state.
- Added bounded chunk-indexed `SPAWNER` spawn attribution and entity PDC provenance with stable source-spawner UUID.
- Expanded `PlexonSpawnersApi` with tier-aware items, managed-spawner lookups/snapshots and provenance queries for Skills/Jobs/Quests/Keys integrations.
- Added chunk-load and already-loaded reconciliation limited to records already present in the managed registry.
- Added configurable per-chunk managed-spawner safety cap.
- Updated diagnostics to expose managed runtime count, tier ceiling, persistence cadence, provenance radius and chunk cap.
- Extended `/pspawners give` with an optional managed spawner tier.
- Updated Paper 26.2 / Java 25 / PlexonCore 2.0.4 build/release-candidate pipeline and removed the obsolete 2.3.0 tag workflow.
- Added Phase 2 source-contract coverage for two-phase placement, schema migration, chunk-bounded provenance, access gates, coalesced persistence and upgrade rollback/refund.
- Added 3.0 API/migration documentation and explicit prerelease runtime-certification boundary.

**RUNTIME CERTIFICATION NOT EXECUTED** for this candidate. Stable promotion remains blocked until the PlexonCraft placement/break/access/upgrade/restart/persistence/provenance/WildStacker/Spark/soak matrix passes.

## 2.3.1 - PlexonCore 2.0.4 Stabilization

- Pinned PlexonCore 2.0.4 and its release SHA-256 in CI.
- Added Core API compatibility for `>=1.0 <3.0` while rejecting unsupported Core 3.x.
- Updated module lifecycle ownership for PlexonCore 2.x and preserved safe standalone fallback.
- Added source-level stabilization contracts around managed-item identity, placement, break ownership, Essence overflow, hot-path I/O, cache reload and Core integration.
- Preserved the 2.3 gameplay/GUI line as the rollback baseline before the Phase 2 premium rewrite.

## 2.3.0 - Stable Performance & Reliability

- Cached WildStacker lifecycle/API resolution so steady-state spawner breaks no longer perform plugin lookup, class loading, or method discovery.
- Added safe WildStacker enable/disable lifecycle handling and preserved fail-closed behavior for incompatible, cancelled, degraded, or disabled providers.
- Compiled gameplay configuration into an immutable runtime snapshot with resolved world UUIDs, precompiled per-EntityType Essence rules, and validation warnings.
- Reduced accepted-break allocations by making transaction UUIDs lazy, reusing successful-outcome locations, and avoiding unnecessary Silk bypass permission checks.
- Added per-EntityType managed spawner item template caching while preserving exact BlockStateMeta and PDC identity.
- Added O(1) managed `spawner_type` PDC lookup and cached EntityType display names.
- Added optional `spawner_schema` PDC versioning on new managed items while keeping existing 2.x managed spawners readable.
- Cached Essence maximum stack size and added minimum-stack bulk creation.
- Changed inventory Essence delivery to one bounded `Inventory#addItem` operation with overflow handled once.
- Added warnings for configurations capable of creating excessive ground Essence item entities without silently changing the configured logical award.
- Expanded diagnostics with WildStacker resolution/cache state, world-filter state, Essence defaults/overrides/max-stack size, managed template cache size, entity-key lookup size, and config warning count.
- Precomputed the admin spawnable-entity completion list.
- Preserved Paper 26.2 / Java 25, public spawner events, strict Silk rules, one-unit stacked-spawner recovery, protection compatibility, PDC item identity, and database-free/stateless runtime.

## 2.2.0 - PlexonCore Migration & Public Spawner Event API

- Migrated PlexonSpawners to the PlexonCore module bridge while preserving standalone operation.
- Added Core API range validation and module lifecycle states STARTING/READY/DEGRADED/FAILED.
- Registered module id `spawners` and published implemented spawner/API/event/WildStacker/stateless capabilities.
- Added `PlexonSpawnerRecoveredEvent`, `PlexonSpawnerEssenceAwardedEvent`, and `PlexonSpawnerPlacedEvent` as stable synchronous post-success Bukkit events.
- Added non-empty transaction IDs and event IDs for public event correlation and deduplication.
- Preserved the existing `PlexonSpawnersApi` ServicesManager contract.
- Added `/pspawners diagnostics` and expanded `/pspawners info` with Core mode, module state, Silk, Essence, WildStacker, API and event status.
- Preserved `loadbefore: WildStacker` and added `softdepend: PlexonCore`.
- PlexonCore is compile-only/provided and CI verifies its runtime classes are not shaded.

## 2.1.0 - PlexonCraft Presentation Update

- Redesigned recovered spawner item names and lore around the PlexonCraft primary/secondary color palette.
- Added `<!italic>` to stock item formatting for clean non-italic Minecraft lore.
- Replaced generic plugin-facing lore with concise collectible-style flavor text, creature metadata, placement state, and a PlexonCraft footer.
- Reworked `messages.yml` into the PlexonCraft message theme.
- Preserved the stable break ownership, WildStacker compatibility, Silk Touch, Essence, XP, world, and Creative-mode logic.

## 2.0.2 - Silk Touch Qualification Hotfix

- Fixed operators/admins automatically qualifying for spawner recovery without Silk Touch.
- Changed `plexonspawners.bypass.silk` to `default: false`.
- Added `breaking.allow-silk-bypass-permission`, disabled by default.

## 2.0.1 - Spawner Ownership & WildStacker Compatibility

- Added authoritative break ownership and WildStacker one-unit compatibility.
- Preserved fail-closed handling when the stack provider cannot safely complete the operation.

## 2.0.0 - GUI & Configuration Update

- Redesigned the admin interface into focused pages.
- Added configurable Essence drop chances and per-mob overrides.
- Added configuration versioning and safe 1.x migration.

## 1.0.0 - Initial Release

- Released standalone Paper 26.2 / Java 25 spawner handling, typed spawners, physical PDC-secured Essence, GUI administration, and Bukkit ServicesManager API.
