package com.plexon.spawners.compat;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional WildStacker bridge. Reflection discovery happens only at provider
 * lifecycle boundaries; gameplay paths use cached public API methods.
 */
public final class WildStackerCompat implements Listener, EntityStackBackend {
    public enum Result {
        NOT_INSTALLED,
        NOT_STACKED,
        SUCCESS,
        CANCELLED,
        UNAVAILABLE
    }

    public record Amount(Result result, int amount) {}

    public interface SpawnGuard {
        boolean shouldUseStackedSpawn(CreatureSpawner spawner);
        boolean shouldCancelEntityStack(LivingEntity existingTarget);
    }

    private enum State {
        NOT_INSTALLED,
        DISABLED,
        READY,
        DEGRADED
    }

    private static final String WILDSTACKER = "WildStacker";
    private static final String API_CLASS = "com.bgsoftware.wildstacker.api.WildStackerAPI";
    private static final String STACKED_SPAWN_EVENT =
        "com.bgsoftware.wildstacker.api.events.SpawnerStackedEntitySpawnEvent";
    private static final String ENTITY_STACK_EVENT =
        "com.bgsoftware.wildstacker.api.events.EntityStackEvent";
    private static final String STACKED_ENTITY_CLASS =
        "com.bgsoftware.wildstacker.api.objects.StackedEntity";
    private static final String STACKED_OBJECT_CLASS =
        "com.bgsoftware.wildstacker.api.objects.StackedObject";

    private final JavaPlugin plugin;
    private final Consumer<String> degradationReporter;
    private final Listener dynamicListener = new Listener() {};

    private Plugin provider;
    private Method getStackedSpawner;
    private Method getStackedEntity;
    private Method getEntityAmount;
    private Method getSpawnersAmount;

    private Class<?> stackedSpawnerClass;
    private Method getSpawnerStackAmount;
    private Method runUnstack;
    private Object successUnstackResult;

    private Method stackedObjectGetAmount;
    private Method stackedObjectSetAmount;
    private Method stackedObjectIncreaseAmount;
    private Method stackedObjectIsSimilar;
    private Method stackedObjectCanGetStacked;
    private Method stackedObjectRemove;

    private SpawnGuard spawnGuard;
    private Method stackedSpawnGetSpawner;
    private Method stackedSpawnSetShouldBeStacked;
    private Method entityStackGetEntity;
    private Method entityStackGetTarget;
    private Method entityStackSetCancelled;
    private Method stackedEntityGetLivingEntity;

    private State state = State.NOT_INSTALLED;
    private boolean warned;
    private String resolutionMode = "none";
    private String lastMergeSummary = "none";

    public WildStackerCompat(final JavaPlugin plugin) {
        this(plugin, detail -> {});
    }

    public WildStackerCompat(final JavaPlugin plugin, final Consumer<String> degradationReporter) {
        this.plugin = plugin;
        this.degradationReporter = degradationReporter;
        refresh();
    }

    /** Resolve the optional provider once for the current lifecycle state. */
    public void refresh() {
        final Plugin detected = plugin.getServer().getPluginManager().getPlugin(WILDSTACKER);
        if (detected == null) {
            clear(State.NOT_INSTALLED, "none", null);
            return;
        }
        if (!detected.isEnabled()) {
            clear(State.DISABLED, "provider disabled", detected);
            return;
        }
        resolve(detected);
    }

    public void setSpawnGuard(final SpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
        if (state == State.READY && provider != null && provider.isEnabled()) {
            try {
                registerSpawnInterceptors(provider.getClass().getClassLoader());
            } catch (final ReflectiveOperationException | RuntimeException exception) {
                degrade(exception);
            } catch (final LinkageError error) {
                degrade(error);
            }
        }
    }

    @EventHandler
    public void onPluginEnable(final PluginEnableEvent event) {
        if (WILDSTACKER.equals(event.getPlugin().getName())) {
            resolve(event.getPlugin());
        }
    }

    @EventHandler
    public void onPluginDisable(final PluginDisableEvent event) {
        if (event.getPlugin() == provider || WILDSTACKER.equals(event.getPlugin().getName())) {
            clear(State.DISABLED, "provider disabled", event.getPlugin());
        }
    }

    public Result unstackOne(final CreatureSpawner spawner, final Player player) {
        if (state == State.NOT_INSTALLED) {
            return Result.NOT_INSTALLED;
        }
        if (state == State.DISABLED || provider == null || !provider.isEnabled()) {
            return Result.UNAVAILABLE;
        }
        if (state != State.READY || getStackedSpawner == null) {
            return Result.UNAVAILABLE;
        }

        try {
            final Object stackedSpawner = getStackedSpawner.invoke(null, spawner);
            if (stackedSpawner == null) {
                return Result.NOT_STACKED;
            }
            ensureSpawnerAccessors(stackedSpawner.getClass());
            final Object result = runUnstack.invoke(stackedSpawner, 1, player);
            if (!isSuccess(result)) {
                return Result.CANCELLED;
            }
            final int remaining = ((Number) getSpawnerStackAmount.invoke(stackedSpawner)).intValue();
            if (remaining <= 0 && spawner.getBlock().getType() == Material.SPAWNER) {
                spawner.getBlock().setType(Material.AIR, false);
            }
            return Result.SUCCESS;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return Result.UNAVAILABLE;
        } catch (final LinkageError error) {
            degrade(error);
            return Result.UNAVAILABLE;
        }
    }

    public Amount getLogicalEntityAmount(final LivingEntity entity) {
        if (state == State.NOT_INSTALLED) {
            return new Amount(Result.NOT_INSTALLED, 1);
        }
        if (state != State.READY || provider == null || !provider.isEnabled() || getEntityAmount == null) {
            return new Amount(Result.UNAVAILABLE, 0);
        }
        try {
            final int amount = ((Number) getEntityAmount.invoke(null, entity)).intValue();
            return new Amount(Result.SUCCESS, Math.max(1, amount));
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return new Amount(Result.UNAVAILABLE, 0);
        } catch (final LinkageError error) {
            degrade(error);
            return new Amount(Result.UNAVAILABLE, 0);
        }
    }

    public Result resizeLogicalEntity(final LivingEntity entity, final int amount) {
        final MutationResult result = setLogicalAmount(entity, amount);
        return switch (result) {
            case SUCCESS -> Result.SUCCESS;
            case INCOMPATIBLE -> Result.CANCELLED;
            case UNAVAILABLE -> state == State.NOT_INSTALLED ? Result.NOT_INSTALLED : Result.UNAVAILABLE;
        };
    }

    public Amount getLogicalSpawnerAmount(final CreatureSpawner spawner) {
        if (state == State.NOT_INSTALLED) {
            return new Amount(Result.NOT_INSTALLED, 1);
        }
        if (state != State.READY || provider == null || !provider.isEnabled() || getSpawnersAmount == null) {
            return new Amount(Result.UNAVAILABLE, 0);
        }
        try {
            final int amount = ((Number) getSpawnersAmount.invoke(null, spawner)).intValue();
            return new Amount(Result.SUCCESS, Math.max(1, amount));
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return new Amount(Result.UNAVAILABLE, 0);
        } catch (final LinkageError error) {
            degrade(error);
            return new Amount(Result.UNAVAILABLE, 0);
        }
    }

    @Override
    public boolean available() {
        return methodCacheReady();
    }

    @Override
    public String backendName() {
        return methodCacheReady() ? "WildStacker entity API" : "physical fallback";
    }

    @Override
    public LogicalAmount logicalAmount(final LivingEntity entity) {
        final Amount amount = getLogicalEntityAmount(entity);
        return amount.result() == Result.SUCCESS
            ? LogicalAmount.available(amount.amount())
            : LogicalAmount.unavailable();
    }

    @Override
    public boolean compatible(
        final LivingEntity source,
        final LivingEntity target,
        final int contribution
    ) {
        if (source == null || target == null || source == target || contribution < 1
            || source.getType() != target.getType() || !methodCacheReady()) {
            return false;
        }
        try {
            final Object sourceStack = getStackedEntity.invoke(null, source);
            final Object targetStack = getStackedEntity.invoke(null, target);
            if (sourceStack == null || targetStack == null) {
                return false;
            }
            final boolean similar = (Boolean) stackedObjectIsSimilar.invoke(sourceStack, targetStack);
            if (!similar) {
                return false;
            }
            final Object check = stackedObjectCanGetStacked.invoke(targetStack, contribution);
            return check instanceof Enum<?> enumResult && "SUCCESS".equals(enumResult.name());
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return false;
        } catch (final LinkageError error) {
            degrade(error);
            return false;
        }
    }

    @Override
    public MutationResult mergeInto(
        final LivingEntity source,
        final LivingEntity target,
        final int contribution
    ) {
        if (!compatible(source, target, contribution)) {
            return methodCacheReady() ? MutationResult.INCOMPATIBLE : MutationResult.UNAVAILABLE;
        }
        Object targetStack = null;
        int originalAmount = -1;
        try {
            final Object sourceStack = getStackedEntity.invoke(null, source);
            targetStack = getStackedEntity.invoke(null, target);
            if (sourceStack == null || targetStack == null) {
                return MutationResult.UNAVAILABLE;
            }
            originalAmount = ((Number) stackedObjectGetAmount.invoke(targetStack)).intValue();
            final int expected = safeAdd(originalAmount, contribution);
            final int updated = ((Number) stackedObjectIncreaseAmount.invoke(targetStack, contribution, true)).intValue();
            if (updated != expected) {
                stackedObjectSetAmount.invoke(targetStack, originalAmount, true);
                return MutationResult.UNAVAILABLE;
            }
            try {
                stackedObjectRemove.invoke(sourceStack);
            } catch (final ReflectiveOperationException | RuntimeException removalFailure) {
                stackedObjectSetAmount.invoke(targetStack, originalAmount, true);
                throw removalFailure;
            }
            lastMergeSummary = target.getUniqueId() + " +" + contribution + " -> x" + updated;
            return MutationResult.SUCCESS;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            if (targetStack != null && originalAmount > 0) {
                try {
                    stackedObjectSetAmount.invoke(targetStack, originalAmount, true);
                } catch (final ReflectiveOperationException | RuntimeException ignored) {
                    // Provider is about to be marked degraded; no further mutation is attempted.
                }
            }
            degrade(exception);
            return MutationResult.UNAVAILABLE;
        } catch (final LinkageError error) {
            degrade(error);
            return MutationResult.UNAVAILABLE;
        }
    }

    @Override
    public MutationResult setLogicalAmount(final LivingEntity entity, final int amount) {
        if (amount < 1 || !methodCacheReady()) {
            return MutationResult.UNAVAILABLE;
        }
        try {
            final Object stackedEntity = getStackedEntity.invoke(null, entity);
            if (stackedEntity == null) {
                return MutationResult.UNAVAILABLE;
            }
            stackedObjectSetAmount.invoke(stackedEntity, amount, true);
            return MutationResult.SUCCESS;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return MutationResult.UNAVAILABLE;
        } catch (final LinkageError error) {
            degrade(error);
            return MutationResult.UNAVAILABLE;
        }
    }

    @Override
    public String status() {
        return switch (state) {
            case NOT_INSTALLED -> "not installed";
            case DISABLED -> "disabled";
            case READY -> "ready";
            case DEGRADED -> "degraded";
        };
    }

    public String resolutionMode() {
        return resolutionMode;
    }

    public String lastMergeSummary() {
        return lastMergeSummary;
    }

    public boolean installed() {
        return state != State.NOT_INSTALLED;
    }

    public boolean methodCacheReady() {
        return state == State.READY
            && provider != null
            && provider.isEnabled()
            && getStackedSpawner != null
            && getStackedEntity != null
            && getEntityAmount != null
            && getSpawnersAmount != null
            && stackedObjectGetAmount != null
            && stackedObjectSetAmount != null
            && stackedObjectIncreaseAmount != null
            && stackedObjectIsSimilar != null
            && stackedObjectCanGetStacked != null
            && stackedObjectRemove != null;
    }

    private void resolve(final Plugin detected) {
        HandlerList.unregisterAll(dynamicListener);
        provider = detected;
        warned = false;
        stackedSpawnerClass = null;
        getSpawnerStackAmount = null;
        runUnstack = null;
        successUnstackResult = null;
        lastMergeSummary = "none";
        clearEventAccessors();

        try {
            final ClassLoader loader = detected.getClass().getClassLoader();
            final Class<?> apiClass = Class.forName(API_CLASS, true, loader);
            getStackedSpawner = apiClass.getMethod("getStackedSpawner", CreatureSpawner.class);
            getStackedEntity = apiClass.getMethod("getStackedEntity", LivingEntity.class);
            getEntityAmount = apiClass.getMethod("getEntityAmount", LivingEntity.class);
            getSpawnersAmount = apiClass.getMethod("getSpawnersAmount", CreatureSpawner.class);

            final Class<?> stackedObjectClass = Class.forName(STACKED_OBJECT_CLASS, true, loader);
            final Class<?> stackedEntityClass = Class.forName(STACKED_ENTITY_CLASS, true, loader);
            stackedObjectGetAmount = stackedObjectClass.getMethod("getStackAmount");
            stackedObjectSetAmount = stackedObjectClass.getMethod("setStackAmount", int.class, boolean.class);
            stackedObjectIncreaseAmount = stackedObjectClass.getMethod("increaseStackAmount", int.class, boolean.class);
            stackedObjectIsSimilar = stackedObjectClass.getMethod("isSimilar", stackedObjectClass);
            stackedObjectCanGetStacked = stackedObjectClass.getMethod("canGetStacked", int.class);
            stackedObjectRemove = stackedObjectClass.getMethod("remove");
            stackedEntityGetLivingEntity = stackedEntityClass.getMethod("getLivingEntity");

            state = State.READY;
            resolutionMode = "cached public API reflection";
            if (spawnGuard != null) {
                registerSpawnInterceptors(loader);
            }
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
        } catch (final LinkageError error) {
            degrade(error);
        }
    }

    private void registerSpawnInterceptors(final ClassLoader loader) throws ReflectiveOperationException {
        HandlerList.unregisterAll(dynamicListener);

        final Class<? extends Event> stackedSpawnClass =
            Class.forName(STACKED_SPAWN_EVENT, true, loader).asSubclass(Event.class);
        stackedSpawnGetSpawner = stackedSpawnClass.getMethod("getSpawner");
        stackedSpawnSetShouldBeStacked = stackedSpawnClass.getMethod("setShouldBeStacked", boolean.class);

        final Class<? extends Event> entityStackClass =
            Class.forName(ENTITY_STACK_EVENT, true, loader).asSubclass(Event.class);
        entityStackGetEntity = entityStackClass.getMethod("getEntity");
        entityStackGetTarget = entityStackClass.getMethod("getTarget");
        entityStackSetCancelled = entityStackClass.getMethod("setCancelled", boolean.class);

        plugin.getServer().getPluginManager().registerEvent(
            stackedSpawnClass,
            dynamicListener,
            EventPriority.HIGHEST,
            (ignored, event) -> handleStackedSpawnEvent(event),
            plugin,
            false
        );
        plugin.getServer().getPluginManager().registerEvent(
            entityStackClass,
            dynamicListener,
            EventPriority.HIGHEST,
            (ignored, event) -> handleEntityStackEvent(event),
            plugin,
            false
        );
        resolutionMode = "cached public API reflection (entity + spawner guard events ready)";
    }

    private void handleStackedSpawnEvent(final Event event) {
        if (spawnGuard == null || stackedSpawnGetSpawner == null || stackedSpawnSetShouldBeStacked == null) {
            return;
        }
        try {
            final CreatureSpawner spawner = (CreatureSpawner) stackedSpawnGetSpawner.invoke(event);
            if (!spawnGuard.shouldUseStackedSpawn(spawner)) {
                stackedSpawnSetShouldBeStacked.invoke(event, false);
            }
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
        } catch (final LinkageError error) {
            degrade(error);
        }
    }

    private void handleEntityStackEvent(final Event event) {
        if (spawnGuard == null || entityStackGetEntity == null || entityStackGetTarget == null
            || entityStackSetCancelled == null || stackedEntityGetLivingEntity == null) {
            return;
        }
        try {
            final Object receivingStack = entityStackGetEntity.invoke(event);
            // Resolve both sides once so the public event contract is validated and cached.
            final Object sourceStack = entityStackGetTarget.invoke(event);
            final LivingEntity existingTarget = (LivingEntity) stackedEntityGetLivingEntity.invoke(receivingStack);
            stackedEntityGetLivingEntity.invoke(sourceStack);
            if (spawnGuard.shouldCancelEntityStack(existingTarget)) {
                entityStackSetCancelled.invoke(event, true);
            }
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
        } catch (final LinkageError error) {
            degrade(error);
        }
    }

    private void ensureSpawnerAccessors(final Class<?> actualClass) throws NoSuchMethodException {
        if (actualClass == stackedSpawnerClass && getSpawnerStackAmount != null && runUnstack != null) {
            return;
        }
        stackedSpawnerClass = actualClass;
        getSpawnerStackAmount = actualClass.getMethod("getStackAmount");
        runUnstack = actualClass.getMethod("runUnstack", int.class, Entity.class);
        resolutionMode = "cached public API reflection (spawner migration accessors ready)";
    }

    private boolean isSuccess(final Object result) {
        if (result == null) {
            return false;
        }
        if (successUnstackResult != null) {
            return successUnstackResult == result || successUnstackResult.equals(result);
        }
        if (result instanceof Enum<?> enumResult && "SUCCESS".equals(enumResult.name())) {
            successUnstackResult = result;
            return true;
        }
        return false;
    }

    private void degrade(final Throwable failure) {
        state = State.DEGRADED;
        resolutionMode = "degraded: " + failure.getClass().getSimpleName();
        HandlerList.unregisterAll(dynamicListener);
        if (!warned) {
            warned = true;
            final String detail = "WildStacker public API integration degraded; PlexonSpawners will use bounded physical entity fallback where possible ("
                + failure.getClass().getSimpleName() + ").";
            plugin.getLogger().warning(detail);
            degradationReporter.accept(detail);
        }
    }

    private void clear(final State nextState, final String mode, final Plugin nextProvider) {
        HandlerList.unregisterAll(dynamicListener);
        provider = nextProvider;
        state = nextState;
        resolutionMode = mode;
        warned = false;
        getStackedSpawner = null;
        getStackedEntity = null;
        getEntityAmount = null;
        getSpawnersAmount = null;
        stackedSpawnerClass = null;
        getSpawnerStackAmount = null;
        runUnstack = null;
        successUnstackResult = null;
        stackedObjectGetAmount = null;
        stackedObjectSetAmount = null;
        stackedObjectIncreaseAmount = null;
        stackedObjectIsSimilar = null;
        stackedObjectCanGetStacked = null;
        stackedObjectRemove = null;
        clearEventAccessors();
        lastMergeSummary = "none";
    }

    private void clearEventAccessors() {
        stackedSpawnGetSpawner = null;
        stackedSpawnSetShouldBeStacked = null;
        entityStackGetEntity = null;
        entityStackGetTarget = null;
        entityStackSetCancelled = null;
        stackedEntityGetLivingEntity = null;
    }

    private static int safeAdd(final int left, final int right) {
        final long total = (long) left + (long) right;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
