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
 * Optional WildStacker bridge. Discovery happens at plugin lifecycle boundaries;
 * gameplay calls only use cached reflective accessors from WildStacker's public API.
 */
public final class WildStackerCompat implements Listener {
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

    private final JavaPlugin plugin;
    private final Consumer<String> degradationReporter;
    private final Listener dynamicListener = new Listener() {};

    private Plugin provider;
    private Method getStackedSpawner;
    private Method getStackedEntity;
    private Method getEntityAmount;
    private Method getSpawnersAmount;
    private Method setEntityStackAmount;
    private Class<?> stackedSpawnerClass;
    private Method getStackAmount;
    private Method runUnstack;
    private Object successResult;
    private SpawnGuard spawnGuard;
    private Method stackedSpawnGetSpawner;
    private Method stackedSpawnSetShouldBeStacked;
    private Method entityStackGetEntity;
    private Method entityStackSetCancelled;
    private Method stackedEntityGetLivingEntity;
    private State state = State.NOT_INSTALLED;
    private boolean warned;
    private String resolutionMode = "none";

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

            ensureStackAccessors(stackedSpawner.getClass());
            final Object result = runUnstack.invoke(stackedSpawner, 1, player);
            if (!isSuccess(result)) {
                return Result.CANCELLED;
            }

            final int remaining = ((Number) getStackAmount.invoke(stackedSpawner)).intValue();
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

    /**
     * Resize a WildStacker entity through its public StackedEntity#setStackAmount API.
     * This is used before a pending spawner entity is admitted to the world so an
     * exact nearby logical cap can be retained without degrading to loose entities.
     */
    public Result resizeLogicalEntity(final LivingEntity entity, final int amount) {
        if (state == State.NOT_INSTALLED) {
            return Result.NOT_INSTALLED;
        }
        if (amount < 1 || state != State.READY || provider == null || !provider.isEnabled()
            || getStackedEntity == null || setEntityStackAmount == null) {
            return Result.UNAVAILABLE;
        }
        try {
            final Object stackedEntity = getStackedEntity.invoke(null, entity);
            if (stackedEntity == null) {
                return Result.NOT_STACKED;
            }
            setEntityStackAmount.invoke(stackedEntity, amount, true);
            return Result.SUCCESS;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
            return Result.UNAVAILABLE;
        } catch (final LinkageError error) {
            degrade(error);
            return Result.UNAVAILABLE;
        }
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

    public boolean installed() {
        return state != State.NOT_INSTALLED;
    }

    public boolean methodCacheReady() {
        return state == State.READY
            && getStackedSpawner != null
            && getStackedEntity != null
            && getEntityAmount != null
            && getSpawnersAmount != null
            && setEntityStackAmount != null;
    }

    private void resolve(final Plugin detected) {
        HandlerList.unregisterAll(dynamicListener);
        provider = detected;
        warned = false;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;
        clearEventAccessors();

        try {
            final ClassLoader loader = detected.getClass().getClassLoader();
            final Class<?> apiClass = Class.forName(API_CLASS, true, loader);
            getStackedSpawner = apiClass.getMethod("getStackedSpawner", CreatureSpawner.class);
            getStackedEntity = apiClass.getMethod("getStackedEntity", LivingEntity.class);
            getEntityAmount = apiClass.getMethod("getEntityAmount", LivingEntity.class);
            getSpawnersAmount = apiClass.getMethod("getSpawnersAmount", CreatureSpawner.class);
            final Class<?> stackedEntityClass = Class.forName(STACKED_ENTITY_CLASS, true, loader);
            setEntityStackAmount = stackedEntityClass.getMethod("setStackAmount", int.class, boolean.class);
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
        entityStackSetCancelled = entityStackClass.getMethod("setCancelled", boolean.class);
        final Class<?> stackedEntityClass = Class.forName(STACKED_ENTITY_CLASS, true, loader);
        stackedEntityGetLivingEntity = stackedEntityClass.getMethod("getLivingEntity");

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
        if (spawnGuard == null || entityStackGetEntity == null
            || entityStackSetCancelled == null || stackedEntityGetLivingEntity == null) {
            return;
        }
        try {
            final Object stackedEntity = entityStackGetEntity.invoke(event);
            final LivingEntity livingEntity = (LivingEntity) stackedEntityGetLivingEntity.invoke(stackedEntity);
            if (spawnGuard.shouldCancelEntityStack(livingEntity)) {
                entityStackSetCancelled.invoke(event, true);
            }
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            degrade(exception);
        } catch (final LinkageError error) {
            degrade(error);
        }
    }

    private void ensureStackAccessors(final Class<?> actualClass) throws NoSuchMethodException {
        if (actualClass == stackedSpawnerClass && getStackAmount != null && runUnstack != null) {
            return;
        }
        stackedSpawnerClass = actualClass;
        getStackAmount = actualClass.getMethod("getStackAmount");
        runUnstack = actualClass.getMethod("runUnstack", int.class, Entity.class);
        resolutionMode = "cached public API reflection (stack accessors ready)";
    }

    private boolean isSuccess(final Object result) {
        if (result == null) {
            return false;
        }
        if (successResult != null) {
            return successResult == result || successResult.equals(result);
        }
        if (result instanceof Enum<?> enumResult && "SUCCESS".equals(enumResult.name())) {
            successResult = result;
            return true;
        }
        if ("SUCCESS".equals(result.toString())) {
            successResult = result;
            return true;
        }
        return false;
    }

    private void clear(final State nextState, final String mode, final Plugin nextProvider) {
        HandlerList.unregisterAll(dynamicListener);
        provider = nextProvider;
        getStackedSpawner = null;
        getStackedEntity = null;
        getEntityAmount = null;
        getSpawnersAmount = null;
        setEntityStackAmount = null;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;
        clearEventAccessors();
        state = nextState;
        resolutionMode = mode;
    }

    private void clearEventAccessors() {
        stackedSpawnGetSpawner = null;
        stackedSpawnSetShouldBeStacked = null;
        entityStackGetEntity = null;
        entityStackSetCancelled = null;
        stackedEntityGetLivingEntity = null;
    }

    private void degrade(final Throwable throwable) {
        HandlerList.unregisterAll(dynamicListener);
        state = State.DEGRADED;
        resolutionMode = "degraded";
        getStackedSpawner = null;
        getStackedEntity = null;
        getEntityAmount = null;
        getSpawnersAmount = null;
        setEntityStackAmount = null;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;
        clearEventAccessors();
        warnOnce(throwable);
    }

    private void warnOnce(final Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(
            "WildStacker was detected, but its public stack API could not be used. "
                + "PlexonSpawners will fail closed for managed stack-sensitive operations."
        );
        plugin.getLogger().warning(
            "Compatibility error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
        );
        degradationReporter.accept("WildStacker detected but its compatibility API is unavailable");
    }
}
