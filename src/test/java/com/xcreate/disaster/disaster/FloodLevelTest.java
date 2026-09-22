package com.xcreate.disaster.disaster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 洪水水位的回归：按时间涨、时间倒退不退水、间隔为零时不涨。 */
class FloodLevelTest {

    @Test
    void 每过间隔涨一格() {
        assertEquals(10, FloodLevel.at(10, 6, 0));
        assertEquals(10, FloodLevel.at(10, 6, 5));
        assertEquals(11, FloodLevel.at(10, 6, 6));
        assertEquals(12, FloodLevel.at(10, 6, 12));
    }

    @Test
    void 时间倒着走也不退水() {
        assertEquals(10, FloodLevel.at(10, 6, -30));
    }

    @Test
    void 间隔为零时不涨() {
        assertEquals(7, FloodLevel.at(7, 0, 600));
    }
}
