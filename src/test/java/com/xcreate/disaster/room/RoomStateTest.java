package com.xcreate.disaster.room;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomStateTest {

    @Test
    void 常规路径一路走得通() {
        assertTrue(RoomState.CREATING.canGoTo(RoomState.IDLE));
        assertTrue(RoomState.IDLE.canGoTo(RoomState.WAITING));
        assertTrue(RoomState.WAITING.canGoTo(RoomState.COUNTDOWN));
        assertTrue(RoomState.COUNTDOWN.canGoTo(RoomState.RUNNING));
        assertTrue(RoomState.RUNNING.canGoTo(RoomState.ENDING));
        assertTrue(RoomState.ENDING.canGoTo(RoomState.RESETTING));
        assertTrue(RoomState.RESETTING.canGoTo(RoomState.IDLE));
    }

    @Test
    void 等候中的人走光了退回空闲() {
        assertTrue(RoomState.WAITING.canGoTo(RoomState.IDLE));
    }

    @Test
    void 倒计时里人不够就退回等候() {
        assertTrue(RoomState.COUNTDOWN.canGoTo(RoomState.WAITING));
    }

    @Test
    void 任何非终态都能直接关闭() {
        for (RoomState state : RoomState.values()) {
            if (state == RoomState.CLOSED) {
                continue;
            }
            assertTrue(state.canGoTo(RoomState.CLOSED), state + " 应该允许转为已关闭");
        }
    }

    @Test
    void 不能跳级() {
        assertFalse(RoomState.CREATING.canGoTo(RoomState.RUNNING));
        assertFalse(RoomState.IDLE.canGoTo(RoomState.RUNNING));
        assertFalse(RoomState.RUNNING.canGoTo(RoomState.WAITING));
        assertFalse(RoomState.RESETTING.canGoTo(RoomState.RUNNING));
    }

    @Test
    void 已关闭是终态() {
        for (RoomState state : RoomState.values()) {
            assertFalse(RoomState.CLOSED.canGoTo(state));
        }
    }

    @Test
    void 只有空闲与等候接新玩家() {
        assertTrue(RoomState.IDLE.acceptsPlayers());
        assertTrue(RoomState.WAITING.acceptsPlayers());
        assertFalse(RoomState.CREATING.acceptsPlayers());
        assertFalse(RoomState.COUNTDOWN.acceptsPlayers());
        assertFalse(RoomState.RUNNING.acceptsPlayers());
        assertFalse(RoomState.CLOSED.acceptsPlayers());
    }

    @Test
    void 只有已关闭不占地图() {
        for (RoomState state : RoomState.values()) {
            if (state == RoomState.CLOSED) {
                assertFalse(state.holdsMap());
            } else {
                assertTrue(state.holdsMap(), state + " 应该算作占用地图");
            }
        }
    }

    @Test
    void 空参数不炸() {
        assertFalse(RoomState.IDLE.canGoTo(null));
    }
}
