package com.soulchest.gui;

import com.soulchest.SoulChest;
import com.soulchest.impl.ChestManager;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChestManageGUI {

    private static final int SLOT_INFO     = 0;
    private static final int SLOT_TELEPORT = 2;
    private static final int SLOT_FETCH    = 3;
    private static final int SLOT_VIEW     = 5;
    private static final int SLOT_UNLOCK   = 6;
    private static final int SLOT_SYMBOL   = 7;
    private static final int SLOT_DELETE   = 8;
    private static final int SLOT_BACK     = 22;

    private static final String[] SYMBOLS =
            {"⚰", "☠", "♦", "★", "✦", "⚡", "❄", "♣", "♥", "⚜", "✿", "◈"};

    private final SoulChest    plugin;
    private final ChestManager chestManager;
    private final Player       viewer;
    private       SoulChestData data;
    private final ChestListGUI  parentGUI;
    private final boolean       isAdminView;

    public ChestManageGUI(SoulChest plugin, ChestManager chestManager,
                          Player viewer, SoulChestData data,
                          ChestListGUI parentGUI, boolean isAdminView) {
        this.plugin       = plugin;
        this.chestManager = chestManager;
        this.viewer       = viewer;
        this.data         = data;
        this.parentGUI    = parentGUI;
        this.isAdminView  = isAdminView;
    }

    public void open() {
        SoulChestData fresh = chestManager.getChestById(data.getId());
        if (fresh != null) data = fresh;

        String rawTitle = plugin.getConfig().getString("gui.manage-title", "&5Manage Soul Chest");
        Inventory inv   = Bukkit.createInventory(null, 27, MessageUtils.parseAny(rawTitle));

        Material fillerMat = parseMaterial("gui.filler-material", Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler   = makeItem(fillerMat, Component.text(" "), null);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        boolean isOwner = viewer.getUniqueId().equals(data.getOwnerUUID());

        inv.setItem(SLOT_INFO, buildInfoItem());

        if (isAdminView || viewer.hasPermission("soulchest.tp"))
            inv.setItem(SLOT_TELEPORT, makeItem(Material.ENDER_PEARL,
                    Component.text("Teleport to Chest", NamedTextColor.AQUA, TextDecoration.BOLD),
                    List.of(Component.text("Warp to this chest's location.", NamedTextColor.GRAY))));

        if (isAdminView || viewer.hasPermission("soulchest.fetch"))
            inv.setItem(SLOT_FETCH, makeItem(Material.LEAD,
                    Component.text("Fetch Chest to You", NamedTextColor.GREEN, TextDecoration.BOLD),
                    List.of(Component.text("Move the chest to your location.", NamedTextColor.GRAY))));

        if (plugin.getConfig().getBoolean("gui.show-view-button", true))
            inv.setItem(SLOT_VIEW, makeItem(Material.BOOK,
                    Component.text("View Items", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    List.of(
                            Component.text("Read-only preview of contents.", NamedTextColor.GRAY),
                            Component.text("Items cannot be removed.", NamedTextColor.DARK_GRAY))));

        if (isOwner || isAdminView) {
            Material icon = data.isLocked() ? Material.TRIPWIRE_HOOK : Material.TRIPWIRE_HOOK;
            String title = data.isLocked() ? "Unlock Chest" : "Lock Chest";
            inv.setItem(SLOT_UNLOCK, makeItem(icon,
                    Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD),
                    data.isLocked()
                            ? List.of(Component.text("Allow anyone to open this chest.", NamedTextColor.GRAY))
                            : List.of(
                                    Component.text("Currently: Unlocked", NamedTextColor.RED),
                                    Component.text("Click to protect again.", NamedTextColor.GRAY))));
        }

        if (isOwner || isAdminView) {
            int nextIdx = (Arrays.asList(SYMBOLS).indexOf(data.getSymbol()) + 1) % SYMBOLS.length;
            inv.setItem(SLOT_SYMBOL, makeItem(Material.NAME_TAG,
                    Component.text("Change Symbol", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    List.of(
                            Component.text("Current: " + data.getSymbol(), NamedTextColor.GRAY),
                            Component.text("Next:    " + SYMBOLS[nextIdx], NamedTextColor.YELLOW),
                            Component.text("Click to cycle.", NamedTextColor.DARK_GRAY))));
        }

        if (isOwner || isAdminView)
            inv.setItem(SLOT_DELETE, makeItem(Material.BARRIER,
                    Component.text("Delete Chest", NamedTextColor.RED, TextDecoration.BOLD),
                    List.of(
                            Component.text("Permanently removes this chest.", NamedTextColor.GRAY),
                            Component.text("Items will be dropped at the chest location.",
                                    NamedTextColor.DARK_RED))));

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW,
                Component.text("<- Back to List", NamedTextColor.GRAY),
                List.of(Component.text("Return to the chest list.", NamedTextColor.DARK_GRAY))));

        viewer.openInventory(inv);
        plugin.getGUIListener().registerManageGUI(viewer.getUniqueId(), this);
    }

    public boolean handleClick(int slot) {
        return switch (slot) {
            case SLOT_TELEPORT -> { handleTeleport();   yield true; }
            case SLOT_FETCH    -> { handleFetch();       yield true; }
            case SLOT_VIEW     -> { handleView();        yield true; }
            case SLOT_UNLOCK   -> { handleToggleLock();  yield true; }
            case SLOT_SYMBOL   -> { handleSymbol();      yield true; }
            case SLOT_DELETE   -> { handleDelete();      yield true; }
            case SLOT_BACK     -> {
                viewer.closeInventory();
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> parentGUI.open(), 1L);
                yield true;
            }
            default -> false;
        };
    }

    private void handleTeleport() {
        if (!isAdminView && !viewer.hasPermission("soulchest.tp")) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) {
            MessageUtils.send(viewer, prefix() + "&cChest world is not currently loaded."); return;
        }
        viewer.closeInventory();
        viewer.teleportAsync(loc.clone().add(0.5, 1.0, 0.5)).thenRun(() -> {
            MessageUtils.send(viewer, prefix() + msg("teleporting"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT,
                    SoundCategory.PLAYERS, 1f, 1f);
        });
    }

    private void handleFetch() {
        if (!isAdminView && !viewer.hasPermission("soulchest.fetch")) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }

        Location newLoc = viewer.getLocation().getBlock().getLocation();
        data = chestManager.relocateChest(data, newLoc);

        viewer.closeInventory();
        MessageUtils.send(viewer, prefix() + msg("fetching"));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN,
                SoundCategory.BLOCKS, 1f, 0.8f);
    }

    private void handleView() {
        Inventory preview = Bukkit.createInventory(null, 54,
                MessageUtils.parseAny("&5Contents of &d" + data.getOwnerName() + "'s Chest"));

        List<ItemStack> contents = data.getContents();
        for (int i = 0; i < Math.min(contents.size(), 36); i++) {
            ItemStack item = contents.get(i);
            if (item != null && item.getType() != Material.AIR)
                preview.setItem(i, item.clone());
        }

        ItemStack[] armour = data.getArmour();
        for (int i = 0; i < armour.length; i++)
            if (armour[i] != null && armour[i].getType() != Material.AIR)
                preview.setItem(36 + i, armour[i].clone());

        ItemStack offHand = data.getOffHand();
        if (offHand != null && offHand.getType() != Material.AIR)
            preview.setItem(40, offHand.clone());

        preview.setItem(49, makeItem(Material.EXPERIENCE_BOTTLE,
                Component.text("Stored XP: " + data.getStoredXp(), NamedTextColor.YELLOW),
                List.of(Component.text("Restored when looted.", NamedTextColor.GRAY))));

        preview.setItem(53, makeItem(Material.ARROW,
                Component.text("<- Back", NamedTextColor.GRAY),
                List.of(Component.text("Return to manage menu.", NamedTextColor.DARK_GRAY))));

        viewer.openInventory(preview);
        plugin.getGUIListener().registerViewGUI(viewer.getUniqueId(), this);
    }

    private void handleToggleLock() {
        boolean isOwner = viewer.getUniqueId().equals(data.getOwnerUUID());
        if (!isOwner && !isAdminView) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }
        SoulChestData updated = data.withLocked(!data.isLocked());
        chestManager.updateChest(updated);
        data = updated;
        String feedback = data.isLocked()
                ? prefix() + "&aChest is now &lprotected&r&a."
                : prefix() + msg("chest-unlocked");
        MessageUtils.send(viewer, feedback);
        open();
    }

    private void handleSymbol() {
        boolean isOwner = viewer.getUniqueId().equals(data.getOwnerUUID());
        if (!isOwner && !isAdminView) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }
        List<String> symbols = Arrays.asList(SYMBOLS);
        int currentIdx = symbols.indexOf(data.getSymbol());
        String nextSymbol = SYMBOLS[(currentIdx + 1) % SYMBOLS.length];
        SoulChestData updated = data.withSymbol(nextSymbol);
        chestManager.updateChest(updated);
        data = updated;
        MessageUtils.send(viewer, prefix() + msg("symbol-changed").replace("{symbol}", nextSymbol));
        open();
    }

    private void handleDelete() {
        boolean isOwner = viewer.getUniqueId().equals(data.getOwnerUUID());
        if (!isOwner && !isAdminView) {
            MessageUtils.send(viewer, prefix() + msg("no-permission")); return;
        }
        chestManager.removeChest(data,
                plugin.getConfig().getBoolean("general.drop-items-on-expire", true));
        String feedback = (!isOwner && isAdminView)
                ? prefix() + msg("admin-chest-deleted").replace("{player}", data.getOwnerName())
                : prefix() + msg("chest-deleted");
        MessageUtils.send(viewer, feedback);
        viewer.closeInventory();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> parentGUI.open(), 1L);
    }

    public void handleViewBack() { open(); }

    private ItemStack buildInfoItem() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Owner: ", NamedTextColor.GRAY)
                .append(Component.text(data.getOwnerName(), NamedTextColor.WHITE)));
        lore.add(Component.text("World: ", NamedTextColor.GRAY)
                .append(Component.text(data.getWorldName(), NamedTextColor.WHITE)));
        lore.add(Component.text("Location: ", NamedTextColor.GRAY)
                .append(Component.text(data.getX() + ", " + data.getY() + ", " + data.getZ(),
                        NamedTextColor.WHITE)));
        lore.add(Component.text("Cause: ", NamedTextColor.GRAY)
                .append(Component.text(data.getCauseOfDeath(), NamedTextColor.WHITE)));
        lore.add(Component.text("Protected: ", NamedTextColor.GRAY)
                .append(Component.text(data.isLocked() ? "Yes" : "No",
                        data.isLocked() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        lore.add(Component.text("Time left: ", NamedTextColor.GRAY)
                .append(Component.text(data.formattedTimeLeft(),
                        data.isExpired() ? NamedTextColor.RED : NamedTextColor.GREEN)));
        lore.add(Component.text("Stored XP: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(data.getStoredXp()), NamedTextColor.YELLOW)));
        lore.add(Component.text("Symbol: " + data.getSymbol(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Chest ID: " + data.getId().substring(0, 8) + "...",
                NamedTextColor.DARK_GRAY));
        return makeItem(Material.CHEST,
                Component.text(data.getSymbol() + " Soul Chest Info",
                        NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                lore);
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null) {
                meta.lore(lore.stream()
                        .map(l -> l.decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material parseMaterial(String key, Material fallback) {
        try {
            return Material.valueOf(
                    plugin.getConfig().getString(key, fallback.name()).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String prefix() {
        return plugin.getConfig().getString("general.prefix",
                "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> ");
    }

    private String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    public SoulChestData getData() { return data; }
}