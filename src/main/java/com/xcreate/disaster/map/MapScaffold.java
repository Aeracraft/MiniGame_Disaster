package com.xcreate.disaster.map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 用代码生成一座测试城市。
 *
 * <p>给开发和联调用的：不必手工做图就能跑通「选图 → 开局 → 放灾难 → 结算」整条链路，
 * 同时也可以当成服主做图时的尺寸参照。</p>
 *
 * <p>方块按街区网格铺开，同一个地图 id 生成的城每次都一样（随机数种子取自 id），
 * 所以复现问题时不必担心地图本身在变。落方块分批进行，不会一次性写几万方块把服务端顶进看门狗。</p>
 */
public final class MapScaffold implements Runnable {

    /** 街区间距：一格 16，其中靠外的 3 格是街道。 */
    private static final int CELL = 16;
    private static final int STREET = 3;

    /** 每个 tick 最多落多少方块。 */
    private static final int BATCH = 8192;

    private static final int MIN_SIZE = 32;
    private static final int MAX_SIZE = 161;

    private static final int SPAWN_COUNT = 16;

    private static final Material[] WALLS = {
            Material.WHITE_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.BRICKS,
            Material.TERRACOTTA,
            Material.OAK_PLANKS,
    };

    /** 生成结果，由调用方并进地图定义。 */
    public record Generated(MapBounds bounds, List<MapPoint> spawns, MapPoint spectatorSpawn,
                            int blocks) {
    }

    private record Placement(int x, int y, int z, Material material) {
    }

    private final JavaPlugin plugin;
    private final World world;
    private final int size;
    private final String seedSource;
    private final Consumer<Generated> onFinished;

    private final ArrayList<Placement> placements = new ArrayList<>();

    private int floorY;
    private int cursor;
    private BukkitTask task;

    /** 计划阶段就失败的，比如世界根本没加载。 */
    private MapScaffold(JavaPlugin plugin, World world, int size, String seedSource,
                        Consumer<Generated> onFinished) {
        this.plugin = plugin;
        this.world = world;
        this.size = size;
        this.seedSource = seedSource;
        this.onFinished = onFinished;
    }

    /**
     * 取一个已经加载的世界，没有就现造一个超平坦。
     *
     * <p>不指定生成器预设——各版本对预设字符串的接受程度不一样，直接用默认平地，
     * 城市自己会在地表往上砌。</p>
     */
    public static World prepareWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        return new WorldCreator(name)
                .type(WorldType.FLAT)
                .generateStructures(false)
                .createWorld();
    }

    /** 边长限制在可生成区间内，向上取奇数，保证中心点上有一格。 */
    public static int clampSize(int requested) {
        int clamped = Math.max(MIN_SIZE, Math.min(MAX_SIZE, requested));
        return clamped % 2 == 0 ? clamped + 1 : clamped;
    }

    /**
     * 开始生成。
     *
     * @param onFinished 全部方块落完后的回调，在主线程执行
     */
    public static void start(JavaPlugin plugin, World world, int size, String seedSource,
                             Consumer<Generated> onFinished) {
        MapScaffold scaffold = new MapScaffold(plugin, world, clampSize(size), seedSource, onFinished);
        scaffold.plan();
        scaffold.task = Bukkit.getScheduler().runTaskTimer(plugin, scaffold, 1L, 1L);
    }

    public int total() {
        return placements.size();
    }

    public int percent() {
        return placements.isEmpty() ? 100 : (int) (cursor * 100L / placements.size());
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) {
            cancel();
            return;
        }

        int placed = 0;
        while (cursor < placements.size() && placed < BATCH) {
            Placement placement = placements.get(cursor++);
            Block block = world.getBlockAt(placement.x(), placement.y(), placement.z());
            if (block.getType() != placement.material()) {
                block.setType(placement.material(), false);
            }
            placed++;
        }

        if (cursor >= placements.size()) {
            cancel();
            onFinished.accept(result());
        }
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void plan() {
        int half = size / 2;
        floorY = surfaceY();

        planGround(half);
        planBuildings(half);
        world.setSpawnLocation(0, floorY + 1, 0, 0f);
        placements.trimToSize();
    }

    private int surfaceY() {
        try {
            return world.getHighestBlockYAt(0, 0);
        } catch (Throwable t) {
            return world.getMinHeight();
        }
    }

    private void planGround(int half) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean street = isStreet(x) || isStreet(z);
                boolean centerLine = isCenterLine(x) || isCenterLine(z);
                Material top = street
                        ? (centerLine ? Material.GRAY_CONCRETE : Material.STONE_BRICKS)
                        : Material.GRASS_BLOCK;
                add(x, floorY, z, top);
                add(x, floorY - 1, z, Material.DIRT);
                add(x, floorY - 2, z, Material.STONE);
            }
        }
    }

    private void planBuildings(int half) {
        int cells = half / CELL + 1;
        for (int i = -cells; i <= cells; i++) {
            for (int j = -cells; j <= cells; j++) {
                int minX = Math.max(i * CELL + STREET, -half);
                int maxX = Math.min(i * CELL + CELL - 1, half);
                int minZ = Math.max(j * CELL + STREET, -half);
                int maxZ = Math.min(j * CELL + CELL - 1, half);
                if (maxX - minX < 5 || maxZ - minZ < 5) {
                    continue;
                }
                Random random = new Random(seed(i, j));
                if (random.nextInt(7) == 0) {
                    continue;
                }
                planBuilding(random, minX + 1, maxX - 1, minZ + 1, maxZ - 1);
            }
        }
    }

    private void planBuilding(Random random, int minX, int maxX, int minZ, int maxZ) {
        Material wall = WALLS[random.nextInt(WALLS.length)];
        int floors = 2 + random.nextInt(5);
        int top = floorY + floors * 3;

        for (int y = floorY + 1; y <= top; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        add(x, y, z, wall);
                    }
                }
            }
        }

        // 每层中间开一排窗，省方块也好看
        for (int floor = 0; floor < floors; floor++) {
            int y = floorY + floor * 3 + 2;
            for (int x = minX + 1; x < maxX; x += 2) {
                add(x, y, minZ, Material.GLASS);
                add(x, y, maxZ, Material.GLASS);
            }
            for (int z = minZ + 1; z < maxZ; z += 2) {
                add(minX, y, z, Material.GLASS);
                add(maxX, y, z, Material.GLASS);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                add(x, top + 1, z, Material.SMOOTH_STONE);
            }
        }
    }

    private Generated result() {
        int half = size / 2;
        MapBounds bounds = new MapBounds(-half - 1, floorY - 2, -half - 1,
                half + 1, floorY + 48, half + 1);

        List<int[]> intersections = new ArrayList<>();
        int cells = half / CELL;
        for (int i = -cells; i <= cells; i++) {
            for (int j = -cells; j <= cells; j++) {
                intersections.add(new int[]{i * CELL, j * CELL});
            }
        }
        int step = Math.max(1, intersections.size() / SPAWN_COUNT);
        List<MapPoint> spawns = new ArrayList<>();
        for (int index = 0; index < intersections.size() && spawns.size() < SPAWN_COUNT; index += step) {
            int[] point = intersections.get(index);
            spawns.add(new MapPoint(world.getName(), "路口 " + (spawns.size() + 1),
                    point[0] + 0.5, floorY + 1, point[1] + 0.5, 0f, 0f));
        }
        if (spawns.isEmpty()) {
            spawns.add(new MapPoint(world.getName(), "中心", 0.5, floorY + 1, 0.5, 0f, 0f));
        }

        MapPoint spectator = new MapPoint(world.getName(), "观战",
                0.5, floorY + 40, 0.5, 0f, 45f);
        return new Generated(bounds, spawns, spectator, placements.size());
    }

    private long seed(int i, int j) {
        long base = seedSource == null ? 0L : seedSource.hashCode();
        return base * 31L + i * 73856093L + j * 19349663L;
    }

    private static boolean isStreet(int value) {
        return Math.floorMod(value, CELL) < STREET;
    }

    private static boolean isCenterLine(int value) {
        return Math.floorMod(value, CELL) == STREET / 2;
    }

    private void add(int x, int y, int z, Material material) {
        placements.add(new Placement(x, y, z, material));
    }
}
