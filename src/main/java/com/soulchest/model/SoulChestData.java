package com.soulchest.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SoulChestData {

    private final String id;
    private final UUID   ownerUUID;
    private final String ownerName;

    private final String worldName;
    private final int    x;
    private final int    y;
    private final int    z;

    private final List<ItemStack> contents;
    private final ItemStack[]     armour;
    private final ItemStack       offHand;
    private final int             storedXp;

    private final long creationTime;
    private final long expirationTime;

    private final boolean locked;
    private final String  causeOfDeath;
    private final String  symbol;

    private SoulChestData(String id, UUID ownerUUID, String ownerName,
                          String worldName, int x, int y, int z,
                          List<ItemStack> contents, ItemStack[] armour,
                          ItemStack offHand, int storedXp,
                          long creationTime, long expirationTime,
                          boolean locked, String causeOfDeath, String symbol) {
        this.id             = id;
        this.ownerUUID      = ownerUUID;
        this.ownerName      = ownerName;
        this.worldName      = worldName;
        this.x              = x;
        this.y              = y;
        this.z              = z;
        this.contents       = (contents != null) ? new ArrayList<>(contents) : new ArrayList<>();
        this.armour         = (armour   != null) ? armour.clone()            : new ItemStack[4];
        this.offHand        = offHand;
        this.storedXp       = storedXp;
        this.creationTime   = creationTime;
        this.expirationTime = expirationTime;
        this.locked         = locked;
        this.causeOfDeath   = (causeOfDeath != null) ? causeOfDeath : "Unknown";
        this.symbol         = (symbol       != null) ? symbol        : "⛀";
    }

    public static SoulChestData create(String id,
                                       UUID ownerUUID,
                                       String ownerName,
                                       Location chestLocation,
                                       List<ItemStack> contents,
                                       ItemStack[] armour,
                                       ItemStack offHand,
                                       int storedXp,
                                       long durationSecs,
                                       boolean locked,
                                       String causeOfDeath,
                                       String symbol) {
        long now        = System.currentTimeMillis();
        long expiration = (durationSecs <= 0) ? -1L : now + (durationSecs * 1_000L);
        return new SoulChestData(
                id, ownerUUID, ownerName,
                chestLocation.getWorld().getName(),
                chestLocation.getBlockX(),
                chestLocation.getBlockY(),
                chestLocation.getBlockZ(),
                contents, armour, offHand, storedXp,
                now, expiration,
                locked, causeOfDeath, symbol
        );
    }

    public static SoulChestData restore(String id,
                                        UUID ownerUUID,
                                        String ownerName,
                                        String worldName,
                                        int x, int y, int z,
                                        List<ItemStack> contents,
                                        ItemStack[] armour,
                                        ItemStack offHand,
                                        int storedXp,
                                        long creationTime,
                                        long expirationTime,
                                        boolean locked,
                                        String causeOfDeath,
                                        String symbol) {
        return new SoulChestData(
                id, ownerUUID, ownerName,
                worldName, x, y, z,
                contents, armour, offHand, storedXp,
                creationTime, expirationTime,
                locked, causeOfDeath, symbol
        );
    }

    public boolean isExpired() {
        return expirationTime != -1 && System.currentTimeMillis() > expirationTime;
    }

    public long secondsRemaining() {
        if (expirationTime == -1) return -1L;
        long remaining = (expirationTime - System.currentTimeMillis()) / 1_000L;
        return Math.max(0, remaining);
    }

    public String formattedTimeLeft() {
        long secs = secondsRemaining();
        if (secs == -1) return "oo";
        if (secs ==  0) return "Expired";
        long mins = secs / 60;
        long s    = secs % 60;
        return String.format("%02d:%02d", mins, s);
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public SoulChestData withLocked(boolean locked) {
        return new SoulChestData(id, ownerUUID, ownerName, worldName, x, y, z,
                contents, armour, offHand, storedXp,
                creationTime, expirationTime, locked, causeOfDeath, symbol);
    }

    public SoulChestData withSymbol(String newSymbol) {
        return new SoulChestData(id, ownerUUID, ownerName, worldName, x, y, z,
                contents, armour, offHand, storedXp,
                creationTime, expirationTime, locked, causeOfDeath, newSymbol);
    }

    public SoulChestData withLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return this;
        return new SoulChestData(id, ownerUUID, ownerName,
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                contents, armour, offHand, storedXp,
                creationTime, expirationTime, locked, causeOfDeath, symbol);
    }

    public String          getId()             { return id; }
    public UUID            getOwnerUUID()      { return ownerUUID; }
    public String          getOwnerName()      { return ownerName; }
    public String          getWorldName()      { return worldName; }
    public int             getX()              { return x; }
    public int             getY()              { return y; }
    public int             getZ()              { return z; }
    public List<ItemStack> getContents()       { return new ArrayList<>(contents); }
    public ItemStack[]     getArmour()         { return armour.clone(); }
    public ItemStack       getOffHand()        { return offHand; }
    public int             getStoredXp()       { return storedXp; }
    public long            getCreationTime()   { return creationTime; }
    public long            getExpirationTime() { return expirationTime; }
    public boolean         isLocked()          { return locked; }
    public String          getCauseOfDeath()   { return causeOfDeath; }
    public String          getSymbol()         { return symbol; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoulChestData other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "SoulChest{id=" + id + ", owner=" + ownerName
               + ", loc=[" + worldName + " " + x + "," + y + "," + z + "]"
               + ", expired=" + isExpired() + "}";
    }
}