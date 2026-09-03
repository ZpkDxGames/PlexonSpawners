# PlexonSpawners 2.1.0

PlexonSpawners 2.1.0 is a presentation and migration update that brings recovered spawner items and plugin messages in line with the PlexonCraft visual language while preserving the stable 2.0.2 break logic.

## PlexonCraft item theme

- Redesigned recovered spawner names with the PlexonCraft primary blue gradient.
- Added `<!italic>` to item name/lore defaults so Minecraft's default italic lore style does not interfere with the design.
- Replaced generic implementation-facing lore with a compact collectible-style description.
- Added concise `›` metadata rows for creature type and placement state.
- Added a subtle PlexonCraft footer instead of the old `Managed by PlexonSpawners` line.
- Updated Java fallback templates so missing config values no longer fall back to the old purple style.

## Message theme

- Reworked the default message prefix into a clean PlexonCraft-styled `SPAWNERS »` header.
- Added consistent success, warning, danger, muted and secondary accent colors.
- Shortened technical/admin wording where player-facing feedback should stay concise.
- Improved recovery and Essence-drop feedback to feel like part of the server rather than raw plugin output.

## Safe migration

2.1.0 includes conservative migration for existing installations:

- The old stock 2.0.x recovered-spawner name/lore is upgraded automatically.
- Customized spawner item templates are preserved.
- Old stock 2.0.x messages are upgraded key-by-key to the PlexonCraft theme.
- Customized message values are preserved instead of being overwritten.

This allows an existing server to receive the new theme without deleting its configuration files.

## Gameplay stability

The working 2.0.2 gameplay behavior is unchanged:

- Authoritative spawner break ownership remains enabled by configuration.
- WildStacker-safe one-at-a-time unstacking remains intact.
- Silk Touch requirements remain authoritative for OP/admin players by default.
- Spawner Essence chances, amounts, delivery, world restrictions, XP and Creative-mode rules remain unchanged.

## Requirements

- Paper 26.2
- Java 25

## Server file

Replace the previous plugin JAR with `PlexonSpawners-2.1.0.jar` and fully restart the server.
