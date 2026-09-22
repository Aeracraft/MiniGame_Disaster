package com.xcreate.disaster.reputation;

import com.xcreate.disaster.api.reputation.RatingVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 六道防刷闸的回归：每道单独过一遍，确认拒绝时能说清是哪一条拦下的。 */
class RatingGuardTest {

    private static final Set<String> TAGS = Set.of("卡顿", "太简单");

    @Test
    void 窗口关掉就不受理() {
        assertEquals(RatingVerdict.WINDOW_CLOSED, RatingGuard.checkWindow(false, true));
    }

    @Test
    void 不在本局的人不能评() {
        assertEquals(RatingVerdict.NOT_PARTICIPANT, RatingGuard.checkWindow(true, false));
        assertEquals(RatingVerdict.NOT_PARTICIPANT, RatingGuard.checkParticipant(false));
    }

    @Test
    void 窗口开着且是本局的人就放行() {
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkWindow(true, true));
    }

    @Test
    void 星级只能是一到五() {
        assertEquals(RatingVerdict.INVALID, RatingGuard.checkStars(0));
        assertEquals(RatingVerdict.INVALID, RatingGuard.checkStars(6));
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkStars(1));
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkStars(5));
    }

    @Test
    void 自填标签一律不合法() {
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkTags(List.of("卡顿"), TAGS));
        assertEquals(RatingVerdict.INVALID, RatingGuard.checkTags(List.of("你打得很烂"), TAGS));
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkTags(List.of(), TAGS));
    }

    @Test
    void 不能给自己点赞() {
        assertEquals(RatingVerdict.SELF, RatingGuard.checkTarget(true, false, 0L));
    }

    @Test
    void 同一局对同一人只能赞一次() {
        assertEquals(RatingVerdict.ALREADY_RECOMMENDED,
                RatingGuard.checkTarget(false, true, 0L));
    }

    @Test
    void 冷却没走完不放行() {
        assertEquals(RatingVerdict.COOLDOWN, RatingGuard.checkTarget(false, false, 1L));
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkTarget(false, false, 0L));
    }

    @Test
    void 每局点赞人数有上限() {
        assertEquals(RatingVerdict.LIMIT_REACHED, RatingGuard.checkQuota(3, 3));
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkQuota(2, 3));
    }

    @Test
    void 上限为零表示不限() {
        assertEquals(RatingVerdict.ACCEPTED, RatingGuard.checkQuota(999, 0));
    }

    @Test
    void 标签去重且保住顺序() {
        assertEquals(List.of("卡顿", "太简单"),
                RatingGuard.distinct(List.of("卡顿", "太简单", "卡顿")));
        assertEquals(List.of(), RatingGuard.distinct(null));
    }
}
