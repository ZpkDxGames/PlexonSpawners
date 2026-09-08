package com.plexon.spawners.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional WildStacker bridge implemented through reflection so PlexonSpawners
 * can load before WildStacker and still preserve stacked-spawner counts.
 */
public final class WildStackerCompat {
    public enum Result {
        NOT_INSTALLED,
        NOT_STACKED,
        SUCCESS,
        CANCELLED,
        UNAVAILABLE
    }

    private final JavaPlugin plugin;
    private final Consumer<String> degradationReporter;
    private boolean warned;

    public WildStackerCompat(final JavaPlugin plugin) {
        this(plugin, detail -> {});
    }

    public WildStackerCompat(final JavaPlugin plugin, final Consumer<String> degradationReporter) {
        this.plugin = plugin;
        this.degradationReporter = degradationReporter;
    }

    public Result unstackOne(final CreatureSpawner spawner, final Player player) {
        final Plugin wildStacker = plugin.getServer().getPluginManager().getPlugin("WildStacker");
        if (wildStacker == null || !wildStacker.isEnabled()) {
            return Result.NOT_INSTALLED;
        }

        try {
            final ClassLoader loader = wildStacker.getClass().getClassLoader();
            final Class<?> apiClass = Class.forName(
                "com.bgsoftware.wildstacker.api.WildStackerAPI",
                true,
                loader
            );
            final Method getStackedSpawner = apiClass.getMethod("getStackedSpawner", CreatureSpawner.class);
            final Object stackedSpawner = getStackedSpawner.invoke(null, spawner);
            if (stackedSpawner == null) {
                return Result.NOT_STACKED;
            }

            final Method getStackAmount = stackedSpawner.getClass().getMethod("getStackAmount");
            final Method runUnstack = stackedSpawner.getClass().getMethod("runUnstack", int.class, Entity.class);
            final Object result = runUnstack.invoke(stackedSpawner, 1, player);
            if (result == null || !"SUCCESS".equals(result.toString())) {
                return Result.CANCELLED;
            }

            final int remaining = ((Number) getStackAmount.invoke(stackedSpawner)).intValue();
            if (remaining <= 0 && spawner.getBlock().getType() == Material.SPAWNER) {
                spawner.getBlock().setType(Material.AIR, false);
            }
            return Result.SUCCESS;
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            warnOnce(exception);
            return Result.UNAVAILABLE;
        } catch (final LinkageError error) {
            warnOnce(error);
            return Result.UNAVAILABLE;
        }
    }

    public String status() {
        final Plugin wildStacker = plugin.getServer().getPluginManager().getPlugin("WildStacker");
        if (wildStacker == null || !wildStacker.isEnabled()) {
            return "not installed";
        }
        return warned ? "degraded" : "ready";
    }

    private void warnOnce(final Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(
            "WildStacker was detected, but its stack API could not be used. "
                + "PlexonSpawners will not force-remove stacked spawners to avoid deleting a full stack."
        );
        plugin.getLogger().warning("Compatibility error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        degradationReporter.accept("WildStacker detected but its compatibility API is unavailable");
    }
}
