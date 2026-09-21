package com.xcreate.disaster.world;

import com.xcreate.disaster.api.replay.BlockChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批次合并的回归：分组、取走即清空、上限提示。 */
class ChangeBatchTest {

    @Test
    void 按来源分组互不干扰() {
        ChangeBatch batch = new ChangeBatch();
        batch.record("sinkhole", change(1));
        batch.record("meteor_shower", change(2));
        batch.record("sinkhole", change(3));

        assertEquals(3, batch.size());
        assertEquals(List.of(change(1), change(3)), batch.drain("sinkhole"));
        assertEquals(List.of(change(2)), batch.drain("meteor_shower"));
        assertTrue(batch.isEmpty());
    }

    @Test
    void 取走之后同一来源是空的() {
        ChangeBatch batch = new ChangeBatch();
        batch.record("sinkhole", change(1));

        assertEquals(1, batch.drain("sinkhole").size());
        assertTrue(batch.drain("sinkhole").isEmpty());
        assertEquals(0, batch.size());
    }

    @Test
    void 空批次取全部得到空表() {
        ChangeBatch batch = new ChangeBatch();
        assertTrue(batch.drainAll().isEmpty());
        assertEquals(0, batch.size());
    }

    @Test
    void 取全部保留记录顺序() {
        ChangeBatch batch = new ChangeBatch();
        batch.record("a", change(1));
        batch.record("b", change(2));

        Map<String, List<BlockChange>> drained = batch.drainAll();
        assertEquals(List.of("a", "b"), List.copyOf(drained.keySet()));
        assertTrue(batch.isEmpty());
    }

    @Test
    void 来源为空时归到未知() {
        ChangeBatch batch = new ChangeBatch();
        batch.record(null, change(1));
        batch.record("  ", change(2));

        assertTrue(batch.has("unknown"));
        assertEquals(2, batch.drain("unknown").size());
    }

    @Test
    void 攒满一批会通知调用方冲一次() {
        ChangeBatch batch = new ChangeBatch();
        for (int i = 0; i < ChangeBatch.MAX_PER_CAUSE - 1; i++) {
            assertFalse(batch.record("sinkhole", change(i)), "不到上限不该提示");
        }
        assertTrue(batch.record("sinkhole", change(-1)), "到上限应当提示");
    }

    private static BlockChange change(int x) {
        return new BlockChange("ds_city_1", x, 64, 0, "STONE", "AIR");
    }
}
