package com.xcreate.disaster.room;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomNamingTest {

    @Test
    void 世界名按固定格式拼() {
        assertEquals("ds_city_1", RoomNaming.worldName("city", 1));
        assertEquals("ds_my_city_12", RoomNaming.worldName("my_city", 12));
    }

    @Test
    void 跳过已被占用的编号() {
        assertEquals("ds_city_3",
                RoomNaming.nextWorldName("city", Set.of("ds_city_1", "ds_city_2")));
    }

    @Test
    void 占用判断不区分大小写() {
        // Windows 上目录名不区分大小写，判重也得跟上，否则会撞到已存在的世界目录
        assertEquals("ds_city_2", RoomNaming.nextWorldName("city", Set.of("DS_City_1")));
    }

    @Test
    void 没有占用时从一号开始() {
        assertEquals("ds_city_1", RoomNaming.nextWorldName("city", Set.of()));
        assertEquals("ds_city_1", RoomNaming.nextWorldName("city", null));
    }

    @Test
    void 别的世界名不干扰编号() {
        assertEquals("ds_city_1",
                RoomNaming.nextWorldName("city", Set.of("world", "ds_other_1", "city")));
    }

    @Test
    void 反解地图id时保住自带的下划线() {
        assertEquals("city", RoomNaming.mapIdOf("ds_city_2"));
        assertEquals("my_city", RoomNaming.mapIdOf("ds_my_city_7"));
    }

    @Test
    void 反解认不出的名字返回空() {
        assertNull(RoomNaming.mapIdOf("world"));
        assertNull(RoomNaming.mapIdOf("ds_city"));
        assertNull(RoomNaming.mapIdOf("ds_city_a"));
        assertNull(RoomNaming.mapIdOf("ds__1"));
        assertNull(RoomNaming.mapIdOf(null));
    }

    @Test
    void 识别副本世界() {
        assertTrue(RoomNaming.isRoomWorld("ds_city_1"));
        assertTrue(RoomNaming.isRoomWorld("DS_CITY_1"));
        assertFalse(RoomNaming.isRoomWorld("world"));
        assertFalse(RoomNaming.isRoomWorld(null));
    }
}
