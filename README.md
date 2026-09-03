# PlexonSpawners

Standalone spawner handling and physical Spawner Essence for the Plexon plugin family.

## Version 1.0.0

PlexonSpawners owns the player spawner lifecycle without requiring SilkSpawners or a database:

- Qualifying Silk Touch breaks drop a typed PlexonSpawners spawner item.
- Failed Silk Touch breaks can destroy the spawner and produce physical **Spawner Essence**.
- Spawner Essence is a configurable real ItemStack secured with a PersistentDataContainer identity tag.
- Admins can set the exact Essence item from their main hand using `/pspawners admin` or `/pspawners essence set`.
- Per-mob Essence amounts can value different spawners differently.
- Typed spawner items preserve the spawned entity when placed.
- Admin/console give commands are suitable for GUI shop command actions.
- No database and no per-block tracking are required for the core mechanic.

## Requirements

- Paper 26.2
- Java 25

## Commands

- `/pspawners admin`
- `/pspawners info`
- `/pspawners reload`
- `/pspawners give <player> <mob> [amount]`
- `/pspawners essence set`
- `/pspawners essence give <player> [amount]`

All commands are intended for administrators/console. Players interact with spawners and physical Essence naturally in-game.

## Permissions

- `plexonspawners.admin`
- `plexonspawners.admin.gui`
- `plexonspawners.admin.reload`
- `plexonspawners.admin.give`
- `plexonspawners.admin.essence`
- `plexonspawners.bypass.silk`

## GUIPlus / shop integration

A GUIPlus button can execute a console command such as:

```text
pspawners give %player% BLAZE 1
```

GUIPlus can use the configured physical Spawner Essence item as the transaction cost.

## Public API

`PlexonSpawnersApi` is registered through Bukkit's `ServicesManager`, allowing another plugin to validate/create Spawner Essence and managed spawner items without depending on display names or lore.

## Building

```bash
gradle clean build
```

The project targets Java 25 and Paper API `26.2.build.121-stable`.

## Security and performance notes

Spawner Essence identity uses the hidden `plexonspawners:spawner_essence` PDC marker. Display material, name, lore, enchantments, custom model data, and other visible metadata are not used as the identity check.

Managed spawners are tagged with their entity type and also write the entity type into the spawner block-state metadata. Core gameplay is stateless, so the plugin performs no database writes and keeps no global per-block registry.
