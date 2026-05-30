package com.soulchest.listeners;

import com.soulchest.SoulChest;
import com.soulchest.impl.ChestManager;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;

public class ChestInteractListener implements Listener {

    private final SoulChest    plugin;
    private final ChestManager chestManager;

    public ChestInteractListener(SoulChest plugin, ChestManager chestManager) {
        this.plugin       = plugin;
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        SoulChestData data = chestManager.getChestByBlock(block);
        if (data == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (data.isExpired()) {
            chestManager.removeChest(data,
                    plugin.getConfig().getBoolean("general.drop-items-on-expire", true));
            return;
        }

        if (!canAccess(player, data)) {
            MessageUtils.send(player, getPrefix() + getMsg("no-permission"));
            return;
        }

        chestManager.lootChest(player, data);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        SoulChestData data = chestManager.getChestByBlock(block);
        if (data == null) return;

        Player player = event.getPlayer();

        boolean allowBreak = plugin.getConfig().getBoolean("protection.allow-break-to-loot", false);

        if (!allowBreak) {
            if (!player.hasPermission("soulchest.protect.ignore")
                    && !player.getUniqueId().equals(data.getOwnerUUID())) {
                event.setCancelled(true);
                MessageUtils.send(player, getPrefix() + getMsg("no-permission"));
                return;
            }
            event.setCancelled(true);
            chestManager.lootChest(player, data);
            return;
        }

        if (!canAccess(player, data)) {
            event.setCancelled(true);
            MessageUtils.send(player, getPrefix() + getMsg("no-permission"));
            return;
        }

        event.setCancelled(true);
        chestManager.lootChest(player, data);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeChestBlocksFromExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeChestBlocksFromExplosion(event.blockList().iterator());
    }

    private void removeChestBlocksFromExplosion(Iterator<Block> blocks) {
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (block.getType() == Material.CHEST
                    && chestManager.getChestByBlock(block) != null) {
                blocks.remove();
            }
        }
    }

    private boolean canAccess(Player player, SoulChestData data) {
        if (!plugin.getConfig().getBoolean("protection.protect-from-others", true)) return true;
        if (!data.isLocked()) return true;
        if (player.getUniqueId().equals(data.getOwnerUUID())) return true;
        if (player.hasPermission("soulchest.protect.ignore")) return true;
        return false;
    }

    private String getPrefix() {
        return plugin.getConfig().getString("general.prefix",
                "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> ");
    }

    private String getMsg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }
}