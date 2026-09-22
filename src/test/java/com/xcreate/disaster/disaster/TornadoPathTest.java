package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 龙卷风路线的回归：进场点贴边、方向指图内、推进按步长、出图算跑完。 */
class TornadoPathTest {

    private static final long SEED = 20260921L;

    private static MapBounds bounds() {
        return new MapBounds(0, 0, 0, 99, 64, 99);
    }

    @Test
    void 进场点落在图边上() {
        Random random = new Random(SEED);
        for (int i = 0; i < 40; i++) {
            TornadoPath.Spot entry = TornadoPath.enter(bounds(), random);
            boolean onEdge = entry.x() <= 0.5 || entry.x() >= 99.5
                    || entry.z() <= 0.5 || entry.z() >= 99.5;
            assertTrue(onEdge, "进场点不在边上: " + entry);
        }
    }

    @Test
    void 方向指着图内() {
        TornadoPath.Spot entry = new TornadoPath.Spot(0.5, 50.0);
        TornadoPath.Spot direction = TornadoPath.direction(bounds(), entry);

        assertEquals(1.0, Math.hypot(direction.x(), direction.z()), 1.0e-6);
        assertTrue(direction.x() > 0, "图心在进场点的 +x 侧，方向该朝那边");
    }

    @Test
    void 落在图心时给兜底方向() {
        TornadoPath.Spot center = new TornadoPath.Spot(49.5, 49.5);
        TornadoPath.Spot direction = TornadoPath.direction(bounds(), center);
        assertEquals(1.0, Math.hypot(direction.x(), direction.z()), 1.0e-6);
    }

    @Test
    void 推进按步长走() {
        TornadoPath.Spot moved = new TornadoPath.Spot(0.0, 0.0)
                .moved(new TornadoPath.Spot(1.0, 0.0), 5.0);
        assertEquals(5.0, moved.x(), 1.0e-6);
        assertEquals(0.0, moved.z(), 1.0e-6);
    }

    @Test
    void 走出图外算跑完() {
        assertFalse(TornadoPath.done(bounds(), new TornadoPath.Spot(50, 50)));
        assertTrue(TornadoPath.done(bounds(), new TornadoPath.Spot(120, 50)));
    }
}
