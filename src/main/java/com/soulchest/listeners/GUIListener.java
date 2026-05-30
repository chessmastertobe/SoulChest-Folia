package com.soulchest.listeners;

import com.soulchest.SoulChest;
import com.soulchest.gui.ChestListGUI;
import com.soulchest.gui.ChestManageGUI;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {

    public sealed interface GUISession
            permits GUIListener.ListSession,
                    GUIListener.ManageSession,
                    GUIListener.ViewSession {}

    public record ListSession(ChestListGUI gui)    implements GUISession {}
    public record ManageSession(ChestManageGUI gui) implements GUISession {}
    public record ViewSession(ChestManageGUI parent) implements GUISession {}

    private static final int VIEW_BACK_SLOT = 53;

    private final Map<UUID, GUISession> sessions = new ConcurrentHashMap<>();

    private final SoulChest plugin;

    public GUIListener(SoulChest plugin) {
        this.plugin = plugin;
    }

    public void registerListGUI(UUID playerUUID, ChestListGUI gui) {
        sessions.put(playerUUID, new ListSession(gui));
    }

    public void registerManageGUI(UUID playerUUID, ChestManageGUI gui) {
        sessions.put(playerUUID, new ManageSession(gui));
    }

    public void registerViewGUI(UUID playerUUID, ChestManageGUI parent) {
        sessions.put(playerUUID, new ViewSession(parent));
    }

    public void clearSession(UUID playerUUID) {
        sessions.remove(playerUUID);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        GUISession session = sessions.get(uuid);
        if (session == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().equals(player.getInventory())) return;

        int slot = event.getRawSlot();

        switch (session) {
            case ListSession   ls -> ls.gui().handleClick(slot);
            case ManageSession ms -> ms.gui().handleClick(slot);
            case ViewSession   vs -> {
                if (slot == VIEW_BACK_SLOT) {
                    if (vs.parent() != null) {
                        vs.parent().handleViewBack();
                    } else {
                        player.closeInventory();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        sessions.remove(player.getUniqueId());
    }
}