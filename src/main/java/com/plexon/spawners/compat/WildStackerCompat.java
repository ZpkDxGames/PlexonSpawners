package com.plexon.spawners.compat;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional WildStacker bridge. Discovery happens at plugin lifecycle boundaries;
 * gameplay calls only use cached reflective accessors.
 */
public final class WildStackerCompat implements Listener {
    public enum Result {
        NOT_INSTALLED,
        NOT_STACKED,
        SUCCESS,
        CANCELLED,
        UNAVAILABLE
    }

    private enum State {
        NOT_INSTALLED,
        READY,
        DEGRADED
    }

    private static final String WILDSTACKER = "WildStacker";
    private static final String API_CLASS = "com.bgsoftware.wildstacker.api.WildStackerAPI";

    private final JavaPlugin plugin;
    private final Consumer<String> degradationReporter;

    private Plugin provider;
    private Method getStackedSpawner;
    private Class<?> stackedSpawnerClass;
    private Method getStackAmount;
    private Method runUnstack;
    private Object successResult;
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
        if (detected == null || !detected.isEnabled()) {
            clear(State.NOT_INSTALLED, "none");
            return;
        }
        resolve(detected);
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
            clear(State.NOT_INSTALLED, "provider disabled");
        }
    }

    public Result unstackOne(final CreatureSpawner spawner, final Player player) {
        if (state == State.NOT_INSTALLED || provider == null || !provider.isEnabled()) {
            return Result.NOT_INSTALLED;
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

    public String status() {
        return switch (state) {
            case NOT_INSTALLED -> "not installed";
            case READY -> "ready";
            case DEGRADED -> "degraded";
        };
    }

    public String resolutionMode() {
        return resolutionMode;
    }

    public boolean methodCacheReady() {
        return state == State.READY && getStackedSpawner != null;
    }

    private void resolve(final Plugin detected) {
        provider = detected;
        warned = false;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;

        try {
            final ClassLoader loader = detected.getClass().getClassLoader();
            final Class<?> apiClass = Class.forName(API_CLASS, true, loader);
            getStackedSpawner = apiClass.getMethod("getStackedSpawner", CreatureSpawner.class);
            state = State.READY;
            resolutionMode = "cached reflection";
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
        resolutionMode = "cached reflection (stack accessors ready)";
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

    private void clear(final State nextState, final String mode) {
        provider = null;
        getStackedSpawner = null;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;
        state = nextState;
        resolutionMode = mode;
    }

    private void degrade(final Throwable throwable) {
        state = State.DEGRADED;
        resolutionMode = "degraded";
        getStackedSpawner = null;
        stackedSpawnerClass = null;
        getStackAmount = null;
        runUnstack = null;
        successResult = null;
        warnOnce(throwable);
    }

    private void warnOnce(final Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(
            "WildStacker was detected, but its stack API could not be used. "
                + "PlexonSpawners will fail closed to avoid deleting a full stack."
        );
        plugin.getLogger().warning(
            "Compatibility error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
        );
        degradationReporter.accept("WildStacker detected but its compatibility API is unavailable");
    }
}
