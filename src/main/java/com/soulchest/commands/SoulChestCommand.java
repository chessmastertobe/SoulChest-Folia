package com.soulchest.commands;

import com.soulchest.SoulChest;
import com.soulchest.gui.ChestListGUI;
import com.soulchest.impl.ChestManager;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class SoulChestCommand implements CommandExecutor, TabCompleter {

    private final SoulChest    plugin;
    private final ChestManager chestManager;

    public SoulChestCommand(SoulChest plugin, ChestManager chestManager) {
        this.plugin       = plugin;
        this.chestManager = chestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "help"    -> sendHelp(sender);
            case "version" -> sendVersion(sender);
            case "reload"  -> handleReload(sender);
            case "toggle"  -> handleToggle(sender);
            case "chests"  -> handleChests(sender, args);
            case "tp"      -> handleTp(sender, args);
            case "fetch"   -> handleFetch(sender, args);
            case "unlock"  -> handleUnlock(sender, args);
            case "delete"  -> handleDelete(sender, args);
            case "admin"   -> handleAdmin(sender, args);
            default        -> MessageUtils.send(sender,
                    prefix() + "&cUnknown sub-command. Try &e/sc help&c.");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        line(sender, "<dark_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        line(sender, "  <light_purple><bold>⚰ SoulChest</bold></light_purple>  <gray>command reference</gray>");
        line(sender, "<dark_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        helpLine(sender, "/sc chests",        "Open your death chest GUI");
        helpLine(sender, "/sc tp <id>",       "Teleport to a chest");
        helpLine(sender, "/sc fetch <id>",    "Pull chest to your location");
        helpLine(sender, "/sc unlock <id>",   "Unlock a chest for everyone");
        helpLine(sender, "/sc delete <id>",   "Delete one of your chests");
        helpLine(sender, "/sc toggle",        "Toggle SoulChest spawning on/off");
        helpLine(sender, "/sc version",       "Show plugin version");
        if (sender.hasPermission("soulchest.admin")) {
            line(sender, "<dark_purple>─── <gray>Admin</gray> ──────────────────────────────────");
            helpLine(sender, "/sc chests <player>",              "[A] View another player's chests");
            helpLine(sender, "/sc admin delete <player> <id>",   "[A] Delete a player's chest");
            helpLine(sender, "/sc admin tp <player> <id>",       "[A] TP to a player's chest");
            helpLine(sender, "/sc admin view <player> <id>",     "[A] Preview chest contents");
            helpLine(sender, "/sc admin setlimit <player> <N>",  "[A] Set chest limit");
            helpLine(sender, "/sc admin resetlimit <player>",    "[A] Reset chest limit");
            helpLine(sender, "/sc reload",                       "[A] Reload configuration");
        }
        line(sender, "<dark_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void helpLine(CommandSender s, String cmd, String desc) {
        line(s, "  <yellow>" + cmd + "</yellow> <dark_gray>→</dark_gray> <gray>" + desc + "</gray>");
    }

    private void line(CommandSender s, String mini) {
        s.sendMessage(MessageUtils.parse(mini));
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage(MessageUtils.parse(
                "<light_purple><bold>⚰ SoulChest</bold></light_purple> "
              + "<gray>v" + plugin.getDescription().getVersion()
              + " — Paper/Folia 1.21+</gray>"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("soulchest.reload")) {
            MessageUtils.send(sender, prefix() + msg("no-permission")); return;
        }
        plugin.reloadConfig();
        MessageUtils.send(sender, prefix() + msg("config-reloaded"));
    }

    private void handleToggle(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("soulchest.toggle")) {
            MessageUtils.send(player, prefix() + msg("no-permission")); return;
        }
        boolean nowDisabled = plugin.toggleDisabled(player.getUniqueId());
        MessageUtils.send(player, prefix() + msg(nowDisabled ? "toggle-off" : "toggle-on"));
    }

    private void handleChests(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender); if (viewer == null) return;

        if (args.length >= 2) {
            if (!viewer.hasPermission("soulchest.admin")) {
                MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
            }
            OfflinePlayer target = resolveOffline(args[1]);
            if (target == null) {
                MessageUtils.send(viewer, prefix() + msg("player-not-found")); return;
            }
            openListGUI(viewer, target.getUniqueId(),
                    target.getName() != null ? target.getName() : args[1]);
            return;
        }

        if (!viewer.hasPermission("soulchest.gui")) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }
        if (chestManager.getChestsForPlayer(viewer.getUniqueId()).isEmpty()) {
            MessageUtils.send(viewer, prefix() + msg("no-chests")); return;
        }
        openListGUI(viewer, viewer.getUniqueId(), viewer.getName());
    }

    private void handleTp(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("soulchest.tp")) {
            MessageUtils.send(player, prefix() + msg("no-permission")); return;
        }
        if (args.length < 2) {
            MessageUtils.send(player, prefix() + "&eUsage: /sc tp <id>"); return;
        }
        SoulChestData data = resolveOwnChest(player, args[1]);
        if (data == null) return;

        Location loc = data.toLocation();
        if (loc == null) {
            MessageUtils.send(player, prefix() + "&cChest world is not loaded."); return;
        }

        // Using teleportAsync is already Folia-safe
        player.teleportAsync(loc.clone().add(0.5, 1.0, 0.5)).thenRun(() -> {
            MessageUtils.send(player, prefix() + msg("teleporting"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT,
                    SoundCategory.PLAYERS, 1f, 1f);
        });
    }

    private void handleFetch(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("soulchest.fetch")) {
            MessageUtils.send(player, prefix() + msg("no-permission")); return;
        }
        if (args.length < 2) {
            MessageUtils.send(player, prefix() + "&eUsage: /sc fetch <id>"); return;
        }
        SoulChestData data = resolveOwnChest(player, args[1]);
        if (data == null) return;

        Location newLoc = player.getLocation().getBlock().getLocation();
        chestManager.relocateChest(data, newLoc);

        MessageUtils.send(player, prefix() + msg("fetching"));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN,
                SoundCategory.BLOCKS, 1f, 0.8f);
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("soulchest.unlock")) {
            MessageUtils.send(player, prefix() + msg("no-permission")); return;
        }
        if (args.length < 2) {
            MessageUtils.send(player, prefix() + "&eUsage: /sc unlock <id>"); return;
        }
        SoulChestData data = resolveOwnChest(player, args[1]);
        if (data == null) return;
        chestManager.updateChest(data.withLocked(false));
        MessageUtils.send(player, prefix() + msg("chest-unlocked"));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) {
            MessageUtils.send(player, prefix() + "&eUsage: /sc delete <id>"); return;
        }
        SoulChestData data = resolveOwnChest(player, args[1]);
        if (data == null) return;
        chestManager.removeChest(data,
                plugin.getConfig().getBoolean("general.drop-items-on-expire", true));
        MessageUtils.send(player, prefix() + msg("chest-deleted"));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulchest.admin")) {
            MessageUtils.send(sender, prefix() + msg("no-permission")); return;
        }
        if (args.length < 2) {
            MessageUtils.send(sender, prefix() +
                    "&eSub-commands: chests, delete, tp, view, setlimit, resetlimit");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "chests"     -> adminChests(sender, args);
            case "delete"     -> adminDelete(sender, args);
            case "tp"         -> adminTp(sender, args);
            case "view"       -> adminView(sender, args);
            case "setlimit"   -> adminSetLimit(sender, args);
            case "resetlimit" -> adminResetLimit(sender, args);
            default           -> MessageUtils.send(sender,
                    prefix() + "&cUnknown admin sub-command.");
        }
    }

    private void adminChests(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender); if (viewer == null) return;
        if (args.length < 3) {
            MessageUtils.send(viewer, prefix() + "&eUsage: /sc admin chests <player>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(viewer, prefix() + msg("player-not-found")); return;
        }
        openListGUI(viewer, target.getUniqueId(),
                target.getName() != null ? target.getName() : args[2]);
    }

    private void adminDelete(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtils.send(sender, prefix() + "&eUsage: /sc admin delete <player> <id>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(sender, prefix() + msg("player-not-found")); return;
        }
        SoulChestData data = chestManager.getChestById(args[3]);
        if (data == null || !data.getOwnerUUID().equals(target.getUniqueId())) {
            MessageUtils.send(sender, prefix() + msg("chest-not-found")); return;
        }
        chestManager.removeChest(data,
                plugin.getConfig().getBoolean("general.drop-items-on-expire", true));
        MessageUtils.send(sender, prefix() + msg("admin-chest-deleted")
                .replace("{player}", target.getName() != null ? target.getName() : args[2]));
    }

    private void adminTp(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender); if (viewer == null) return;
        if (args.length < 4) {
            MessageUtils.send(viewer, prefix() + "&eUsage: /sc admin tp <player> <id>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(viewer, prefix() + msg("player-not-found")); return;
        }
        SoulChestData data = chestManager.getChestById(args[3]);
        if (data == null || !data.getOwnerUUID().equals(target.getUniqueId())) {
            MessageUtils.send(viewer, prefix() + msg("chest-not-found")); return;
        }
        Location loc = data.toLocation();
        if (loc == null) {
            MessageUtils.send(viewer, prefix() + "&cWorld not loaded."); return;
        }
        viewer.teleportAsync(loc.clone().add(0.5, 1.0, 0.5))
              .thenRun(() -> MessageUtils.send(viewer, prefix() + msg("teleporting")));
    }

    private void adminView(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender); if (viewer == null) return;
        if (args.length < 4) {
            MessageUtils.send(viewer, prefix() + "&eUsage: /sc admin view <player> <id>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(viewer, prefix() + msg("player-not-found")); return;
        }
        SoulChestData data = chestManager.getChestById(args[3]);
        if (data == null || !data.getOwnerUUID().equals(target.getUniqueId())) {
            MessageUtils.send(viewer, prefix() + msg("chest-not-found")); return;
        }
        openAdminViewInventory(viewer, data);
    }

    private void adminSetLimit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtils.send(sender, prefix() + "&eUsage: /sc admin setlimit <player> <N>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(sender, prefix() + msg("player-not-found")); return;
        }
        int limit;
        try {
            limit = Integer.parseInt(args[3]);
            if (limit < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageUtils.send(sender, prefix() + msg("invalid-number")); return;
        }
        chestManager.getDataManager().setCustomLimit(target.getUniqueId(), limit);
        MessageUtils.send(sender, prefix() + msg("limit-set")
                .replace("{player}", target.getName() != null ? target.getName() : args[2])
                .replace("{limit}",  String.valueOf(limit)));
    }

    private void adminResetLimit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtils.send(sender, prefix() + "&eUsage: /sc admin resetlimit <player>"); return;
        }
        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            MessageUtils.send(sender, prefix() + msg("player-not-found")); return;
        }
        chestManager.getDataManager().setCustomLimit(target.getUniqueId(), -1);
        String name = target.getName() != null ? target.getName() : args[2];
        MessageUtils.send(sender, prefix() + "&aReset &e" + name + "&a's chest limit to default.");
    }

    private void openAdminViewInventory(Player viewer, SoulChestData data) {
        Inventory preview = Bukkit.createInventory(null, 54,
                MessageUtils.parseAny("&5Contents of &d" + data.getOwnerName() + "'s Chest"));

        List<ItemStack> contents = data.getContents();
        for (int i = 0; i < Math.min(contents.size(), 36); i++) {
            ItemStack item = contents.get(i);
            if (item != null && item.getType() != Material.AIR) {
                preview.setItem(i, item.clone());
            }
        }
        ItemStack[] armour = data.getArmour();
        for (int i = 0; i < armour.length; i++) {
            if (armour[i] != null && armour[i].getType() != Material.AIR) {
                preview.setItem(36 + i, armour[i].clone());
            }
        }
        ItemStack offHand = data.getOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            preview.setItem(40, offHand.clone());
        }

        ItemStack xpItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta  = xpItem.getItemMeta();
        xpMeta.displayName(Component.text("Stored XP: " + data.getStoredXp(),
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        xpItem.setItemMeta(xpMeta);
        preview.setItem(49, xpItem);

        viewer.openInventory(preview);
        plugin.getGUIListener().registerViewGUI(viewer.getUniqueId(), null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "help", "version", "chests", "tp", "fetch", "unlock", "delete", "toggle"));
            if (sender.hasPermission("soulchest.reload")) subs.add("reload");
            if (sender.hasPermission("soulchest.admin"))  subs.add("admin");
            filterPrefix(subs, args[0], out);

        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "chests" -> {
                    if (sender.hasPermission("soulchest.admin"))
                        onlineNames(args[1], out);
                }
                case "tp", "fetch", "unlock", "delete" -> {
                    if (sender instanceof Player p)
                        chestIds(p.getUniqueId(), args[1], out);
                }
                case "admin" -> {
                    if (sender.hasPermission("soulchest.admin"))
                        filterPrefix(List.of("chests", "delete", "tp", "view",
                                "setlimit", "resetlimit"), args[1], out);
                }
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (List.of("chests","delete","tp","view","setlimit","resetlimit").contains(sub))
                onlineNames(args[2], out);

        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (List.of("delete","tp","view").contains(sub)) {
                OfflinePlayer target = resolveOffline(args[2]);
                if (target != null) chestIds(target.getUniqueId(), args[3], out);
            }
        }

        return out;
    }

    private void openListGUI(Player viewer, UUID targetUUID, String targetName) {
        new ChestListGUI(plugin, chestManager, viewer, targetUUID, targetName).open();
    }

    private SoulChestData resolveOwnChest(Player player, String arg) {
        SoulChestData direct = chestManager.getChestById(arg);
        if (direct != null && direct.getOwnerUUID().equals(player.getUniqueId()))
            return direct;

        try {
            int idx = Integer.parseInt(arg) - 1;
            List<SoulChestData> list = chestManager.getChestsForPlayer(player.getUniqueId());
            if (idx >= 0 && idx < list.size()) return list.get(idx);
        } catch (NumberFormatException ignored) { /* fall through */ }

        MessageUtils.send(player, prefix() + msg("chest-not-found"));
        return null;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveOffline(String name) {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getName().equalsIgnoreCase(name)) return p;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return (op.hasPlayedBefore() || op.isOnline()) ? op : null;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        MessageUtils.send(sender, prefix() + "&cThis command requires a player.");
        return null;
    }

    private void filterPrefix(List<String> opts, String prefix, List<String> out) {
        opts.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
                .forEach(out::add);
    }

    private void onlineNames(String prefix, List<String> out) {
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .forEach(out::add);
    }

    private void chestIds(UUID uuid, String prefix, List<String> out) {
        chestManager.getChestsForPlayer(uuid).stream()
                .map(SoulChestData::getId)
                .filter(id -> id.toLowerCase().startsWith(prefix.toLowerCase()))
                .forEach(out::add);
    }

    private String prefix() {
        return plugin.getConfig().getString("general.prefix",
                "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> ");
    }

    private String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }
}