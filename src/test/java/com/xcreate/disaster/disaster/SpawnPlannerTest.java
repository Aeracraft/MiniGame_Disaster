package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 落点策略与重抽规则的回归。地形是写死的表，取点用固定种子的随机数。 */
class SpawnPlannerTest {

    private static final MapBounds BOUNDS = new MapBounds(-40, 0, -40, 40, 60, 40);

    /** 出生点放在角上，免得挡住其他用例的取点。 */
    private static final MapPoint SPAWN = point(35, 11, 35);

    private static final TestTerrain FLAT = new TestTerrain(10);

    private static final List<MapPoint> MIDDLE = List.of(point(0, 11, 0));

    @Test
    void 全图型灾难不产生落点() {
        SpawnOutcome outcome = plan(def("flood", SpawnStrategy.NONE, 0), context(MIDDLE), FLAT);

        assertEquals(0, outcome.requested());
        assertFalse(outcome.placed());
        assertFalse(outcome.abandoned(), "本来就不该落点，不算失败");
    }

    @Test
    void 没有边界时整波放弃() {
        SpawnContext unbound = new SpawnContext("ds_test_1", null, List.of(), List.of(), Map.of());

        SpawnOutcome outcome = plan(def("meteor", SpawnStrategy.RANDOM_IN_BOUNDS, 5), unbound, FLAT);

        assertTrue(outcome.abandoned());
        assertEquals(5, outcome.rejections().get(SpawnRejection.NO_BOUNDS),
                "整波被拒时按计划落点数记一笔");
    }

    @Test
    void 边界内随机的落点全部在框里() {
        SpawnOutcome outcome = plan(def("meteor", SpawnStrategy.RANDOM_IN_BOUNDS, 6),
                context(MIDDLE), FLAT);

        assertEquals(6, outcome.points().size());
        for (MapPoint landing : outcome.points()) {
            assertTrue(BOUNDS.contains(landing), "落到了边界外：" + landing.shortText());
            assertEquals(11.0, landing.y(), "落点应该停在地面上一格");
        }
    }

    @Test
    void 整片没有地面时一个都不落() {
        TestTerrain hollow = new TestTerrain(10).breakArea(-40, -40, 40, 40);

        SpawnOutcome outcome = plan(def("meteor", SpawnStrategy.RANDOM_IN_BOUNDS, 4),
                context(hollow, MIDDLE), hollow);

        assertTrue(outcome.abandoned());
        assertTrue(outcome.rejections().getOrDefault(SpawnRejection.NO_GROUND, 0) > 0);
    }

    @Test
    void 围着每个玩家取点按人头发放() {
        List<MapPoint> players = List.of(point(0, 11, 0), point(20, 11, 0), point(0, 11, 20));

        SpawnOutcome outcome = plan(def("zombie", SpawnStrategy.AROUND_EACH_PLAYER, 2),
                context(players), FLAT);

        assertEquals(6, outcome.requested(), "3 个人每人 2 个");
        assertEquals(6, outcome.points().size());
    }

    @Test
    void 需要玩家的灾种在没有玩家时不掷空() {
        SpawnOutcome outcome = plan(def("zombie", SpawnStrategy.AROUND_EACH_PLAYER, 2),
                context(List.of()), FLAT);

        assertTrue(outcome.abandoned());
        assertTrue(outcome.rejections().containsKey(SpawnRejection.NO_PLAYERS));
    }

    @Test
    void 高点策略落在抬高的那片地上() {
        TestTerrain plateau = new TestTerrain(10);
        for (int x = 1; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                plateau.raise(x, z, 40);
            }
        }

        SpawnOutcome outcome = plan(def("lightning", SpawnStrategy.HIGH_POINTS, 6),
                context(MIDDLE), plateau);

        assertEquals(6, outcome.points().size());
        for (MapPoint landing : outcome.points()) {
            assertEquals(41.0, landing.y(), "应该劈在抬高的那片地上：" + landing.shortText());
        }
    }

    @Test
    void 边缘策略贴着边界走() {
        SpawnOutcome outcome = plan(def("tornado", SpawnStrategy.FROM_EDGE, 8),
                context(MIDDLE), FLAT);

        assertEquals(8, outcome.points().size());
        for (MapPoint landing : outcome.points()) {
            int x = landing.blockX();
            int z = landing.blockZ();
            boolean onEdge = x <= BOUNDS.minX() + 8 || x >= BOUNDS.maxX() - 8
                    || z <= BOUNDS.minZ() + 8 || z >= BOUNDS.maxZ() - 8;
            assertTrue(onEdge, "落点不在边缘带上：" + landing.shortText());
        }
    }

    @Test
    void 中心策略围着地图中心散开() {
        SpawnOutcome outcome = plan(def("sinkhole", SpawnStrategy.AT_MAP_CENTER, 5),
                context(MIDDLE), FLAT);

        assertEquals(5, outcome.points().size());
        for (MapPoint landing : outcome.points()) {
            assertTrue(Math.abs(landing.x()) <= 9 && Math.abs(landing.z()) <= 9,
                    "离中心太远：" + landing.shortText());
        }
    }

    @Test
    void 地图标了固定落点就不再随机() {
        List<MapPoint> fixed = List.of(point(10, 12, 10), point(20, 14, 20));
        SpawnContext withAnchors = new SpawnContext("ds_test_1", BOUNDS,
                MIDDLE, List.of(SPAWN), Map.of("meteor", fixed));

        SpawnOutcome outcome = plan(def("meteor", SpawnStrategy.RANDOM_IN_BOUNDS, 6),
                withAnchors, FLAT);

        assertEquals(2, outcome.points().size(), "用固定落点，不按 point-count 生成");
        assertEquals(10.0, outcome.points().get(0).x());
        assertEquals(12.0, outcome.points().get(0).y(), "固定落点的高度由服主说了算");
        assertEquals("ds_test_1", outcome.points().get(0).world());
    }

    @Test
    void 点太挤时少落几个而不是硬塞() {
        MapBounds small = new MapBounds(-6, 0, -6, 6, 60, 6);
        SpawnContext crowded = new SpawnContext("ds_test_1", small, MIDDLE, List.of(), Map.of());

        SpawnOutcome outcome = plan(def("meteor", SpawnStrategy.RANDOM_IN_BOUNDS, 20),
                crowded, FLAT, "    min-distance-between-points: 6\n");

        assertTrue(outcome.placed(), "再挤也该落下一部分");
        assertTrue(outcome.partial(), "20 个点在 13x13 里塞不下");
        assertTrue(outcome.rejections().getOrDefault(SpawnRejection.TOO_CLOSE_TO_POINT, 0) > 0);
    }

    @Test
    void 离出生点太近的点会被剔掉() {
        SpawnContext nearSpawn = new SpawnContext("ds_test_1", BOUNDS,
                MIDDLE, List.of(point(0, 11, 0)), Map.of());

        SpawnOutcome outcome = plan(def("sinkhole", SpawnStrategy.AT_MAP_CENTER, 3),
                nearSpawn, FLAT);

        assertTrue(outcome.rejections().getOrDefault(SpawnRejection.TOO_CLOSE_TO_SPAWN, 0) > 0);
        for (MapPoint landing : outcome.points()) {
            assertTrue(landing.horizontalDistanceTo(0, 0) >= 8);
        }
    }

    private static SpawnOutcome plan(DisasterDefinition definition, SpawnContext context,
                                     SpawnTerrain terrain) {
        return plan(definition, context, terrain, "");
    }

    private static SpawnOutcome plan(DisasterDefinition definition, SpawnContext context,
                                     SpawnTerrain terrain, String extraConfig) {
        // 固定种子，失败时能拿同一串随机数重演
        return new SpawnPlanner(config(extraConfig), new Random(20260921L))
                .plan(definition, context, terrain);
    }

    private static SpawnContext context(List<MapPoint> players) {
        return new SpawnContext("ds_test_1", BOUNDS, players, List.of(SPAWN), Map.of());
    }

    private static SpawnContext context(SpawnTerrain ignored, List<MapPoint> players) {
        return context(players);
    }

    private static DisasterDefinition def(String id, SpawnStrategy strategy, int count) {
        return new DisasterDefinition(id, id, DisasterTier.PRIMARY, true, 1.0,
                strategy, count, DisasterOptions.empty());
    }

    private static MapPoint point(double x, double y, double z) {
        return new MapPoint("", "", x, y, z, 0f, 0f);
    }

    private static PluginConfig config(String extra) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString("disaster:\n"
                    + "  spawn-validation:\n"
                    + "    min-distance-from-spawn: 8\n"
                    + "    min-distance-between-points: 3\n"
                    + "    max-retries: 20\n"
                    + extra);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return PluginConfig.parse(configuration);
    }
}
