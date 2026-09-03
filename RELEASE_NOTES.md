# PlexonSpawners 2.0.2

PlexonSpawners 2.0.2 fixes Silk Touch qualification for operators and administrators.

## Fixed

- Fixed OP/admin players automatically qualifying for spawner recovery even when their held tool had no Silk Touch.
- The configured `breaking.required-silk-touch-level` is now authoritative by default for everyone, including server operators.
- Added `breaking.allow-silk-bypass-permission`, disabled by default.
- `plexonspawners.bypass.silk` is now `default: false` instead of being automatically granted to operators.
- A bypass now works only when BOTH conditions are true:
  - `breaking.allow-silk-bypass-permission: true`
  - the player has `plexonspawners.bypass.silk`
- Existing 2.0/2.0.1 configs that do not contain the new option automatically behave as `false`, so they are protected without requiring a config reset.

## Expected behavior

With:

```yaml
breaking:
  required-silk-touch-level: 1
  allow-silk-bypass-permission: false
```

- No Silk Touch -> failed recovery -> roll Spawner Essence chance.
- Silk Touch I or higher -> typed spawner drop.
- OP/admin status alone -> does not bypass the requirement.

## Requirements

- Paper 26.2
- Java 25

## Server file

Replace the previous plugin JAR with `PlexonSpawners-2.0.2.jar` and fully restart the server.
