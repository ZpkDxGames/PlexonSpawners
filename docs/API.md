# PlexonSpawners 2.2.0 API

## Bukkit service

`com.plexon.spawners.api.PlexonSpawnersApi` remains registered through Bukkit `ServicesManager`.

Preserved methods:

```java
boolean isSpawnerEssence(ItemStack item);
ItemStack createSpawnerEssence(int amount);
boolean isManagedSpawner(ItemStack item);
EntityType getSpawnerType(ItemStack item);
ItemStack createSpawner(EntityType type, int amount);
```

Returned/generated ItemStacks preserve PlexonSpawners' PDC identity. Callers should not infer Essence or spawner identity from visible name/lore.

## Public Bukkit events

### PlexonSpawnerRecoveredEvent

Fires once only after a typed managed spawner item is actually produced. It exposes the player, entity type, logical amount, source location, Silk level, whether explicit bypass was used, whether WildStacker managed the one-unit unstack, event ID, and transaction ID.

### PlexonSpawnerEssenceAwardedEvent

Fires once only after Essence chance succeeds and the complete configured logical amount is delivered. Physical stack splitting does not create additional events. It exposes player, entity type, total amount, delivery mode, source location, event ID, and transaction ID.

### PlexonSpawnerPlacedEvent

Fires once only after an uncancelled managed spawner placement successfully restores the CreatureSpawner type. Vanilla/unmanaged spawner items do not emit this event.

## IDs and threading

Events are dispatched on the primary server thread. Every event has a non-empty `eventId` and `transactionId`.

For a managed break:

```text
transactionId = UUID
recovery eventId = transactionId + ":recovered"
essence eventId  = transactionId + ":essence"
```

Placement receives a separate UUID transaction and `:placed` event ID.

Core does not rebroadcast these Bukkit events; integrations should subscribe to the public event classes directly.
