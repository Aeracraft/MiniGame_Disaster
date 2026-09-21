package com.xcreate.disaster.room;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomQueueTest {

    @Test
    void 先到先出() {
        RoomQueue queue = new RoomQueue(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        queue.enqueue(first);
        queue.enqueue(second);

        assertEquals(Optional.of(first), queue.poll());
        assertEquals(Optional.of(second), queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void 重复入队不会把名次往后挤() {
        RoomQueue queue = new RoomQueue(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.enqueue(first);
        queue.enqueue(second);

        assertTrue(queue.enqueue(first));

        assertEquals(1, queue.positionOf(first));
        assertEquals(2, queue.positionOf(second));
        assertEquals(2, queue.size());
    }

    @Test
    void 名次从一数起且不在队列里为零() {
        RoomQueue queue = new RoomQueue(0);
        UUID player = UUID.randomUUID();
        queue.enqueue(player);
        queue.enqueue(UUID.randomUUID());

        assertEquals(1, queue.positionOf(player));
        assertEquals(0, queue.positionOf(UUID.randomUUID()));
    }

    @Test
    void 中途退出的人不再占位() {
        RoomQueue queue = new RoomQueue(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.enqueue(first);
        queue.enqueue(second);

        assertTrue(queue.remove(first));

        assertEquals(1, queue.size());
        assertEquals(1, queue.positionOf(second));
        assertEquals(Optional.of(second), queue.poll());
    }

    @Test
    void 移除不在队列里的人返回假() {
        RoomQueue queue = new RoomQueue(0);
        assertFalse(queue.remove(UUID.randomUUID()));
        assertFalse(queue.remove(null));
    }

    @Test
    void 队列满了就不再收人() {
        RoomQueue queue = new RoomQueue(2);
        assertTrue(queue.enqueue(UUID.randomUUID()));
        assertTrue(queue.enqueue(UUID.randomUUID()));
        assertFalse(queue.enqueue(UUID.randomUUID()));
        assertEquals(2, queue.size());
    }

    @Test
    void 上限为零表示不限() {
        RoomQueue queue = new RoomQueue(0);
        for (int i = 0; i < 50; i++) {
            assertTrue(queue.enqueue(UUID.randomUUID()));
        }
        assertEquals(50, queue.size());
    }

    @Test
    void 出队之后再排要到队尾() {
        RoomQueue queue = new RoomQueue(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.enqueue(first);
        queue.enqueue(second);

        queue.poll();
        queue.enqueue(first);

        assertEquals(2, queue.positionOf(first));
    }

    @Test
    void 看一眼队首不会把人取走() {
        RoomQueue queue = new RoomQueue(0);
        UUID player = UUID.randomUUID();
        queue.enqueue(player);

        assertEquals(Optional.of(player), queue.peek());
        assertEquals(1, queue.size());
        assertTrue(queue.contains(player));
    }

    @Test
    void 快照按排队顺序() {
        RoomQueue queue = new RoomQueue(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.enqueue(first);
        queue.enqueue(second);

        assertEquals(2, queue.snapshot().size());
        assertEquals(first, queue.snapshot().get(0));
    }

    @Test
    void 清空后连占用表一起清掉() {
        RoomQueue queue = new RoomQueue(0);
        UUID player = UUID.randomUUID();
        queue.enqueue(player);

        queue.clear();

        assertTrue(queue.isEmpty());
        assertFalse(queue.contains(player));
        // 清空后重新入队应该还能进，说明占用表确实清干净了
        assertTrue(queue.enqueue(player));
        assertEquals(1, queue.positionOf(player));
    }

    @Test
    void 空入参不炸() {
        RoomQueue queue = new RoomQueue(0);
        assertFalse(queue.enqueue(null));
        assertTrue(queue.poll().isEmpty());
        assertTrue(queue.peek().isEmpty());
    }
}
