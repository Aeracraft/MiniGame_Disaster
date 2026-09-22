package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * 洪水：水位一格一格往上漫。
 *
 * <p>铺水按游标一段一段扫，扫完一圈就抬一格；水位本身只按时间算（见 {@link FloodLevel}），
 * 所以地图大只是漫得慢，不会把水位卡住。</p>
 *
 * <p>写水用 {@code applyPhysics=false}。让水自己流会连锁更新，一格水能滚出几百格变更，
 * 而且流到哪儿就不由插件说了算——漫过整张图的水墙应该是「画」出来的，不是「流」出来的。</p>
 */
public final class FloodEffect implements DisasterEffect {

    public static final String ID = "flood";

    private static final int DEFAULT_RISE_SECONDS = 6;
    private static final int DEFAULT_COLUMNS_PER_STEP = 8000;
    private static final int MIN_COLUMNS_PER_STEP = 200;

    /** 开局水位比最低的玩家再低一点。第一波就把人闷死不算灾难，算处决。 */
    private static final int START_MARGIN = 1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        // 首层水交给活动实例铺，落下来这一下什么都不做
        return 0;
    }

    @Override
    public Optional<ActiveDisaster> activate(EffectContext context, List<MapPoint> points) {
        if (!context.hasBounds()) {
            return Optional.empty();
        }
        int riseSeconds = Math.max(1, context.definition().options()
                .getInt("rise-seconds", DEFAULT_RISE_SECONDS));
        int columnsPerStep = Math.max(MIN_COLUMNS_PER_STEP, context.definition().options()
                .getInt("columns-per-step", DEFAULT_COLUMNS_PER_STEP));
        return Optional.of(new Rising(context.bounds(), lowestLevel(context),
                riseSeconds, columnsPerStep));
    }

    /** 起涨水位取最低那位玩家脚下再低一格，没人时从地图下沿起。 */
    private static int lowestLevel(EffectContext context) {
        World world = context.world();
        int floor = world.getMinHeight() + 1;
        int lowest = Integer.MAX_VALUE;
        for (Player player : context.players()) {
            if (context.world().equals(player.getWorld())) {
                lowest = Math.min(lowest, player.getLocation().getBlockY());
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            return Math.max(floor, context.bounds().minY());
        }
        return Math.max(floor, lowest - START_MARGIN);
    }

    private static final class Rising implements ActiveDisaster {

        private final MapBounds bounds;
        private final int startLevel;
        private final int riseSeconds;
        private final int columnsPerStep;
        private final int ceiling;

        private int level;
        private int x;
        private int z;

        private Rising(MapBounds bounds, int startLevel, int riseSeconds, int columnsPerStep) {
            this.bounds = bounds;
            this.startLevel = startLevel;
            this.riseSeconds = riseSeconds;
            this.columnsPerStep = columnsPerStep;
            this.level = startLevel;
            this.ceiling = Math.min(bounds.maxY(), 320);
            this.x = bounds.minX();
            this.z = bounds.minZ();
        }

        @Override
        public boolean tick(EffectContext context, int elapsedSeconds) {
            int target = FloodLevel.at(startLevel, riseSeconds, elapsedSeconds);
            if (target > ceiling) {
                return false;
            }
            if (target != level) {
                level = target;
                x = bounds.minX();
                z = bounds.minZ();
            }
            sweep(context);
            return true;
        }

        private void sweep(EffectContext context) {
            World world = context.world();
            for (int i = 0; i < columnsPerStep; i++) {
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    int surface = context.terrain().groundY(x, z);
                    if (surface != SpawnTerrain.NO_GROUND && surface < level) {
                        Block block = world.getBlockAt(x, level, z);
                        if (block.getType() == Material.AIR) {
                            context.blocks().set(block, Material.WATER, false, ID);
                        }
                    }
                }
                if (++z > bounds.maxZ()) {
                    z = bounds.minZ();
                    if (++x > bounds.maxX()) {
                        x = bounds.minX();
                    }
                }
            }
        }
    }
}
