# PlexonSpawners 2.0.1

PlexonSpawners 2.0.1 is a compatibility and break-ownership hotfix for servers that run another spawner manager alongside PlexonSpawners, especially WildStacker.

## Fixed

- Fixed spawner breaks being intercepted by another HIGHEST-priority spawner listener before PlexonSpawners could produce its typed spawner or Spawner Essence result.
- PlexonSpawners can now take authoritative ownership of managed spawner breaks with `breaking.take-ownership: true` (enabled by default).
- Added explicit load ordering before WildStacker so PlexonSpawners can claim the break first.
- Added a stack-safe WildStacker compatibility bridge that removes exactly one spawner from a stack instead of deleting the entire physical stack.
- Restored normal tool durability consumption for Plexon-owned breaks through Paper's item-damage API, including normal Unbreaking/item-break processing.
- PlexonSpawners now cancels a successfully claimed break before later spawner managers can run a second drop pipeline.
- If the WildStacker stack API is unavailable or rejects an unstack, PlexonSpawners does not force-remove the stack, preventing destructive fallback behavior.

## Break ownership

```yaml
breaking:
  take-ownership: true
```

With ownership enabled, PlexonSpawners performs the physical break/unstack itself and exclusively decides the outcome:

- Silk Touch requirement met -> typed spawner (when enabled).
- Silk Touch requirement not met -> roll the configured Spawner Essence chance.
- Failed Essence roll -> no managed drop.

For a WildStacker stack, one player break removes one spawner unit from that stack while PlexonSpawners handles that unit's reward outcome.

## Requirements

- Paper 26.2
- Java 25

## Server file

Replace the previous plugin JAR with `PlexonSpawners-2.0.1.jar` and fully restart the server so plugin load ordering is reapplied.
