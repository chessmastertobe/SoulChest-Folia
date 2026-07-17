package com.soulchest.impl;

import com.soulchest.SoulChest;
import com.soulchest.model.SoulChestData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DataManager {

    private final SoulChest plugin;
    private Connection connection;

    public DataManager(SoulChest plugin) {
        this.plugin = plugin;
        openConnection();
    }

    private void openConnection() {
        try {
            plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "soulchest.db");

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
            }

            createTables();
            plugin.getLogger().info("[SoulChest] SQLite database connected: " + dbFile.getName());

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SoulChest] Failed to open SQLite connection!", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SoulChest] Error closing SQLite connection", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS soul_chests (
                    id              TEXT    PRIMARY KEY,
                    owner_uuid      TEXT    NOT NULL,
                    owner_name      TEXT    NOT NULL,
                    world_name      TEXT    NOT NULL,
                    x               INTEGER NOT NULL,
                    y               INTEGER NOT NULL,
                    z               INTEGER NOT NULL,
                    creation_time   INTEGER NOT NULL,
                    expiration_time INTEGER NOT NULL,
                    locked          INTEGER NOT NULL DEFAULT 1,
                    cause           TEXT    NOT NULL DEFAULT 'Unknown',
                    symbol          TEXT    NOT NULL DEFAULT '⚰',
                    stored_xp       INTEGER NOT NULL DEFAULT 0
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS chest_items (
                    chest_id   TEXT    NOT NULL,
                    slot_type  TEXT    NOT NULL,
                    slot_index INTEGER NOT NULL,
                    item_data  BLOB,
                    PRIMARY KEY (chest_id, slot_type, slot_index),
                    FOREIGN KEY (chest_id) REFERENCES soul_chests(id) ON DELETE CASCADE
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_limits (
                    player_uuid TEXT    PRIMARY KEY,
                    chest_limit INTEGER NOT NULL
                )
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_chests_owner ON soul_chests(owner_uuid)");
        }
    }

    public List<SoulChestData> loadAllChests() {
        List<SoulChestData> result = new ArrayList<>();
        String sql = "SELECT * FROM soul_chests";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    SoulChestData d = rowToData(rs);
                    if (d != null) result.add(d);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "[SoulChest] Skipping corrupt chest row", e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SoulChest] Failed to load all chests from database", e);
        }
        return result;
    }

    public void saveChest(SoulChestData data) {
        String upsertChest = """
            INSERT OR REPLACE INTO soul_chests
            (id, owner_uuid, owner_name, world_name, x, y, z,
             creation_time, expiration_time, locked, cause, symbol, stored_xp)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        String deleteItems = "DELETE FROM chest_items WHERE chest_id = ?";
        String insertItem  = "INSERT INTO chest_items (chest_id, slot_type, slot_index, item_data) VALUES (?,?,?,?)";

        try {
            connection.setAutoCommit(false);

            // Save chest metadata
            try (PreparedStatement ps = connection.prepareStatement(upsertChest)) {
                ps.setString(1, data.getId());
                ps.setString(2, data.getOwnerUUID().toString());
                ps.setString(3, data.getOwnerName());
                ps.setString(4, data.getWorldName());
                ps.setInt(5, data.getX());
                ps.setInt(6, data.getY());
                ps.setInt(7, data.getZ());
                ps.setLong(8, data.getCreationTime());
                ps.setLong(9, data.getExpirationTime());
                ps.setInt(10, data.isLocked() ? 1 : 0);
                ps.setString(11, data.getCauseOfDeath());
                ps.setString(12, data.getSymbol());
                ps.setInt(13, data.getStoredXp());
                ps.executeUpdate();
            }

            // Delete old items
            try (PreparedStatement ps = connection.prepareStatement(deleteItems)) {
                ps.setString(1, data.getId());
                ps.executeUpdate();
            }

            // Insert new items in batch
            try (PreparedStatement ps = connection.prepareStatement(insertItem)) {
                // Contents
                List<ItemStack> contents = data.getContents();
                for (int i = 0; i < contents.size(); i++) {
                    byte[] bytes = serializeItem(contents.get(i));
                    if (bytes == null) continue;
                    ps.setString(1, data.getId());
                    ps.setString(2, "content");
                    ps.setInt(3, i);
                    ps.setBytes(4, bytes);
                    ps.addBatch();
                }

                // Armour
                ItemStack[] armour = data.getArmour();
                for (int i = 0; i < armour.length; i++) {
                    byte[] bytes = serializeItem(armour[i]);
                    if (bytes == null) continue;
                    ps.setString(1, data.getId());
                    ps.setString(2, "armour");
                    ps.setInt(3, i);
                    ps.setBytes(4, bytes);
                    ps.addBatch();
                }

                // Offhand
                byte[] offBytes = serializeItem(data.getOffHand());
                if (offBytes != null) {
                    ps.setString(1, data.getId());
                    ps.setString(2, "offhand");
                    ps.setInt(3, 0);
                    ps.setBytes(4, offBytes);
                    ps.addBatch();
                }

                ps.executeBatch();
            }

            connection.commit();

        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.SEVERE,
                    "[SoulChest] Failed to save chest " + data.getId(), e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void deleteChest(UUID playerUUID, String chestId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM soul_chests WHERE id = ?")) {
            ps.setString(1, chestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SoulChest] Failed to delete chest " + chestId, e);
        }
    }

    public int getCustomLimit(UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT chest_limit FROM player_limits WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("chest_limit");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SoulChest] Failed to read custom limit", e);
        }
        return -1;
    }

    public void setCustomLimit(UUID playerUUID, int limit) {
        if (limit <= -1) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM player_limits WHERE player_uuid = ?")) {
                ps.setString(1, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[SoulChest] Failed to remove custom limit", e);
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_limits (player_uuid, chest_limit) VALUES (?,?)")) {
                ps.setString(1, playerUUID.toString());
                ps.setInt(2, limit);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[SoulChest] Failed to set custom limit", e);
            }
        }
    }

    private byte[] serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        try {
            return item.serializeAsBytes();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SoulChest] Failed to serialize item", e);
            return null;
        }
    }

    private ItemStack deserializeItem(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SoulChest] Failed to deserialize item", e);
            return null;
        }
    }

    private SoulChestData rowToData(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String uuidStr = rs.getString("owner_uuid");
        UUID ownerUUID;

        try {
            ownerUUID = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SoulChest] Invalid UUID in database: " + uuidStr);
            return null;
        }

        String ownerName   = rs.getString("owner_name");
        String worldName   = rs.getString("world_name");
        int x              = rs.getInt("x");
        int y              = rs.getInt("y");
        int z              = rs.getInt("z");
        long creation      = rs.getLong("creation_time");
        long expiration    = rs.getLong("expiration_time");
        boolean locked     = rs.getInt("locked") == 1;
        String cause       = rs.getString("cause");
        String symbol      = rs.getString("symbol");
        int storedXp       = rs.getInt("stored_xp");

        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(36, null));
        ItemStack[] armour = new ItemStack[4];
        ItemStack offHand = null;

        String itemSql = "SELECT slot_type, slot_index, item_data FROM chest_items WHERE chest_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(itemSql)) {
            ps.setString(1, id);
            try (ResultSet ir = ps.executeQuery()) {
                while (ir.next()) {
                    String slotType = ir.getString("slot_type");
                    int slotIndex = ir.getInt("slot_index");
                    byte[] bytes = ir.getBytes("item_data");
                    ItemStack item = deserializeItem(bytes);

                    switch (slotType) {
                        case "content" -> {
                            if (slotIndex >= 0 && slotIndex < 36) contents.set(slotIndex, item);
                        }
                        case "armour" -> {
                            if (slotIndex >= 0 && slotIndex < 4) armour[slotIndex] = item;
                        }
                        case "offhand" -> offHand = item;
                    }
                }
            }
        }

        return SoulChestData.restore(
                id, ownerUUID, ownerName, worldName, x, y, z,
                contents, armour, offHand, storedXp,
                creation, expiration, locked, cause, symbol);
    }
}