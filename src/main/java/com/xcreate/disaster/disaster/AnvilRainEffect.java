package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 铁砧雨：在落点上方一片地方掉铁砧。
 *
 * <p>铁砧是实体不是方块，落在哪儿由服务端自己算，不经过方块变更入口。这跟水流、沙子塌方
 * 是一类，属于世界的自然演化，不算插件写的地形，所以回放与高度图都不必管它。</p>
 *
 * <p>掉下来的铁砧落地后变成方块，那些方块没人捡——它们本来就该留在那儿，成为玩家的掩体。</p>
 */
public final class AnvilRainEffect implements DisasterEffect {

    public static final String ID = "anvil_rain";

    private static final int DEFAULT_PER_POINT = 3;
    private static final int MAX_PER_POINT = 16;
    private static final int MIN_HEIGHT = 12;
    private static final int MAX_HEIGHT = 24;
    private static final double DEFAULT_SCATTER = 3.0;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        int perPoint = clamp(context.definition().options().getInt("per-point", DEFAULT_PER_POINT),
                1, MAX_PER_POINT);
        double scatter = context.definition().options()
                .getDouble("scatter", DEFAULT_SCATTER);
        World world = context.world();
        int ceiling = world.getMaxHeight() - 2;

        int spawned = 0;
        for (MapPoint point : points) {
            int surface = context.terrain().groundY(point.blockX(), point.blockZ());
            if (surface == SpawnTerrain.NO_GROUND) {
                continue;
            }
            for (int i = 0; i < perPoint; i++) {
                double offsetX = jitter(context, scatter);
                double offsetZ = jitter(context, scatter);
                int height = MIN_HEIGHT + context.random().nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
                Location at = new Location(world,
                        point.blockX() + 0.5 + offsetX,
                        Math.min(surface + height, ceiling),
                        point.blockZ() + 0.5 + offsetZ);
                FallingBlock anvil = world.spawnFallingBlock(at, Material.ANVIL.createBlockData());
                anvil.setDropItem(false);
                anvil.setHurtEntities(true);
                anvil.setVelocity(new Vector(0, -0.6, 0));
                spawned++;
            }
        }
        return spawned;
    }

    private static double jitter(EffectContext context, double scatter) {
        return (context.random().nextDouble() * 2 - 1) * scatter;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
