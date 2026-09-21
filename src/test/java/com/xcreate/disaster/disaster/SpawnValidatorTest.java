package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 落点校验链的回归。这里只验规则本身，地形用一张写死的表。 */
class SpawnValidatorTest {

    private static final MapBounds BOUNDS = new MapBounds(-50, 0, -50, 50, 60, 50);

    private static final SpawnContext CONTEXT = new SpawnContext("ds_test_1", BOUNDS,
            List.of(), List.of(point(0, 10, 0)), Map.of());

    private static final TestTerrain TERRAIN = new TestTerrain(10);

    @Test
    void 边界内的落点通过() {
        assertTrue(check(25, 25, List.of(), true).isEmpty());
    }

    @Test
    void 越界被拒() {
        assertEquals(SpawnRejection.OUT_OF_BOUNDS, check(51, 25, List.of(), true).orElseThrow());
        assertEquals(SpawnRejection.OUT_OF_BOUNDS, check(-51, 25, List.of(), true).orElseThrow());
    }

    @Test
    void 这一列没有地面时被拒() {
        TestTerrain terrain = new TestTerrain(10).breakColumn(25, 25);

        assertEquals(SpawnRejection.NO_GROUND,
                check(terrain, 25, 25, List.of(), true).orElseThrow());
    }

    @Test
    void 地面高过边界上沿时被拒() {
        TestTerrain terrain = new TestTerrain(10).raise(25, 25, 80);

        assertEquals(SpawnRejection.OUT_OF_BOUNDS,
                check(terrain, 25, 25, List.of(), true).orElseThrow());
    }

    @Test
    void 离出生点太近被拒() {
        assertEquals(SpawnRejection.TOO_CLOSE_TO_SPAWN,
                check(3, 3, List.of(), true).orElseThrow());
    }

    @Test
    void 与已确定的落点太挤被拒() {
        List<MapPoint> accepted = List.of(point(25, 10, 25));

        assertEquals(SpawnRejection.TOO_CLOSE_TO_POINT,
                check(26, 25, accepted, true).orElseThrow());
    }

    @Test
    void 关掉间距校验后挨着也没事() {
        List<MapPoint> accepted = List.of(point(25, 10, 25));

        assertTrue(check(26, 25, accepted, false).isEmpty(),
                "服主手标的固定落点不该被间距规则挡住");
    }

    @Test
    void 没有边界时整条链都过不去() {
        SpawnContext unbound = new SpawnContext("ds_test_1", null, List.of(), List.of(), Map.of());

        assertEquals(SpawnRejection.NO_BOUNDS,
                new SpawnValidator(config()).check(unbound, TERRAIN, 25, 25, List.of(), true)
                        .orElseThrow());
    }

    private static Optional<SpawnRejection> check(int x, int z, List<MapPoint> accepted,
                                                 boolean enforceSpacing) {
        return check(TERRAIN, x, z, accepted, enforceSpacing);
    }

    private static Optional<SpawnRejection> check(TestTerrain terrain, int x, int z,
                                                 List<MapPoint> accepted,
                                                 boolean enforceSpacing) {
        return new SpawnValidator(config()).check(CONTEXT, terrain, x, z, accepted, enforceSpacing);
    }

    private static MapPoint point(double x, double y, double z) {
        return new MapPoint("", "", x, y, z, 0f, 0f);
    }

    private static PluginConfig.Disasters config() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString("disaster:\n"
                    + "  spawn-validation:\n"
                    + "    min-distance-from-spawn: 8\n"
                    + "    min-distance-between-points: 3\n"
                    + "    max-retries: 20\n");
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return PluginConfig.parse(configuration).disasters();
    }
}
