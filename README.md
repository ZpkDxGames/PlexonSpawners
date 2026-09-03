# PlexonSpawners

Standalone spawner handling and physical Spawner Essence for the Plexon plugin family.

## Version 2.1.0

PlexonSpawners manages the player spawner lifecycle directly while presenting recovered items and feedback in the PlexonCraft visual language:

- Qualifying Silk Touch breaks can drop a typed PlexonCraft-styled spawner item.
- Failed Silk Touch breaks can roll a configurable chance to produce physical **Spawner Essence**.
- Global Essence chance and amount can be overridden independently for individual mob types.
- Spawner Essence is a configurable real ItemStack secured with a PersistentDataContainer identity tag.
- Admins can copy the exact Essence item from their main hand.
- Typed spawner items preserve their mob type when placed.
- WildStacker stacks are handled one spawner at a time through the compatibility bridge.
- Core gameplay remains stateless: no database and no per-block registry are required.

## PlexonCraft presentation

2.1.0 refreshes the player-facing layer without changing the stable 2.0.2 break rules.

Recovered spawners use the PlexonCraft primary blue gradient, non-italic lore, compact `›` detail rows, a placement-state indicator, and a subtle PlexonCraft footer. Plugin chat feedback uses the same server palette with dedicated success, warning, danger, muted, and secondary accent colors.

Existing installations receive a safe migration: exact stock 2.0.x item/message defaults are upgraded, while customized values are preserved.

## Admin GUI

Open the complete editor with `/pspawners admin`.

The GUI is organized by administrator tasks instead of raw configuration paths:

### Spawner Rules

Configure authoritative break handling, required Silk Touch level, optional explicit bypass permission, qualified spawner drops, experience behavior, and Creative-mode drops.

### Spawner Essence

Configure the canonical Essence item, global drop chance, global amount, delivery mode, and test items.

### Mob Values

Browse every living/spawnable entity type and configure its individual Essence values in-game:

- Left / Right Click: amount +1 / -1
- Shift Left / Right Click: chance +5% / -5%
- Drop key (Q): remove the mob override and return to global defaults

## Essence chance behavior

The Essence chance is rolled only after a player breaks a spawner without meeting the required Silk Touch level.

```text
Player breaks spawner
        |
        v
Meets Silk Touch requirement?
   |                 |
  Yes               No
   |                 |
Spawner result   Roll Essence chance
                     |
              +------+------+
              |             |
            Pass           Fail
              |             |
        Drop Essence    No Essence
```

`0%` means Essence never drops. `100%` means it is guaranteed. Decimal percentages such as `37.5` are supported.

## WildStacker compatibility

With `breaking.take-ownership: true`, PlexonSpawners claims the managed spawner break and owns the reward outcome. When WildStacker is installed, its stack API is used to remove exactly one spawner unit per normal break rather than deleting the whole physical stack.

## Silk Touch qualification

`breaking.required-silk-touch-level` is authoritative for everyone by default, including OP/admin players.

The optional permission bypass works only when both conditions are true:

- `breaking.allow-silk-bypass-permission: true`
- the player has `plexonspawners.bypass.silk`

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

## Shop integration

Console-driven shops can grant managed typed spawners with commands such as:

```text
pspawners give %player% BLAZE 1
```

The configured Spawner Essence remains a physical item, so compatible item-based economy/shop systems can use the real item as currency.

## Public API

`PlexonSpawnersApi` is registered through Bukkit's `ServicesManager`, allowing another plugin to validate/create genuine Spawner Essence and managed spawner items without relying on visible names or lore.

## Configuration

`config.yml` is fully commented with ranges, outcomes, examples, and explanations for each setting. Migration is conservative: new required settings are added, and exact stock presentation defaults may be refreshed, but customized item/message values are not intentionally overwritten.

## Building

```bash
gradle clean build
```

The project targets Java 25 and Paper API `26.2.build.121-stable`.

## Security and performance notes

Spawner Essence identity uses the hidden `plexonspawners:spawner_essence` PDC marker. Display material, name, lore, enchantments, model data, and other visible metadata are not trusted as identity.

Managed spawners preserve their entity type through item/block metadata. Core gameplay performs no database writes and keeps no global per-block registry.
