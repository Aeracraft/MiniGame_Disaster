package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * 脚下岩浆：把玩家踩着的那一格换成岩浆。
 *
 * <p>隔几秒换一次，不是每 tick 换。每 tick 换等于站着就死，玩家连抬脚的机会都没有；
 * 隔几秒就有得选——要么一直跑，要么拿方块自己铺路。</p>
 *
 * <p>只换玩家正下方那一格，不往下挖。挖一排下去就是条火沟，等于把玩家的退路先烧了。</p>
 */
public final class FloorIsLavaEffect implements DisasterEffect {

    public static final String ID = "floor_is_lava";

    private static final int DEFAULT_INTERVAL_SECONDS = 3;
    private static final int MIN_INTERVAL_SECONDS = 1;

    /** 只烧脚底下这点高度内的。人在半空飞的时候，不该把几十格以下的地面也点着。 */
    private static final int REACH = 2;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        // 跟随玩家的活儿在活动实例里按时做
        return 0;
    }

    @Override
    public Optional<ActiveDisaster> activate(EffectContext context, List<MapPoint> points) {
        int interval = Math.max(MIN_INTERVAL_SECONDS, context.definition().options()
                .getInt("interval-seconds", DEFAULT_INTERVAL_SECONDS));
        return Optional.of(new Melting(interval));
    }

    private static final class Melting implements ActiveDisaster {

        private final int intervalSeconds;
        private int nextAtSecond = -1;

        private Melting(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        @Override
        public boolean tick(EffectContext context, int elapsedSeconds) {
            if (nextAtSecond < 0) {
                // 掷中的当秒就先来一次，不然还要白等一个间隔
                nextAtSecond = elapsedSeconds;
            }
            if (elapsedSeconds < nextAtSecond) {
                return true;
            }
            nextAtSecond = elapsedSeconds + intervalSeconds;
            for (Player player : context.players()) {
                melt(context, player);
            }
            return true;
        }

        private void melt(EffectContext context, Player player) {
            Location location = player.getLocation();
            if (!context.world().equals(location.getWorld())) {
                return;
            }
            int x = location.getBlockX();
            int z = location.getBlockZ();
            int surface = context.terrain().groundY(x, z);
            if (surface == SpawnTerrain.NO_GROUND
                    || location.getBlockY() - surface > REACH) {
                return;
            }
            Block block = context.world().getBlockAt(x, surface, z);
            if (DigRule.diggable(block)) {
                context.blocks().set(block, Material.LAVA, false, ID);
            }
        }
    }
}
