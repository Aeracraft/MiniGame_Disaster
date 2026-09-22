package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 全图撒列的回归：边界内、不重复、数量受边界限制、固定种子可复现。 */
class ColumnSamplerTest {

    private static final long SEED = 20260921L;

    private static MapBounds bounds() {
        return new MapBounds(-40, 0, 60, 40, 120, 140);
    }

    @Test
    void 取到的列都在边界内() {
        List<ColumnSampler.Column> columns = ColumnSampler.pick(bounds(), new Random(SEED), 200);
        assertEquals(200, columns.size());
        for (ColumnSampler.Column column : columns) {
            assertTrue(column.x() >= -40 && column.x() <= 40, "x 越界: " + column.x());
            assertTrue(column.z() >= 60 && column.z() <= 140, "z 越界: " + column.z());
        }
    }

    @Test
    void 同一列不会取两次() {
        List<ColumnSampler.Column> columns = ColumnSampler.pick(bounds(), new Random(SEED), 2000);
        Set<String> seen = new HashSet<>();
        for (ColumnSampler.Column column : columns) {
            assertTrue(seen.add(column.x() + ":" + column.z()), "重复取列");
        }
    }

    @Test
    void 要的列数超过整张图时给出全部列() {
        MapBounds tiny = new MapBounds(0, 0, 0, 3, 0, 4);   // 4 x 5
        assertEquals(20, ColumnSampler.pick(tiny, new Random(SEED), 100).size());
    }

    @Test
    void 固定种子取到同一批列() {
        assertEquals(ColumnSampler.pick(bounds(), new Random(SEED), 50),
                ColumnSampler.pick(bounds(), new Random(SEED), 50));
    }

    @Test
    void 参数不成立时返回空表() {
        assertTrue(ColumnSampler.pick(null, new Random(SEED), 10).isEmpty());
        assertTrue(ColumnSampler.pick(bounds(), null, 10).isEmpty());
        assertTrue(ColumnSampler.pick(bounds(), new Random(SEED), 0).isEmpty());
    }
}
