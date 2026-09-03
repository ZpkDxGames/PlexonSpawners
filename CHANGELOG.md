# Changelog

## 1.0.0 - Initial Release

- Released the first production version of PlexonSpawners.
- Added standalone Paper 26.2 / Java 25 spawner handling with no SilkSpawners dependency.
- Added configurable Silk Touch level requirements for recovering spawners.
- Added typed spawner items that preserve the spawned entity when placed.
- Added physical, PDC-secured Spawner Essence when a recovery attempt does not meet the Silk Touch requirement.
- Added configurable default and per-mob Essence values.
- Added ground or inventory Essence delivery with safe overflow handling.
- Added an in-game administration GUI for core break rules and the Essence item template.
- Added `/pspawners` admin, info, reload, spawner give, and Essence management commands.
- Added MiniMessage-powered names, lore, and chat messages.
- Added a small Bukkit ServicesManager API for integrations with other Plexon plugins and GUI/shop systems.
- Added configurable world filtering, creative handling, XP behavior, and optional break feedback.
- Added automated Java 25 CI and GitHub Release packaging.

## 0.1.0-SNAPSHOT

- Internal development milestone used to establish the initial source tree.
