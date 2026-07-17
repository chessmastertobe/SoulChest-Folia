package com.soulchest.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

public final class SafeLocationFinder {

    private SafeLocationFinder() {}

    private static final Set<Material> HAZARD_FLOOR = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POWDER_SNOW
    );

    private static final Set<Material> DEADLY_SPACE = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.MAGMA_BLOCK,
            Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH,
            Material.CACTUS, Material.POWDER_SNOW
    );

    private static final int NETHER_MAX_Y = 110;

    public static boolean isSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) return false;

        Block floorBlock = world.getBlockAt(x, y - 1, z);
        Block chestBlock = world.getBlockAt(x, y, z);
        Block headBlock  = world.getBlockAt(x, y + 1, z);

        if (!isValidFloor(floorBlock)) return false;
        if (!chestBlock.getType().isAir()) return false;
        if (!isPassable(headBlock.getType())) return false;
        if (DEADLY_SPACE.contains(headBlock.getType())) return false;

        return true;
    }

    public static boolean isDangerous(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        World world = loc.getWorld();
        int y = loc.getBlockY();

        if (y < world.getMinHeight()) return true;
        if (world.getEnvironment() == World.Environment.THE_END && y < 0) return true;

        Block block = world.getBlockAt(loc);
        if (block.getType() == Material.LAVA) return true;

        if (y > world.getMinHeight()) {
            Block below = world.getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ());
            if (below.getType() == Material.LAVA) return true;
        }
        return false;
    }

    public static Location findSafeGuaranteed(Location deathLoc, int radiusChunks, boolean alwaysSearch) {
        if (deathLoc == null || deathLoc.getWorld() == null) return null;

        World world = deathLoc.getWorld();

        Location near = findSafe(deathLoc, radiusChunks);
        if (near != null) return near;

        Location spawnLoc = world.getSpawnLocation();
        Location nearSpawn = findSafe(spawnLoc, 5);
        if (nearSpawn != null) return nearSpawn;

        int sx = spawnLoc.getBlockX();
        int sz = spawnLoc.getBlockZ();
        int yMin = chestYMin(world);
        int yMax = chestYMax(world);

        // Load spawn chunk safely
        world.getChunkAt(sx >> 4, sz >> 4).load(true);

        for (int y = yMax; y >= yMin; y--) {
            Location c = new Location(world, sx, y, sz);
            if (isSafe(c)) return c;
        }

        return new Location(world, sx, world.getHighestBlockYAt(sx, sz) + 1, sz);
    }

    public static Location findSafe(Location origin, int radiusChunks) {
        if (origin == null || origin.getWorld() == null) return null;

        World world = origin.getWorld();
        int rBlocks = radiusChunks * 16;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int yMin = chestYMin(world);
        int yMax = chestYMax(world);

        int maxYStep = Math.max(oy - yMin, yMax - oy) + 1;

        for (int yStep = 0; yStep <= maxYStep; yStep++) {
            for (int shell = 0; shell <= rBlocks; shell++) {
                if (shell == 0) {
                    Location h = checkY(world, ox, oz, oy, yStep, yMin, yMax);
                    if (h != null) return h;
                } else {
                    for (int dx = -shell; dx <= shell; dx++) {
                        Location h1 = checkY(world, ox + dx, oz + shell, oy, yStep, yMin, yMax);
                        if (h1 != null) return h1;
                        Location h2 = checkY(world, ox + dx, oz - shell, oy, yStep, yMin, yMax);
                        if (h2 != null) return h2;
                    }
                    for (int dz = -shell + 1; dz <= shell - 1; dz++) {
                        Location h3 = checkY(world, ox - shell, oz + dz, oy, yStep, yMin, yMax);
                        if (h3 != null) return h3;
                        Location h4 = checkY(world, ox + shell, oz + dz, oy, yStep, yMin, yMax);
                        if (h4 != null) return h4;
                    }
                }
            }
        }
        return null;
    }

    private static Location checkY(World world, int x, int z, int originY,
                                   int yStep, int yMin, int yMax) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;

        if (yStep == 0) {
            int y = clamp(originY, yMin, yMax);
            Location loc = new Location(world, x, y, z);
            return isSafe(loc) ? loc : null;
        }

        int yBelow = originY - yStep;
        if (yBelow >= yMin) {
            Location loc = new Location(world, x, yBelow, z);
            if (isSafe(loc)) return loc;
        }

        int yAbove = originY + yStep;
        if (yAbove <= yMax) {
            Location loc = new Location(world, x, yAbove, z);
            if (isSafe(loc)) return loc;
        }

        return null;
    }

    private static int chestYMin(World world) {
        int min = world.getMinHeight() + 1;
        if (world.getEnvironment() == World.Environment.NETHER) min = Math.max(min, 2);
        if (world.getEnvironment() == World.Environment.THE_END) min = Math.max(min, 1);
        return min;
    }

    private static int chestYMax(World world) {
        int max = world.getMaxHeight() - 2;
        if (world.getEnvironment() == World.Environment.NETHER)
            max = Math.min(max, NETHER_MAX_Y);
        return max;
    }

    private static boolean isValidFloor(Block block) {
        Material mat = block.getType();
        if (mat.isAir()) return false;
        if (HAZARD_FLOOR.contains(mat)) return false;
        if (mat.isSolid()) return true;
        return !DEADLY_SPACE.contains(mat);
    }

    private static boolean isPassable(Material mat) {
        if (DEADLY_SPACE.contains(mat)) return false;
        return !mat.isSolid();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}