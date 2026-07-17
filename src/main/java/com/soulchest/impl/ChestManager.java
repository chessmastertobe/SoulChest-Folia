package com.soulchest.impl;

import com.soulchest.SoulChest;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.FoliaUtil;
import com.soulchest.util.MessageUtils;
import com.soulchest.util.SafeLocationFinder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ChestManager {

    public static final NamespacedKey KEY_CHEST_ID =
            new NamespacedKey("soulchest", "chest_id");
    public static final NamespacedKey KEY_HOLOGRAM_ID =
            new NamespacedKey("soulchest", "hologram_id");

    private final SoulChest plugin;
    private final DataManager dataManager;

    private final Map<String, SoulChestData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();
    private final Map<String, Object> expiryTasks = new ConcurrentHashMap<>(); // Changed to Object for Folia compatibility

    private Object hologramRefreshTask; // Changed from BukkitTask

    public ChestManager(SoulChest plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void onEnable() {
        cleanupOrphanedHolograms();
        loadAllChests();
        startHologramRefresh();
    }

    public void onDisable() {
        // Cancel hologram refresh task
        if (hologramRefreshTask != null) {
            cancelTask(hologramRefreshTask);
        }

        // Cancel all expiry tasks
        for (Object task : expiryTasks.values()) {
            cancelTask(task);
        }
        expiryTasks.clear();

        dataManager.close();
    }

    private void cancelTask(Object task) {
        if (task == null) return;

        try {
            if (task instanceof org.bukkit.scheduler.BukkitTask bukkitTask) {
                bukkitTask.cancel();
            } else if (task.getClass().getName().contains("ScheduledTask")) {
                // Folia ScheduledTask
                task.getClass().getMethod("cancel").invoke(task);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cancel task: " + e.getMessage());
        }
    }

    private void cleanupOrphanedHolograms() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay td) {
                    String id = td.getPersistentDataContainer()
                            .get(KEY_HOLOGRAM_ID, PersistentDataType.STRING);
                    if (id != null) {
                        entity.remove();
                        removed++;
                    }
                }
            }
        }
        if (removed > 0)
            plugin.getLogger().info("[SoulChest] Cleaned up " + removed + " orphaned hologram(s).");
    }

    private void loadAllChests() {
        List<SoulChestData> all = dataManager.loadAllChests();
        int loaded = 0;

        for (SoulChestData chest : all) {
            cache.put(chest.getId(), chest);

            if (!chest.isExpired()) {
                scheduleExpiry(