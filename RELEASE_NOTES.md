# PlexonSpawners 2.0.0

PlexonSpawners 2.0 focuses on administration quality, configuration clarity, and making Spawner Essence a tunable physical currency rather than a guaranteed fallback item.

## Highlights

- Completely redesigned `/pspawners admin` interface with separate **Spawner Rules**, **Spawner Essence**, and **Mob Values** pages.
- Removed unrelated plugin references and raw configuration-path wording from the GUI.
- Added detailed explanations directly to each GUI control so admins can understand the outcome before changing a setting.
- Added configurable Essence drop chance after a failed Silk Touch recovery.
- Added global and per-mob Essence chances from `0%` to `100%`, including decimal percentages.
- Added per-mob amount + chance editing directly in-game.
- Added reset-to-global behavior for mob overrides.
- Added in-game controls for Essence delivery, break handling, XP drops, Creative drops, qualified spawner drops, and Silk Touch requirement.
- Reworked `config.yml` into a fully documented reference with ranges, examples, and behavior notes.
- Added safe migration for 1.x configurations while preserving existing custom values.
- Maintains the database-free, low-overhead design of the 1.0 release.

## Essence chance example

With:

```yaml
essence:
  default-chance: 35.0
  default-amount: 1
```

A player who breaks a spawner without meeting the configured Silk Touch requirement has a 35% chance to receive one Spawner Essence. Mob-specific settings can override either value.

## Requirements

- Paper 26.2
- Java 25

## Server file

Install `PlexonSpawners-2.0.0.jar` in the server's `plugins` directory and restart the server.
