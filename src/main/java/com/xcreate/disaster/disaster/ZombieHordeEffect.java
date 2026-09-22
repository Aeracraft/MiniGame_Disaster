package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.List;

/**
 * 僵尸潮：在落点附近刷僵尸，顺手锁定最近的存活玩家。
 *
 * <p>刷出来的就是普通僵尸，不给装备也不加属性。灾难的难度该来自数量与地形，靠把普通怪
 * 硬调成精英来加难度，服主反而没法用服务端自己的难度设置去调平衡。</p>
 *
 * <p>每次刷新的数量有上限：一波几十只、一秒内刷出上百只实体，服务端会先在怪物 AI 上卡住。</p>
 */
public final class ZombieHordeEffect implements DisasterEffect {

    public static final String ID = "zombie_apocalypse";

    private static final int DEFAULT_PER_POINT = 2;
    private static final int MAX_PER_POINT = 8;
    private static final double DEFAULT_SCATTER = 3.0;

    /** 锁定玩家的距离上限。再远的干脆不锁，让它自己慢慢找。 */
    private static final double TARGET_RANGE = 32.0;

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

        int spawned = 0;
        for (MapPoint point : points) {
            for (int i = 0; i < perPoint; i++) {
                Location at = spotNear(context, point, scatter);
                if (at == null) {
                    continue;
                }
                Zombie zombie = (Zombie) context.world().spawnEntity(at, EntityType.ZOMBIE);
                zombie.setTarget(nearestPlayer(context, at));
                spawned++;
            }
        }
        return spawned;
    }

    /** 在落点周围找个能站人的位置。找不到地面就返回空，宁可不刷也不要把僵尸塞进石头里。 */
    private static Location spotNear(EffectContext context, MapPoint point, double scatter) {
        double offsetX = (context.random().nextDouble() * 2 - 1) * scatter;
        double offsetZ = (context.random().nextDouble() * 2 - 1) * scatter;
        int x = point.blockX() + (int) Math.round(offsetX);
        int z = point.blockZ() + (int) Math.round(offsetZ);
        if (context.hasBounds() && !context.bounds().contains(x + 0.5, point.y(), z + 0.5)) {
            return null;
        }
        int surface = context.terrain().groundY(x, z);
        if (surface == SpawnTerrain.NO_GROUND) {
            return null;
        }
        return new Location(context.world(), x + 0.5, surface + 1, z + 0.5);
    }

    private static Player nearestPlayer(EffectContext context, Location from) {
        Player nearest = null;
        double best = TARGET_RANGE * TARGET_RANGE;
        for (Player player : context.players()) {
            if (!context.world().equals(player.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(from);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
