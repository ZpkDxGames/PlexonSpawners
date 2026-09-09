# Changelog

## 2.3.1 - PlexonCore Integration Hotfix

- Made PlexonCore 1.x a required runtime dependency and moved PlexonSpawners to deterministic `POSTWORLD` loading after Core `STARTUP` initialization.
- Removed the silent standalone downgrade that could leave PlexonSpawners visible as `Legacy/Standalone` even when PlexonCore was installed.
- Added fail-fast validation for the PlexonCore plugin, ServicesManager API registration, API owner, API range, and shared Core services.
- Hardened module `spawners` registration and safely reclaim stale self-registrations left by disabled prior plugin instances.
- Registration rejection now stops startup with the Core registry reason instead of enabling a detached runtime.
- Added Core integration-registry health publishing alongside module STARTING/READY/DEGRADED/FAILED/DISABLED state updates.
- Added safe shutdown behavior if PlexonCore is disabled while PlexonSpawners is active.
- Removed the obsolete standalone bridge and standalone-mode unit test.
- Added a Core-binding distribution test and strengthened the Gradle/CI JAR contract to require `depend: PlexonCore` while continuing to reject shaded Core classes.
- Fixed managed spawner placement mutating world state from a `MONITOR` listener by moving the handler to `HIGHEST` with `ignoreCancelled = true`.
- Preserved the 2.3.0 hot-path, WildStacker fail-closed, Essence, PDC identity, public API/event, and database-free behavior.

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
- Preserved Paper 26.2 / Java 25, optional PlexonCore 1.x, public spawner events, strict Silk rules, one-unit stacked-spawner recovery, protection compatibility, PDC item identity, and database-free/stateless runtime.
- Updated CI/release automation for `PlexonSpawners-2.3.0.jar`, `SHA256SUMS.txt`, tag `v2.3.0`, and verified GitHub Release publishing.

## 2.2.0 - PlexonCore Migration & Public Spawner Event API

- Migrated PlexonSpawners to the established PlexonCore module bridge while preserving standalone operation.
- Added Core API range validation for `>=1.0 <2.0` and module lifecycle states STARTING/READY/DEGRADED/FAILED.
- Registered module id `spawners` and published implemented spawner/API/event/WildStacker/stateless capabilities.
- Added `PlexonSpawnerRecoveredEvent`, `PlexonSpawnerEssenceAwardedEvent`, and `PlexonSpawnerPlacedEvent` as stable synchronous post-success Bukkit events.
- Added non-empty transaction IDs and event IDs for public event correlation and deduplication.
- Preserved the existing `PlexonSpawnersApi` ServicesManager contract.
- Added `/pspawners diagnostics` and expanded `/pspawners info` with Core mode, module state, Silk, Essence, WildStacker, API and event status.
- Preserved `loadbefore: WildStacker` and added `softdepend: PlexonCore`.
- PlexonCore is compile-only/provided and CI verifies its runtime classes are not shaded.
- Added pinned Core 1.0.0 CI provisioning, Gradle/JUnit verification, reproducible release checks, and `SHA256SUMS.txt` generation.
- Changed production publishing to exact tag-driven `v2.2.0` releases; ordinary `main` pushes no longer publish production releases.
- Preserved 2.1.0 gameplay behavior, PDC keys, exact Essence template, WildStacker one-unit handling, strict Silk rules, conservative config migration, and database-free/stateless runtime.

## 2.1.0 - PlexonCraft Presentation Update

- Redesigned recovered spawner item names and lore around the PlexonCraft primary/secondary color palette.
- Added `<!italic>` to stock item formatting for clean non-italic Minecraft lore.
- Replaced generic plugin-facing lore with concise collectible-style flavor text, creature metadata, placement state, and a PlexonCraft footer.
- Reworked `messages.yml` into the PlexonCraft message theme with consistent success, warning, danger, muted, and secondary accent colors.
- Replaced the old technical `PlexonSpawners` chat prefix with a cleaner player-facing `SPAWNERS »` presentation.
- Improved player-facing recovery and Spawner Essence feedback.
- Added safe migration of the exact stock 2.0.x spawner item style to the new 2.1 theme while preserving customized templates.
- Added safe key-by-key migration of stock 2.0.x messages while preserving customized message values.
- Updated Java fallback item templates to match the new theme when config values are missing.
- Preserved the stable 2.0.2 break ownership, WildStacker compatibility, Silk Touch, Essence, XP, world, and Creative-mode logic.

## 2.0.2 - Silk Touch Qualification Hotfix

- Fixed operators/admins automatically qualifying for spawner recovery without Silk Touch.
- Changed `plexonspawners.bypass.silk` to `default: false`.
- Added `breaking.allow-silk-bypass-permission`, disabled by default.
- A Silk Touch bypass now requires both the explicit config switch and the explicit permission.
- Existing configs safely default to no bypass without requiring a reset.

## 2.0.1 - Spawner Ownership & WildStacker Compatibility

- Fixed conflicts with other HIGHEST-priority spawner break listeners that could prevent PlexonSpawners from producing its own spawner/Essence outcome.
- Added `breaking.take-ownership`, enabled by default, so PlexonSpawners can authoritatively complete managed spawner breaks.
- Added `loadbefore: [WildStacker]` so PlexonSpawners registers its HIGHEST-priority break handler first.
- Added a reflection-based WildStacker compatibility bridge with no hard runtime dependency.
- WildStacker stacks are reduced by exactly one unit per PlexonSpawners-owned break instead of removing the whole physical stack.
- Added safe fallback behavior: if WildStacker's stack API cannot be used or rejects an unstack, PlexonSpawners does not force-delete the stack.
- Restored tool durability consumption for authoritative breaks using Paper's normal item-damage pipeline.
- Authoritative breaks cancel the original Bukkit event after safe physical removal/unstacking, preventing later spawner managers that respect cancellation from creating a duplicate/conflicting outcome.
- Preserved existing Silk Touch, Essence chance, per-mob valuation, XP, world and Creative-mode rules.

## 2.0.0 - GUI & Configuration Update

- Redesigned the admin interface into focused 54-slot pages instead of one compact editor.
- Added dedicated **Spawner Rules**, **Spawner Essence**, and **Mob Values** administration screens.
- Added configurable Essence drop chances and per-mob amount/chance overrides.
- Added GUI control for Essence delivery mode and break/Silk/XP/Creative rules.
- Added configuration versioning and safe 1.x migration.
- Kept the core system stateless and database-free.

## 1.0.0 - Initial Release

- Released standalone Paper 26.2 / Java 25 spawner handling, typed spawners, physical PDC-secured Essence, GUI administration, and Bukkit ServicesManager API.
