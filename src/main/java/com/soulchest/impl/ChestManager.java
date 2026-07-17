package com.soulchest.impl;

import com.soulchest.SoulChest;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import com.soulchest.util.SafeLocationFinder;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChestManager {

    public static final NamespacedKey KEY_CHEST_ID    = new NamespacedKey("soulchest", "chest_id");
    public static final NamespacedKey KEY_HOLOGRAM_ID = new NamespacedKey("soulchest", "hologram_id");

    private final SoulChest   plugin;
    private final DataManager dataManager;

    private final Map<String, SoulChestData> cache       = new ConcurrentHashMap<>();
    private final Map<String, UUID>          holograms   = new ConcurrentHashMap<>();
    private final Map<String, ScheduledTask> expiryTasks = new ConcurrentHashMap<>();

    private ScheduledTask hologramRefreshTask;

    public ChestManager(SoulChest plugin, DataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
    }

    public void onEnable() {
        cleanupOrphanedHolograms();
        loadAllChests();
        startHologramRefresh();
    }

    public void onDisable() {
        if (hologramRefreshTask != null) {
            hologramRefreshTask.cancel();
        }
        for (ScheduledTask task : expiryTasks.values()) {
            if (task != null) task.cancel();
        }
        expiryTasks.clear();
        dataManager.close();
    }

    private void cleanupOrphanedHolograms() {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            int removed = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                    String id = entity.getPersistentDataContainer()
                            .get(KEY_HOLOGRAM_ID, PersistentDataType.STRING);
                    if (id != null) {
                        entity.remove();
                        removed++;
                    }
                }
            }
            if (removed > 0) {
                plugin.getLogger().info("[SoulChest] Cleaned up " + removed + " orphaned hologram(s).");
            }
        });
    }

    private void loadAllChests() {
        List<SoulChestData> all = dataManager.loadAllChests();
        int loaded = 0;

        for (SoulChestData chest : all) {
            cache.put(chest.getId(), chest);

            if (!chest.isExpired()) {
                scheduleExpiry(chest);
                spawnHologram(chest);   // Now safely scheduled inside spawnHologram()
                loaded++;
            } else {
                expireChest(chest);
            }
        }
        plugin.getLogger().info("[SoulChest] Loaded " + loaded + " SoulChest(s) from database.");
    }

    public void spawnChest(SoulChestData data) {
        final Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("[SoulChest] Cannot spawn - world '" 
                    + data.getWorldName() + "' not loaded.");
            return;
        }

        enforceLimit(data.getOwnerUUID());

        final SoulChestData finalData = data;

        Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
            Block block = loc.getBlock();
            block.setType(Material.CHEST, false);
            tagChestBlock(block, finalData.getId());

            if (plugin.getConfig().getBoolean("general.spawn-effects", true)) {
                loc.getWorld().spawnParticle(Particle.SOUL,
                        loc.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_PLACE,
                        SoundCategory.BLOCKS, 1f, 0.8f);
            }
        });

        cache.put(data.getId(), data);
        dataManager.saveChest(data);

        if (plugin.getConfig().getBoolean("hologram.enabled", true))
            spawnHologram(data);

        scheduleExpiry(data);
    }

    public SoulChestData relocateChest(SoulChestData data, Location requestedLoc) {
        Location safeLoc = SafeLocationFinder.findSafe(requestedLoc, 1);
        final Location newLoc = (safeLoc != null) ? safeLoc : requestedLoc;

        Location oldLoc = data.toLocation();
        if (oldLoc != null && oldLoc.getWorld() != null
                && oldLoc.getBlock().getType() == Material.CHEST) {
            Bukkit.getRegionScheduler().execute(plugin, oldLoc, () -> {
                oldLoc.getBlock().setType(Material.AIR, false);
            });
        }

        removeHologram(data.getId());

        Bukkit.getRegionScheduler().execute(plugin, newLoc, () -> {
            newLoc.getBlock().setType(Material.CHEST, false);
            tagChestBlock(newLoc.getBlock(), data.getId());
        });

        SoulChestData updated = data.withLocation(newLoc);
        cache.put(updated.getId(), updated);
        dataManager.saveChest(updated);

        if (plugin.getConfig().getBoolean("hologram.enabled", true))
            spawnHologram(updated);

        return updated;
    }

    public void removeChest(SoulChestData data, boolean dropContents) {
        ScheduledTask task = expiryTasks.remove(data.getId());
        if (task != null) task.cancel();

        removeHologram(data.getId());

        Location loc = data.toLocation();
        if (loc != null && loc.getWorld() != null) {
            Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
                Block block = loc.getBlock();
                if (block.getType() == Material.CHEST) {
                    block.setType(Material.AIR, false);
                }
                if (dropContents) {
                    dropAllItems(data, loc);
                }
            });
        } else if (dropContents) {
            dropAllItems(data, data.toLocation());
        }

        cache.remove(data.getId());
        dataManager.deleteChest(data.getOwnerUUID(), data.getId());
    }

    public void deleteChest(SoulChestData data) {
        removeChest(data, false);
    }

    private void expireChest(SoulChestData data) {
        boolean drop = plugin.getConfig().getBoolean("general.drop-items-on-expire", true);
        removeChest(data, drop);

        Player owner = Bukkit.getPlayer(data.getOwnerUUID());
        if (owner != null) {
            String msg = cfg("messages.chest-expired", "")
                    .replace("{world}", data.getWorldName())
                    .replace("{x}", String.valueOf(data.getX()))
                    .replace("{y}", String.valueOf(data.getY()))
                    .replace("{z}", String.valueOf(data.getZ()));
            MessageUtils.send(owner, prefix() + msg);
        }
    }

    public void lootChest(Player player, SoulChestData data) {
        Location dropLoc = player.getLocation();
        List<ItemStack> leftOver = new ArrayList<>();

        for (ItemStack item : data.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            leftOver.addAll(overflow.values());
        }

        ItemStack[] stored = data.getArmour();
        ItemStack[] current = player.getInventory().getArmorContents().clone();
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] == null || stored[i].getType() == Material.AIR) continue;
            if (current[i] == null || current[i].getType() == Material.AIR) {
                current[i] = stored[i].clone();
            } else {
                Map<Integer, ItemStack> ov = player.getInventory().addItem(stored[i].clone());
                leftOver.addAll(ov.values());
            }
        }
        player.getInventory().setArmorContents(current);

        ItemStack offHand = data.getOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            if (player.getInventory().getItemInOffHand().getType() == Material.AIR) {
                player.getInventory().setItemInOffHand(offHand.clone());
            } else {
                Map<Integer, ItemStack> ov = player.getInventory().addItem(offHand.clone());
                leftOver.addAll(ov.values());
            }
        }

        if (plugin.getConfig().getBoolean("general.store-xp", true) && data.getStoredXp() > 0) {
            player.giveExp(data.getStoredXp(), true);
        }

        if (!leftOver.isEmpty()) {
            Bukkit.getRegionScheduler().execute(plugin, dropLoc, () -> {
                for (ItemStack ov : leftOver) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, ov);
                }
            });
        }

        removeChest(data, false);
        MessageUtils.send(player, prefix() + cfg("messages.chest-looted", ""));
    }

    // ... (rest of the methods remain mostly the same as they were already using proper schedulers)

    public List<SoulChestData> getChestsForPlayer(UUID playerUUID) {
        return cache.values().stream()
                .filter(d -> d.getOwnerUUID().equals(playerUUID))
                .sorted(Comparator.comparingLong(SoulChestData::getCreationTime).reversed())
                .collect(Collectors.toList());
    }

    public SoulChestData getChestByBlock(Block block) {
        if (!(block.getState() instanceof Chest chestState)) return null;
        String id = chestState.getPersistentDataContainer()
                .get(KEY_CHEST_ID, PersistentDataType.STRING);
        if (id == null) return null;
        return cache.get(id);
    }

    public SoulChestData getChestById(String id) {
        return cache.get(id);
    }

    public int getActiveChestCount(UUID playerUUID) {
        return (int) cache.values().stream()
                .filter(d -> d.getOwnerUUID().equals(playerUUID) && !d.isExpired())
                .count();
    }

    public void updateChest(SoulChestData updated) {
        cache.put(updated.getId(), updated);
        dataManager.saveChest(updated);
        if (plugin.getConfig().getBoolean("hologram.enabled", true))
            updateHologramText(updated);
    }

    public int getEffectiveLimit(Player player) {
        int custom = dataManager.getCustomLimit(player.getUniqueId());
        if (custom > -1) return custom;
        if (player.hasPermission("soulchest.limit.unlimited")) return Integer.MAX_VALUE;
        for (int i = 100; i >= 1; i--)
            if (player.hasPermission("soulchest.limit." + i)) return i;
        return plugin.getConfig().getInt("general.default-max-chests", 5);
    }

    private void enforceLimit(UUID ownerUUID) {
        Player player = Bukkit.getPlayer(ownerUUID);
        int limit = (player != null)
                ? getEffectiveLimit(player)
                : plugin.getConfig().getInt("general.default-max-chests", 5);

        List<SoulChestData> chests = getChestsForPlayer(ownerUUID);
        while (!chests.isEmpty() && chests.size() >= limit) {
            SoulChestData oldest = chests.remove(chests.size() - 1);
            removeChest(oldest, plugin.getConfig().getBoolean("general.drop-items-on-expire", true));
            if (player != null)
                MessageUtils.send(player, prefix() + cfg("messages.chest-limit-reached", "")
                        .replace("{limit}", String.valueOf(limit)));
        }
    }

    private void spawnHologram(SoulChestData data) {
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        double yOffset = plugin.getConfig().getDouble("hologram.y-offset", 1.5);
        Location hLoc = loc.clone().add(0.5, yOffset, 0.5);

        Bukkit.getRegionScheduler().execute(plugin, hLoc, () -> {
            try {
                TextDisplay display = (TextDisplay) hLoc.getWorld()
                        .spawnEntity(hLoc, EntityType.TEXT_DISPLAY);

                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setDefaultBackground(false);
                display.setSeeThrough(false);
                display.setPersistent(true);
                display.setVisibleByDefault(true);

                display.getPersistentDataContainer()
                        .set(KEY_HOLOGRAM_ID, PersistentDataType.STRING, data.getId());

                updateHologramText(display, data);
                holograms.put(data.getId(), display.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn hologram for chest " + data.getId());
            }
        });
    }

    private void removeHologram(String chestId) {
        UUID uid = holograms.remove(chestId);
        if (uid == null) return;

        Entity entity = Bukkit.getEntity(uid);
        if (entity != null) {
            // Safe way on Folia - run on the entity's own region thread
            entity.getScheduler().run(plugin, scheduledTask -> {
                if (entity.isValid()) {
                    entity.remove();
                }
            }, null);
        }
    }

    private void updateHologramText(SoulChestData data) {
        UUID uid = holograms.get(data.getId());
        if (uid == null) return;

        Entity entity = Bukkit.getEntity(uid);
        if (entity instanceof TextDisplay display) {
            display.getScheduler().run(plugin, st -> updateHologramText(display, data), null);
        }
    }

    private void updateHologramText(TextDisplay display, SoulChestData data) {
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");
        if (lines.isEmpty()) {
            lines = List.of(
                    "&5{symbol} &d{owner}'s Soul Chest",
                    "&7Cause: &f{cause}",
                    "&7Expires in: &e{time_left}");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                    .replace("{symbol}",    data.getSymbol())
                    .replace("{owner}",     data.getOwnerName())
                    .replace("{cause}",     data.getCauseOfDeath())
                    .replace("{time_left}", data.formattedTimeLeft())
                    .replace("{world}",     data.getWorldName());
            sb.append(line);
            if (i < lines.size() - 1) sb.append("\n");
        }
        display.text(MessageUtils.parseAny(sb.toString()));
    }

    private void startHologramRefresh() {
        hologramRefreshTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> updateAllHolograms(),
                20L,
                20L
        );
    }

    private void scheduleExpiry(SoulChestData data) {
        long expiration = data.getExpirationTime();
        if (expiration == -1) return;

        long delayMillis = expiration - System.currentTimeMillis();
        if (delayMillis <= 0) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> expireChest(data));
            return;
        }

        long delayTicks = Math.max(1L, delayMillis / 50L);

        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin,
                st -> {
                    SoulChestData current = cache.get(data.getId());
                    if (current != null) expireChest(current);
                },
                delayTicks
        );

        expiryTasks.put(data.getId(), task);
    }

    private void updateAllHolograms() {
        for (SoulChestData data : cache.values()) {
            updateHologramText(data);
        }
    }

    private void tagChestBlock(Block block, String chestId) {
        if (block.getState() instanceof Chest chestState) {
            chestState.getPersistentDataContainer()
                    .set(KEY_CHEST_ID, PersistentDataType.STRING, chestId);
            chestState.update(true, false);
        }
    }

    private void dropAllItems(SoulChestData data, Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
            World world = loc.getWorld();
            for (ItemStack item : data.getContents())
                if (item != null && item.getType() != Material.AIR)
                    world.dropItemNaturally(loc, item);

            for (ItemStack piece : data.getArmour())
                if (piece != null && piece.getType() != Material.AIR)
                    world.dropItemNaturally(loc, piece);

            ItemStack off = data.getOffHand();
            if (off != null && off.getType() != Material.AIR)
                world.dropItemNaturally(loc, off);
        });
    }

    private String prefix() {
        return plugin.getConfig().getString("general.prefix",
                "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> ");
    }

    private String cfg(String key, String fallback) {
        return plugin.getConfig().getString(key, fallback);
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}