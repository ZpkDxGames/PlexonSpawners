# Changelog

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
- Removed references to unrelated plugins and raw implementation/config-path wording from the admin GUI.
- Added fully explained GUI lore so each control describes its gameplay outcome before an admin changes it.
- Added configurable Essence drop chances for failed Silk Touch recovery attempts.
- Added `essence.default-chance` with support for values from `0.0` to `100.0`, including decimal percentages.
- Added per-mob Essence chance overrides alongside per-mob amount overrides.
- Added an in-game mob browser covering living/spawnable entity types.
- Added in-game editing of per-mob amount and chance values, plus reset-to-global-default controls.
- Added GUI control for Essence delivery mode.
- Added GUI controls for break handling, XP drops, Creative-mode drops, qualified spawner drops, and Silk Touch requirement.
- Expanded `config.yml` with detailed descriptions, valid ranges, behavior notes, and examples for every core option.
- Added configuration versioning and safe 1.x migration for the new Essence chance setting.
- Preserved compatibility with 1.x scalar mob amount overrides.
- Kept the core system stateless and database-free.

## 1.0.0 - Initial Release

- Released the first production version of PlexonSpawners.
- Added standalone Paper 26.2 / Java 25 spawner handling.
- Added configurable Silk Touch level requirements for recovering spawners.
- Added typed spawner items that preserve the spawned entity when placed.
- Added physical, PDC-secured Spawner Essence when a recovery attempt does not meet the Silk Touch requirement.
- Added configurable default and per-mob Essence values.
- Added ground or inventory Essence delivery with safe overflow handling.
- Added an in-game administration GUI for core break rules and the Essence item template.
- Added `/pspawners` admin, info, reload, spawner give, and Essence management commands.
- Added MiniMessage-powered names, lore, and chat messages.
- Added a Bukkit ServicesManager API for integrations.
- Added configurable world filtering, creative handling, XP behavior, and optional break feedback.
- Added automated Java 25 CI and GitHub Release packaging.

## 0.1.0-SNAPSHOT

- Internal development milestone used to establish the initial source tree.
