package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * 酸雨：全图露天的地方被蚀穿。
 *
 * <p>只吃露天的柱子。有屋顶或树冠盖住的柱子不受影响，玩家因此可以搭个棚子躲过去——
 * 一个躲不掉的灾种约等于随机处决，那不好玩。</p>
 *
 * <p>作用范围是「从地表往下几格」，蚀到一半留一层砂砾，免得每根柱子都变成通往洞穴的竖井。</p>
 */
public final class AcidRainEffect implements DisasterEffect {

    public static final String ID = "acid_rain";

    private static final int DEFAULT_COLUMNS = 400;
    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_COLUMNS = 4000;
    private static final int MAX_DEPTH = 12;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        if (!context.hasBounds()) {
            return 0;
        }
        int columns = clamp(context.definition().options().getInt("columns", DEFAULT_COLUMNS),
                1, MAX_COLUMNS);
        int depth = clamp(context.definition().options().getInt("depth", DEFAULT_DEPTH),
                1, MAX_DEPTH);

        World world = context.world();
        int changed = 0;
        for (ColumnSampler.Column column : ColumnSampler.pick(context.bounds(), context.random(), columns)) {
            if (!world.isChunkLoaded(column.x() >> 4, column.z() >> 4)) {
                continue;
            }
            int surface = context.terrain().groundY(column.x(), column.z());
            if (surface == SpawnTerrain.NO_GROUND || !exposed(world, column.x(), surface, column.z())) {
                continue;
            }
            changed += erode(context, column.x(), surface, column.z(), depth);
        }
        return changed;
    }

    /** 地表往上连着两格通空才算露天。只看一格的话，一层树叶就能把整棵树底下都算成露天。 */
    private static boolean exposed(World world, int x, int surface, int z) {
        int ceiling = world.getMaxHeight() - 1;
        for (int y = surface + 1; y <= Math.min(surface + 2, ceiling); y++) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** 蚀穿一列：上面几格挖空，最底下一格换成砂砾。 */
    private int erode(EffectContext context, int x, int surface, int z, int depth) {
        World world = context.world();
        int floor = world.getMinHeight();
        int changed = 0;
        for (int i = 0; i < depth; i++) {
            int y = surface - i;
            if (y < floor) {
                break;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!DigRule.diggable(block)) {
                continue;
            }
            Material target = i == depth - 1 ? Material.GRAVEL : Material.AIR;
            if (context.blocks().set(block, target, false, ID)) {
                changed++;
            }
        }
        return changed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
