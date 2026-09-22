package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

/**
 * 龙卷风：从图边进来、一路推向对侧，沿途把人往风眼里拽，顺手掀掉地表。
 *
 * <p>路线见 {@link TornadoPath}。推人用速度而不是直接改坐标：直接改坐标会跟玩家自己的
 * 移动打架，客户端也会把人拽回去。</p>
 *
 * <p>只掀最上面一格。往下挖的话，一路过去就是一条沟，玩家反而多了掩体。</p>
 */
public final class TornadoEffect implements DisasterEffect {

    public static final String ID = "tornado";

    private static final double DEFAULT_SPEED = 5.0;
    private static final double DEFAULT_PULL_RADIUS = 10.0;
    private static final double DEFAULT_PULL_STRENGTH = 1.1;
    private static final int DEFAULT_SWEEP_RADIUS = 2;
    private static final int MAX_SWEEP_RADIUS = 5;

    /** 没有边界时用步数兜底，免得风一直刮到服务器重启。 */
    private static final int MAX_STEPS = 240;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        // 掀地表的活儿在活动实例里一步步做，落下来这一下不动手
        return 0;
    }

    @Override
    public Optional<ActiveDisaster> activate(EffectContext context, List<MapPoint> points) {
        MapBounds bounds = context.bounds();
        MapPoint anchor = points == null || points.isEmpty() ? null : points.get(0);

        TornadoPath.Spot entry;
        TornadoPath.Spot direction;
        if (bounds != null) {
            entry = anchor != null
                    ? new TornadoPath.Spot(anchor.x(), anchor.z())
                    : TornadoPath.enter(bounds, context.random());
            direction = TornadoPath.direction(bounds, entry);
        } else if (anchor != null) {
            entry = new TornadoPath.Spot(anchor.x(), anchor.z());
            direction = new TornadoPath.Spot(1.0, 0.0);
        } else {
            return Optional.empty();
        }

        double speed = Math.max(1.0, context.definition().options().getDouble("speed", DEFAULT_SPEED));
        double pullRadius = context.definition().options()
                .getDouble("pull-radius", DEFAULT_PULL_RADIUS);
        double pullStrength = context.definition().options()
                .getDouble("pull-strength", DEFAULT_PULL_STRENGTH);
        int sweepRadius = Math.max(0, Math.min(MAX_SWEEP_RADIUS, context.definition().options()
                .getInt("sweep-radius", DEFAULT_SWEEP_RADIUS)));

        return Optional.of(new Vortex(bounds, entry, direction, speed,
                pullRadius, pullStrength, sweepRadius));
    }

    private static final class Vortex implements ActiveDisaster {

        private final MapBounds bounds;
        private final TornadoPath.Spot direction;
        private final double speed;
        private final double pullRadius;
        private final double pullStrength;
        private final int sweepRadius;

        private TornadoPath.Spot position;
        private int steps;

        private Vortex(MapBounds bounds, TornadoPath.Spot entry, TornadoPath.Spot direction,
                       double speed, double pullRadius, double pullStrength, int sweepRadius) {
            this.bounds = bounds;
            this.position = entry;
            this.direction = direction;
            this.speed = speed;
            this.pullRadius = pullRadius;
            this.pullStrength = pullStrength;
            this.sweepRadius = sweepRadius;
        }

        @Override
        public boolean tick(EffectContext context, int elapsedSeconds) {
            position = position.moved(direction, speed);
            if (++steps > MAX_STEPS) {
                return false;
            }
            mark(context);
            pull(context);
            tear(context);
            return bounds == null || !TornadoPath.done(bounds, position);
        }

        private void mark(EffectContext context) {
            World world = context.world();
            Location at = new Location(world, position.x(), world.getMinHeight() + 1, position.z());
            world.spawnParticle(Particle.CLOUD, at, 40, 1.6, 1.0, 1.6, 0.05);
        }

        private void pull(EffectContext context) {
            for (Player player : context.players()) {
                Location location = player.getLocation();
                if (!context.world().equals(location.getWorld())) {
                    continue;
                }
                double distance = TornadoPath.distanceTo(position, location.getX(), location.getZ());
                if (distance > pullRadius) {
                    continue;
                }
                Vector pull = new Vector(position.x() - location.getX(), 0,
                        position.z() - location.getZ());
                if (pull.lengthSquared() < 1.0e-4) {
                    pull = new Vector(0, 0, 0);
                } else {
                    pull.normalize().multiply(pullStrength * (1.0 - distance / pullRadius));
                }
                // 往上抬一点，不然只是被推着贴地平移，看着不像被卷进去
                player.setVelocity(player.getVelocity().add(pull.setY(0.45)));
            }
        }

        private void tear(EffectContext context) {
            if (sweepRadius <= 0) {
                return;
            }
            World world = context.world();
            int centerX = (int) Math.floor(position.x());
            int centerZ = (int) Math.floor(position.z());
            int span = sweepRadius * sweepRadius;

            for (int dx = -sweepRadius; dx <= sweepRadius; dx++) {
                for (int dz = -sweepRadius; dz <= sweepRadius; dz++) {
                    if (dx * dx + dz * dz > span) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (outside(bounds, x, z) || !world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    int surface = context.terrain().groundY(x, z);
                    if (surface == SpawnTerrain.NO_GROUND) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, surface, z);
                    if (DigRule.diggable(block)) {
                        context.blocks().set(block, Material.AIR, false, ID);
                    }
                }
            }
        }

        private static boolean outside(MapBounds bounds, int x, int z) {
            return bounds != null
                    && (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ());
        }
    }
}
