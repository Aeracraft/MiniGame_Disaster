package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 落雷：高地与玩家附近一起劈，落地前先给一段预警。
 *
 * <p>预警不是装饰。没有预警的落雷等于随机处决，玩家既躲不掉也看不出哪里危险。
 * 时长走 {@code disaster.lightning-warning-ticks}。</p>
 *
 * <p>用服务端自己的落雷而不是只放个特效：这样死亡原因能落成 {@code lightning}，
 * 打出来的火也归服务端管。火是世界的自然演化，跟插件写的地形是两回事。</p>
 */
public final class LightningEffect implements DisasterEffect {

    public static final String ID = "lightning";

    /** 玩家附近额外补几道。落点全在高地上的话，躲进坑里的反而最安全。 */
    private static final int DEFAULT_NEAR_STRIKES = 3;
    private static final int MAX_NEAR_STRIKES = 12;
    private static final double DEFAULT_NEAR_RADIUS = 6.0;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        // 落雷不写方块，破坏全交给服务端，这里没有要记的变更
        return 0;
    }

    @Override
    public Optional<ActiveDisaster> activate(EffectContext context, List<MapPoint> points) {
        List<Location> targets = new ArrayList<>();
        if (points != null) {
            for (MapPoint point : points) {
                targets.add(point.toLocation(context.world()));
            }
        }
        targets.addAll(nearPlayers(context));
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        int warning = context.config() == null
                ? 30 : context.config().disasters().lightningWarningTicks();
        return Optional.of(new Strike(targets, Math.max(0, warning)));
    }

    /** 在随机几位存活玩家附近各补一道。挑不到地面的就少劈一道。 */
    private static List<Location> nearPlayers(EffectContext context) {
        List<Player> alive = context.players();
        int count = clamp(context.definition().options().getInt("near-strikes", DEFAULT_NEAR_STRIKES),
                0, MAX_NEAR_STRIKES);
        if (alive.isEmpty() || count == 0) {
            return List.of();
        }
        double radius = context.definition().options().getDouble("near-radius", DEFAULT_NEAR_RADIUS);
        List<Location> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Player player = alive.get(context.random().nextInt(alive.size()));
            Location at = spotNear(context, player, radius);
            if (at != null) {
                targets.add(at);
            }
        }
        return targets;
    }

    private static Location spotNear(EffectContext context, Player player, double radius) {
        Location base = player.getLocation();
        double angle = context.random().nextDouble() * Math.PI * 2;
        double distance = radius * (0.5 + context.random().nextDouble() * 0.5);
        int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        if (context.hasBounds() && !context.bounds().contains(x + 0.5, base.getY(), z + 0.5)) {
            return null;
        }
        return groundAt(context, x, z);
    }

    private static Location groundAt(EffectContext context, int x, int z) {
        int surface = context.terrain().groundY(x, z);
        if (surface == SpawnTerrain.NO_GROUND) {
            return null;
        }
        return new Location(context.world(), x + 0.5, surface + 1, z + 0.5);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 预警一段再劈。
     *
     * <p>预警用烟与雷声而不是文字——玩家当时正在跑，只有落到地上的东西才看得见。</p>
     */
    private static final class Strike implements ActiveDisaster {

        private final List<Location> targets;
        private final int warningSeconds;

        private int strikeAtSecond = -1;

        private Strike(List<Location> targets, int warningSeconds) {
            this.targets = targets;
            this.warningSeconds = warningSeconds;
        }

        @Override
        public boolean tick(EffectContext context, int elapsedSeconds) {
            if (strikeAtSecond < 0) {
                strikeAtSecond = elapsedSeconds + warningSeconds;
                warn();
                if (warningSeconds <= 0) {
                    strike();
                    return false;
                }
                return true;
            }
            if (elapsedSeconds < strikeAtSecond) {
                return true;
            }
            strike();
            return false;
        }

        private void warn() {
            for (Location at : targets) {
                World world = at.getWorld();
                if (world == null) {
                    continue;
                }
                world.spawnParticle(Particle.CLOUD, at, 12, 0.7, 0.1, 0.7, 0.01);
                world.spawnParticle(Particle.FLAME, at, 8, 0.5, 0.2, 0.5, 0.01);
                world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
            }
        }

        private void strike() {
            for (Location at : targets) {
                World world = at.getWorld();
                if (world != null && world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                    world.strikeLightning(at);
                }
            }
        }
    }
}
