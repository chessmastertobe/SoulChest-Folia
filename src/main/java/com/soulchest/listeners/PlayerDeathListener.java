package com.soulchest.listeners;

import com.soulchest.SoulChest;
import com.soulchest.impl.ChestManager;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import com.soulchest.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PlayerDeathListener implements Listener {

    private final SoulChest    plugin;
    private final ChestManager chestManager;

    public PlayerDeathListener(SoulChest plugin, ChestManager chestManager) {
        this.plugin       = plugin;
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (!player.hasPermission("soulchest.use")) return;
        if (plugin.isToggleDisabled(player.getUniqueId())) return;
        if (Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)))
            return;

        boolean isPvP = event.getDamageSource().getCausingEntity() instanceof Player;
        if (isPvP && !plugin.getConfig().getBoolean("general.allow-chest-in-pvp", true)) {
            MessageUtils.send(player, prefix() + msg("pvp-disabled"));
            return;
        }

        PlayerInventory inv = player.getInventory();

        List<ItemStack> mainContents = snapshotArray(inv.getStorageContents());
        ItemStack[]     armour       = snapshotArmour(inv.getArmorContents());
        ItemStack       offHand      = safeClone(inv.getItemInOffHand());

        boolean hasItems =
                mainContents.stream().anyMatch(i -> i != null && i.getType() != Material.AIR)
                || Arrays.stream(armour).anyMatch(i -> i != null && i.getType() != Material.AIR)
                || (offHand != null && offHand.getType() != Material.AIR);
        if (!hasItems) return;

        event.getDrops().clear();

        int storedXp = 0;
        if (plugin.getConfig().getBoolean("general.store-xp", true)) {
            storedXp = event.getDroppedExp();
            event.setDroppedExp(0);
        }

        Location deathLoc = player.getLocation().clone();
        int radiusChunks  = plugin.getConfig().getInt("general.safe-location-search-radius", 10);
        Location chestLoc = SafeLocationFinder.findSafeGuaranteed(deathLoc, radiusChunks, true);

        boolean relocated = !locationsMatch(chestLoc, deathLoc);
        Location spawn = player.getWorld().getSpawnLocation();
        boolean usedSpawnFallback = relocated &&
                Math.abs(chestLoc.getBlockX() - spawn.getBlockX()) < 100 &&
                Math.abs(chestLoc.getBlockZ() - spawn.getBlockZ()) < 100;

        long    durationSecs = plugin.getConfig().getLong("general.chest-duration", 600);
        boolean protect      = player.hasPermission("soulchest.protect");
        String  symbol       = plugin.getConfig().getString("general.default-symbol", "⚰");
        String  cause        = formatCause(event);

        SoulChestData data = SoulChestData.create(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                player.getName(),
                chestLoc,
                mainContents,
                armour,
                offHand,
                storedXp,
                durationSecs,
                protect,
                cause,
                symbol
        );

        chestManager.spawnChest(data);

        String createdMsg = msg("chest-created")
                .replace("{world}", chestLoc.getWorld().getName())
                .replace("{x}",    String.valueOf(chestLoc.getBlockX()))
                .replace("{y}",    String.valueOf(chestLoc.getBlockY()))
                .replace("{z}",    String.valueOf(chestLoc.getBlockZ()));
        MessageUtils.send(player, prefix() + createdMsg);

        if (usedSpawnFallback) {
            MessageUtils.send(player, prefix() + msg("spawn-fallback-used"));
        } else if (relocated) {
            MessageUtils.send(player, prefix() + msg("safe-location-found"));
        }

        sendChestButtons(player, data);
    }

    private void sendChestButtons(Player player, SoulChestData data) {
        String chestId = data.getId();

        Component tpButton    = Component.empty();
        Component fetchButton = Component.empty();

        if (player.hasPermission("soulchest.tp")) {
            tpButton = Component
                    .text("[ Teleport to Chest ]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .clickEvent(ClickEvent.runCommand("/sc tp " + chestId))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to teleport to your Soul Chest",
                                    NamedTextColor.GRAY)));
        }

        if (player.hasPermission("soulchest.fetch")) {
            fetchButton = Component
                    .text("[ Fetch to Me ]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .clickEvent(ClickEvent.runCommand("/sc fetch " + chestId))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to pull the chest to your location",
                                    NamedTextColor.GRAY)));
        }

        if (tpButton.equals(Component.empty()) && fetchButton.equals(Component.empty())) return;

        player.sendMessage(
                Component.text("  ⚰  ", NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(tpButton)
                        .append(Component.text("  ", NamedTextColor.GRAY))
                        .append(fetchButton)
        );
    }

    private List<ItemStack> snapshotArray(ItemStack[] raw) {
        List<ItemStack> result = new ArrayList<>(raw.length);
        for (ItemStack item : raw) result.add(safeClone(item));
        return result;
    }

    private ItemStack[] snapshotArmour(ItemStack[] raw) {
        ItemStack[] result = new ItemStack[4];
        for (int i = 0; i < Math.min(raw.length, 4); i++)
            result[i] = safeClone(raw[i]);
        return result;
    }

    private ItemStack safeClone(ItemStack item) {
        return (item != null && item.getType() != Material.AIR) ? item.clone() : null;
    }

    private String formatCause(PlayerDeathEvent event) {
        try {
            if (event.deathMessage() != null) {
                String full = PlainTextComponentSerializer.plainText()
                        .serialize(event.deathMessage());
                String name = event.getPlayer().getName();
                int idx = full.indexOf(name);
                if (idx >= 0) {
                    String after = full.substring(idx + name.length()).trim();
                    if (!after.isEmpty()) return capitalise(after);
                }
                if (!full.isBlank()) return full;
            }
        } catch (Exception ignored) {}

        try {
            String key = event.getDamageSource().getDamageType().key().value();
            return capitalise(key.replace("_", " "));
        } catch (Exception ignored) {}

        return "Unknown";
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean locationsMatch(Location a, Location b) {
        return a.getWorld().getName().equals(b.getWorld().getName())
            && a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    private String prefix() {
        return plugin.getConfig().getString("general.prefix",
                "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> ");
    }

    private String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }
}