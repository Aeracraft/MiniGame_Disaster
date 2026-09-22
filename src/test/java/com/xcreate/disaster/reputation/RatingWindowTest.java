package com.xcreate.disaster.reputation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 评价窗口的回归：开窗、过期、覆盖与参与者查找。 */
class RatingWindowTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String MATCH = "ds_city_1-abc";

    @Test
    void 开窗后参与者都能取到本局() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice", BOB, "Bob"), MATCH, 1_000L);

        assertEquals(MATCH, window.of(ALICE, 999L).orElseThrow().matchId());
        assertEquals(MATCH, window.of(BOB, 999L).orElseThrow().matchId());
        assertEquals(2, window.size());
    }

    @Test
    void 到点即失效() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice"), MATCH, 1_000L);

        assertTrue(window.of(ALICE, 999L).isPresent());
        assertTrue(window.of(ALICE, 1_000L).isEmpty(), "到点那一刻就不该再受理");
    }

    @Test
    void 没开过窗的玩家取不到() {
        RatingWindow window = new RatingWindow();

        assertTrue(window.of(ALICE, 1L).isEmpty());
    }

    @Test
    void 后一局覆盖前一局() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice"), MATCH, 1_000L);
        window.open(Map.of(ALICE, "Alice"), "ds_city_2-def", 2_000L);

        assertEquals("ds_city_2-def", window.of(ALICE, 1_500L).orElseThrow().matchId());
    }

    @Test
    void 按名字找参与者不区分大小写() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice"), MATCH, 1_000L);
        RatingWindow.Entry entry = window.of(ALICE, 1L).orElseThrow();

        assertEquals(ALICE, entry.idOf("alice"));
        assertEquals(ALICE, entry.idOf("ALICE"));
        assertNull(entry.idOf("Nobody"));
    }

    @Test
    void 非参与者不算在内() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice"), MATCH, 1_000L);

        RatingWindow.Entry entry = window.of(ALICE, 1L).orElseThrow();
        assertTrue(entry.contains(ALICE));
        assertFalse(entry.contains(BOB));
    }

    @Test
    void 清理只带走过期的() {
        RatingWindow window = new RatingWindow();
        window.open(Map.of(ALICE, "Alice"), MATCH, 1_000L);
        window.open(Map.of(BOB, "Bob"), "ds_lava_1-xyz", 5_000L);

        Set<String> expired = window.prune(2_000L);

        assertEquals(Set.of(MATCH), expired);
        assertEquals(1, window.size());
        assertTrue(window.of(ALICE, 2_000L).isEmpty());
        assertTrue(window.of(BOB, 2_000L).isPresent());
    }
}
