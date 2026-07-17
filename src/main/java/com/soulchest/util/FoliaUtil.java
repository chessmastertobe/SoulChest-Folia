package com.soulchest.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class FoliaUtil {

    private static final boolean FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            // Not running on Folia
        }
        FOLIA = folia;
    }

    private FoliaUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs a task on the correct scheduler depending on the server.
     * On Folia this uses GlobalRegionScheduler.
     * On Paper/Spigot this falls back to Bukkit scheduler.
     */
    public static void runGlobalTask(Plugin plugin, Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a delayed task on the correct scheduler.
     */
    public static void runGlobalTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a repeating task on the correct scheduler.
     */
    public static void runGlobalTaskTimer(Plugin plugin, Runnable task, long initialDelay, long period) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), initialDelay, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelay, period);
        }
    }
}