# PlexonSpawners 3.0 API

## Bukkit service

`com.plexon.spawners.api.PlexonSpawnersApi` is registered through Bukkit `ServicesManager`.

### Preserved item API

```java
boolean isSpawnerEssence(ItemStack item);
ItemStack createSpawnerEssence(int amount);
boolean isManagedSpawner(ItemStack item);
EntityType getSpawnerType(ItemStack item);
ItemStack createSpawner(EntityType type, int amount);
```

### 3.0 item/tier API

```java
int getSpawnerTier(ItemStack item);
ItemStack createSpawner(EntityType type, int amount, int tier);
```

Schema-1 2.x managed items remain readable and report tier `1`. New 3.x managed items carry schema 2 plus `spawner_tier` PDC.

### Managed runtime API

```java
Optional<ManagedSpawner> getManagedSpawner(Location location);
Optional<ManagedSpawner> getManagedSpawner(UUID id);
List<ManagedSpawner> getManagedSpawnersSnapshot();
```

`ManagedSpawner` is an immutable snapshot containing stable id, world id, coordinates, type, owner, tier, access policy, placement timestamp and lifetime attributed-spawn count. Consumers must not mutate plugin files or physical block PDC directly.

### Spawn-origin API

```java
boolean isSpawnerOrigin(Entity entity);
Optional<UUID> getOriginSpawnerId(Entity entity);
```

This is the supported first-party path for Skills, Jobs, Quests, Keys and other Plexon systems that need to distinguish managed-spawner-origin mobs. Do not infer origin from entity names/lore and do not perform entity-history or per-hit database scans.

## PDC contracts

### Managed spawner items

Plugin namespace keys include:

- `managed_spawner`
- `spawner_type`
- `spawner_schema`
- `spawner_tier` (schema 2)

### Physical managed spawner recovery state

Physical `CreatureSpawner` PDC includes internal managed-instance identity, stable id, owner, tier, access and placement timestamp. These keys are implementation/recovery state; external plugins should prefer the API.

### Spawned entity provenance

Entities attributed to a managed Plexon spawner receive internal PDC origin marker/schema plus the stable source-spawner UUID. External plugins should prefer `isSpawnerOrigin` and `getOriginSpawnerId`.

## Public Bukkit events

### PlexonSpawnerRecoveredEvent

Fires after a typed managed spawner item is produced. It preserves the 2.x event fields and transaction/event ID semantics.

### PlexonSpawnerEssenceAwardedEvent

Fires after Essence chance succeeds and the complete logical amount is delivered. Physical stack splitting does not create additional events.

### PlexonSpawnerPlacedEvent

Fires after the final uncancelled managed placement is accepted. 3.0 applies the physical spawner state at HIGHEST and commits persistent registration only at MONITOR after later protection listeners had an opportunity to cancel.

## Threading and performance

Public events remain synchronous on the primary server thread.

Managed registry lookup is in-memory. Persistence is coalesced separately through one shared coordinator and one writer thread; gameplay consumers must not assume that a file flush occurs on every mutation.

Spawn provenance uses a bounded chunk-index lookup around the spawned entity. It does not enumerate every entity or every spawner in the world.
