# Changelog

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
