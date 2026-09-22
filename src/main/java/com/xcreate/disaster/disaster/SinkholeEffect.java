package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * 地陷：在落点处挖一个碗形坑。
 *
 * <p>一次成型，不留持续状态。坑形见 {@link SinkholeShape}。</p>
 */
public final class SinkholeEffect implements DisasterEffect {

    public static final String ID = "sinkhole";

    private static final int DEFAULT_RADIUS = 8;
    private static final int DEFAULT_DEPTH = 4;

    /** 上限。手改配置写成几百的话，一个坑就能把主线程钉死一整秒。 */
    private static final int MAX_RADIUS = 24;
    private static final int MAX_DEPTH = 16;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        int radius = clamp(context.definition().options().getInt("radius", DEFAULT_RADIUS),
                1, MAX_RADIUS);
        int depth = clamp(context.definition().options().getInt("depth", DEFAULT_DEPTH),
                1, MAX_DEPTH);

        int changed = 0;
        for (MapPoint point : points) {
            changed += carve(context, point.blockX(), point.blockZ(), radius, depth);
        }
        return changed;
    }

    /** 挖一个坑。每一列各取自己的地表高度，坡地上的坑沿才不会被一起削平。 */
    private int carve(EffectContext context, int centerX, int centerZ, int radius, int depth) {
        World world = context.world();
        int floor = world.getMinHeight();
        int changed = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int dig = SinkholeShape.depthAt(dx, dz, radius, depth);
                if (dig == 0) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                int surface = context.terrain().groundY(x, z);
                if (surface == SpawnTerrain.NO_GROUND) {
                    continue;
                }
                for (int i = 0; i < dig; i++) {
                    int y = surface - i;
                    if (y < floor) {
                        break;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (!DigRule.diggable(block)) {
                        continue;
                    }
                    if (context.blocks().set(block, Material.AIR, false, ID)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
