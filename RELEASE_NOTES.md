# PlexonSpawners 1.0.0

The first production release of **PlexonSpawners**, a lightweight standalone spawner-management plugin for Paper 26.2.

## Highlights

- Recover typed spawners with a configurable Silk Touch requirement.
- Failed recovery can produce configurable physical **Spawner Essence** instead of a spawner.
- Essence is secured with a hidden PersistentDataContainer marker, so display-name/lore copies do not count as genuine currency.
- Typed spawner items preserve their mob type when placed again.
- Configure the Essence template directly from an item in an administrator's hand.
- Use `/pspawners admin` for the in-game administration GUI.
- Use console-friendly give commands for GUIPlus/shop integrations.
- MiniMessage support for spawner item names, lore, and plugin messages.
- No database or per-block persistence overhead is required for the core mechanic.

## Requirements

- Paper 26.2
- Java 25

## Server file

Install `PlexonSpawners-1.0.0.jar` in the server's `plugins` directory and restart the server.
