package com.soulchest.gui;

import com.soulchest.SoulChest;
import com.soulchest.impl.ChestManager;
import com.soulchest.model.SoulChestData;
import com.soulchest.util.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChestListGUI {

    private static final int SIZE          = 54;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END   = 44;
    private static final int CONTENT_SLOTS = CONTENT_END - CONTENT_START + 1;
    private static final int PREV_SLOT     = 45;
    private static final int NEXT_SLOT     = 53;
    private static final int INFO_SLOT     = 49;

    private final SoulChest    plugin;
    private final ChestManager chestManager;
    private final Player       viewer;
    private final UUID         targetUUID;
    private final String       targetName;
    private final boolean      isAdminView;

    private List<SoulChestData> chests;
    private int page = 0;

    public ChestListGUI(SoulChest plugin, ChestManager chestManager,
                        Player viewer, UUID targetUUID, String targetName) {
        this.plugin       = plugin;
        this.chestManager = chestManager;
        this.viewer       = viewer;
        this.targetUUID   = targetUUID;
        this.targetName   = targetName;
        this.isAdminView  = !viewer.getUniqueId().equals(targetUUID);
    }

    public void open() {
        chests = chestManager.getChestsForPlayer(targetUUID);

        String rawTitle = isAdminView
                ? plugin.getConfig().getString("gui.admin-list-title",
                        "&4⚰ &c{player}'s Soul Chests").replace("{player}", targetName)
                : plugin.getConfig().getString("gui.list-title",
                        "&5⚰ &dYour Soul Chests");

        Inventory inv = Bukkit.createInventory(null, SIZE, MessageUtils.parseAny(rawTitle));

        Material fillerMat = parseMaterial("gui.filler-material", Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler   = makeItem(fillerMat, Component.text(" "), null);
        for (int i = 0;  i <  9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int start = page * CONTENT_SLOTS;
        int end   = Math.min(start + CONTENT_SLOTS, chests.size());
        for (int i = start; i < end; i++)
            inv.setItem(CONTENT_START + (i - start), buildChestItem(chests.get(i), i + 1));

        if (page > 0)
            inv.setItem(PREV_SLOT, makeItem(Material.ARROW,
                    Component.text("<- Previous Page", NamedTextColor.YELLOW),
                    List.of(Component.text("Page " + page, NamedTextColor.GRAY))));
        if (end < chests.size())
            inv.setItem(NEXT_SLOT, makeItem(Material.ARROW,
                    Component.text("Next Page ->", NamedTextColor.YELLOW),
                    List.of(Component.text("Page " + (page + 2), NamedTextColor.GRAY))));

        int limit    = chestManager.getEffectiveLimit(viewer);
        String limitStr = (limit == Integer.MAX_VALUE) ? "oo" : String.valueOf(limit);
        inv.setItem(INFO_SLOT, makeItem(Material.BOOK,
                Component.text("Soul Chests", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                List.of(
                        Component.text("Owner: ", NamedTextColor.GRAY)
                                .append(Component.text(targetName, NamedTextColor.WHITE)),
                        Component.text("Active: ", NamedTextColor.GRAY)
                                .append(Component.text(chests.size() + " / " + limitStr,
                                        NamedTextColor.YELLOW)),
                        Component.empty(),
                        Component.text("Click a chest to manage it.", NamedTextColor.DARK_GRAY)
                )));

        viewer.openInventory(inv);
        plugin.getGUIListener().registerListGUI(viewer.getUniqueId(), this);
    }

    public boolean handleClick(int slot) {
        if (slot == PREV_SLOT && page > 0) { page--; open(); return true; }
        if (slot == NEXT_SLOT && (page + 1) * CONTENT_SLOTS < chests.size()) {
            page++; open(); return true;
        }
        if (slot >= CONTENT_START && slot <= CONTENT_END) {
            int index = page * CONTENT_SLOTS + (slot - CONTENT_START);
            if (index >= 0 && index < chests.size()) {
                SoulChestData data = chests.get(index);
                new ChestManageGUI(plugin, chestManager, viewer, data, this, isAdminView).open();
                return true;
            }
        }
        return false;
    }

    private ItemStack buildChestItem(SoulChestData data, int number) {
        boolean expired = data.isExpired();
        Material mat = expired
                ? parseMaterial("gui.expired-icon-material", Material.DEAD_BUSH)
                : parseMaterial("gui.chest-icon-material",   Material.CHEST);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("World: ",    NamedTextColor.GRAY)
                .append(Component.text(data.getWorldName(), NamedTextColor.WHITE)));
        lore.add(Component.text("Location: ", NamedTextColor.GRAY)
                .append(Component.text(data.getX() + ", " + data.getY() + ", " + data.getZ(),
                        NamedTextColor.WHITE)));
        lore.add(Component.text("Cause: ",    NamedTextColor.GRAY)
                .append(Component.text(data.getCauseOfDeath(), NamedTextColor.WHITE)));
        if (expired) {
            lore.add(Component.text("EXPIRED", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            lore.add(Component.text("Time left: ", NamedTextColor.GRAY)
                    .append(Component.text(data.formattedTimeLeft(), NamedTextColor.GREEN)));
        }
        lore.add(Component.text("Protected: ", NamedTextColor.GRAY)
                .append(Component.text(data.isLocked() ? "Yes" : "No",
                        data.isLocked() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to manage", NamedTextColor.YELLOW));

        return makeItem(mat,
                Component.text(data.getSymbol() + " Chest #" + number,
                        NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                lore);
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null)
            meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String key, Material fallback) {
        try {
            return Material.valueOf(
                    plugin.getConfig().getString(key, fallback.name()).toUpperCase());
        } catch (IllegalArgumentException e) { return fallback; }
    }

    public Player getViewer()     { return viewer; }
    public UUID   getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public int    getPage()       { return page; }
}