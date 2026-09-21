package com.xcreate.disaster.room;

import com.xcreate.disaster.map.MapDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTest {

    private static Room room() {
        return new Room("ds_city_1", MapDefinition.blank("city", "城市"));
    }

    @Test
    void 新建的房间在准备中且是空的() {
        Room room = room();
        assertTrue(room.is(RoomState.CREATING));
        assertTrue(room.isEmpty());
        assertEquals("ds_city_1", room.id());
        assertEquals("city", room.mapId());
        assertEquals(0, room.size());
    }

    @Test
    void 世界没加载时取世界不炸() {
        Room room = room();
        assertEquals(null, room.world());
        assertEquals(null, room.folder());
    }

    @Test
    void 加人与离开() {
        Room room = room();
        UUID player = UUID.randomUUID();

        assertTrue(room.join(player, 16));
        assertEquals(1, room.size());
        assertTrue(room.contains(player));

        assertTrue(room.leave(player));
        assertFalse(room.contains(player));
        assertTrue(room.isEmpty());
    }

    @Test
    void 准备期间也收人() {
        // 世界还在拷的时候就得先把人留下，等就绪再一起送进去
        Room room = room();
        assertTrue(room.join(UUID.randomUUID(), 16));
    }

    @Test
    void 同一个人不会占两个名额() {
        Room room = room();
        UUID player = UUID.randomUUID();
        assertTrue(room.join(player, 16));
        assertFalse(room.join(player, 16));
        assertEquals(1, room.size());
    }

    @Test
    void 超出上限就进不来() {
        Room room = room();
        assertTrue(room.join(UUID.randomUUID(), 2));
        assertTrue(room.join(UUID.randomUUID(), 2));
        assertFalse(room.join(UUID.randomUUID(), 2));
        assertEquals(2, room.size());
    }

    @Test
    void 上限为零表示不限() {
        Room room = room();
        for (int i = 0; i < 10; i++) {
            assertTrue(room.join(UUID.randomUUID(), 0));
        }
        assertEquals(10, room.size());
    }

    @Test
    void 有人在就不算空置() {
        Room room = room();
        UUID player = UUID.randomUUID();
        room.join(player, 16);
        assertEquals(0, room.emptyMillis());
    }

    @Test
    void 成员按加入顺序排() {
        Room room = room();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        room.join(first, 16);
        room.join(second, 16);
        assertEquals(List.of(first, second), room.players());
    }

    @Test
    void 成员快照不受后续改动影响() {
        Room room = room();
        UUID player = UUID.randomUUID();
        room.join(player, 16);

        List<UUID> snapshot = room.players();
        room.leave(player);

        assertEquals(1, snapshot.size());
        assertTrue(room.isEmpty());
    }

    @Test
    void 清场返回被移出的人() {
        Room room = room();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        room.join(first, 16);
        room.join(second, 16);

        List<UUID> evicted = room.evictAll();

        assertEquals(2, evicted.size());
        assertTrue(room.isEmpty());
    }

    @Test
    void 合法转换生效() {
        Room room = room();
        assertTrue(room.transition(RoomState.IDLE));
        assertTrue(room.is(RoomState.IDLE));
        assertTrue(room.transition(RoomState.WAITING));
        assertTrue(room.is(RoomState.WAITING));
    }

    @Test
    void 非法转换被拒绝且状态不动() {
        Room room = room();
        assertFalse(room.transition(RoomState.RUNNING));
        assertTrue(room.is(RoomState.CREATING));
    }

    @Test
    void 关闭之后接不了人() {
        Room room = room();
        room.transition(RoomState.IDLE);
        room.transition(RoomState.CLOSED);
        assertFalse(room.state().acceptsPlayers());
    }
}
