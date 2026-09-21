package com.xcreate.disaster.disaster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 地陷坑形的回归：中心最深、坑沿收平、半径外不动。 */
class SinkholeShapeTest {

    @Test
    void 坑心最深() {
        assertEquals(4, SinkholeShape.depthAt(0, 0, 8, 4));
    }

    @Test
    void 坑沿处不挖() {
        assertEquals(0, SinkholeShape.depthAt(8, 0, 8, 4));
        assertEquals(0, SinkholeShape.depthAt(0, -8, 8, 4));
    }

    @Test
    void 半径外不挖() {
        assertEquals(0, SinkholeShape.depthAt(9, 0, 8, 4));
        assertEquals(0, SinkholeShape.depthAt(6, 6, 8, 4));
    }

    @Test
    void 由内向外越来越浅() {
        int previous = Integer.MAX_VALUE;
        for (int d = 0; d <= 8; d++) {
            int depth = SinkholeShape.depthAt(d, 0, 8, 4);
            assertTrue(depth <= previous, "距离 " + d + " 处比内侧更深：" + depth + " > " + previous);
            previous = depth;
        }
    }

    @Test
    void 半径或深度不合法时什么都不挖() {
        assertEquals(0, SinkholeShape.depthAt(0, 0, 0, 4));
        assertEquals(0, SinkholeShape.depthAt(0, 0, -3, 4));
        assertEquals(0, SinkholeShape.depthAt(0, 0, 8, 0));
        assertEquals(0, SinkholeShape.depthAt(0, 0, 8, -1));
    }

    @Test
    void 影响范围是个圆不是方() {
        assertTrue(SinkholeShape.inside(8, 0, 8));
        assertTrue(SinkholeShape.inside(5, 5, 8));
        assertFalse(SinkholeShape.inside(6, 6, 8));
        assertFalse(SinkholeShape.inside(0, 0, 0));
    }
}
