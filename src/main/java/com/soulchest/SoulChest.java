package com.soulchest;

import com.soulchest.commands.SoulChestCommand;
import com.soulchest.listeners.ChestInteractListener;
import com.soulchest.listeners.GUIListener;
import com.soulchest.listeners.PlayerDeathListener;
import com.soulchest.impl.ChestManager;
import com.soulchest.impl.DataManager;
import com.soulchest.util.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SoulChest extends JavaPlugin {

    private DataManager  dataManager;
    private ChestManager chestManager;
    private GUIListener  guiListener;

    private final Set<UUID> toggleDisabled = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataManager  = new DataManager(this);
        chestManager = new ChestManager(this, dataManager);
        chestManager.onEnable();

        guiListener = new GUIListener(this);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerDeathListener(this, chestManager), this);
        pm.registerEvents(new ChestInteractListener(this, chestManager), this);
        pm.registerEvents(guiListener, this);

        SoulChestCommand scCmd = new SoulChestCommand(this, chestManager);
        var cmd = getCommand("sc");
        if (cmd != null) {
            cmd.setExecutor(scCmd);
            cmd.setTabCompleter(scCmd);
        } else {
            getLogger().severe("Failed to register /sc command! Check plugin.yml.");
        }

        Bukkit.getConsoleSender().sendMessage(Component.text(
                "[SoulChest] Enabled. Running on Paper 26.1 (1.21.11).",
                NamedTextColor.LIGHT_PURPLE));
    }

    @Override
    public void onDisable() {
        if (chestManager != null) {
            chestManager.onDisable();
        }
        Bukkit.getConsoleSender().sendMessage(Component.text(
                "[SoulChest] Disabled. All tasks cancelled.",
                NamedTextColor.GRAY));
    }

    public boolean isToggleDisabled(UUID uuid) {
        return toggleDisabled.contains(uuid);
    }

    public boolean toggleDisabled(UUID uuid) {
        if (toggleDisabled.contains(uuid)) {
            toggleDisabled.remove(uuid);
            return false;
        } else {
            toggleDisabled.add(uuid);
            return true;
        }
    }

    public DataManager  getDataManager()  { return dataManager; }
    public ChestManager getChestManager() { return chestManager; }
    public GUIListener  getGUIListener()  { return guiListener; }
}