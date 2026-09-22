package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 流星雨：每个落点砸一个碗形坑，坑心铺岩浆、坑沿挂火，站在旁边的人被点着并掀开。
 *
 * <p>坑形直接复用 {@link SinkholeShape}——同一种碗形算术，没必要为「陨石坑」再写一遍。</p>
 *
 * <p>伤害走点燃而不是直接扣血。直接扣血在死亡原因里拿不到像样的分类，点燃会落成
 * {@code fire}，玩家也看得见自己身上着火，知道该往哪跑。</p>
 */
public final class MeteorEffect implements DisasterEffect {

    public static final String ID = "meteor_shower";

    private static final int DEFAULT_RADIUS = 3;
    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_RADIUS = 10;
    private static final int MAX_DEPTH = 16;

    private static final double DEFAULT_SCORCH_RADIUS = 6.0;
    private static final int FIRE_TICKS = 80;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        int radius = clamp(context.definition().options().getInt("crater-radius", DEFAULT_RADIUS),
                1, MAX_RADIUS);
        int depth = clamp(context.definition().options().getInt("crater-depth", DEFAULT_DEPTH),
                1, MAX_DEPTH);

        int changed = 0;
        for (MapPoint point : points) {
            int centerX = point.blockX();
            int centerZ = point.blockZ();
            changed += impact(context, centerX, centerZ, radius, depth);
            scorch(context, centerX, centerZ);
        }
        return changed;
    }

    private int impact(EffectContext context, int centerX, int centerZ, int radius, int depth) {
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
                changed += hollow(context, x, z, surface, dig, floor);

                if (dig >= depth - 1) {
                    // 坑心几圈留一层岩浆，别的地方只挖不烧
                    changed += burn(context, x, z, surface - dig, Material.MAGMA_BLOCK, floor);
                } else if (dig == 1 && context.random().nextInt(4) == 0) {
                    // 坑沿零星挂火，数量不多，看过去像溅出来的
                    changed += burn(context, x, z, surface, Material.FIRE, floor);
                }
            }
        }
        return changed;
    }

    private int hollow(EffectContext context, int x, int z, int surface, int dig, int floor) {
        World world = context.world();
        int changed = 0;
        for (int i = 0; i < dig; i++) {
            int y = surface - i;
            if (y < floor) {
                break;
            }
            Block block = world.getBlockAt(x, y, z);
            if (DigRule.diggable(block) && context.blocks().set(block, Material.AIR, false, ID)) {
                changed++;
            }
        }
        return changed;
    }

    /** 往某一格放东西。只有目标是空气时才放，免得把还没挖透的实心石头换成火。 */
    private int burn(EffectContext context, int x, int z, int y, Material material, int floor) {
        World world = context.world();
        if (y < floor) {
            return 0;
        }
        Block block = world.getBlockAt(x, y, z);
        if (material == Material.FIRE && block.getType() != Material.AIR) {
            return 0;
        }
        return context.blocks().set(block, material, false, ID) ? 1 : 0;
    }

    private void scorch(EffectContext context, int centerX, int centerZ) {
        double radius = context.definition().options().getDouble("scorch-radius", DEFAULT_SCORCH_RADIUS);
        for (Player player : context.players()) {
            Location location = player.getLocation();
            if (!context.world().equals(location.getWorld())) {
                continue;
            }
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            if (Math.sqrt(dx * dx + dz * dz) > radius) {
                continue;
            }
            player.setFireTicks(FIRE_TICKS);
            // 从坑心往外掀，别把站在坑边的人推进坑底
            Vector push = new Vector(dx, 0, dz);
            if (push.lengthSquared() < 0.01) {
                push = new Vector(1, 0, 0);
            }
            player.setVelocity(player.getVelocity()
                    .add(push.normalize().multiply(1.2).setY(0.45)));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
