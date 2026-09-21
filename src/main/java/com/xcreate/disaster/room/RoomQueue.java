package com.xcreate.disaster.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 等空位的队列。
 *
 * <p>先到先得。同一个玩家只会排一次，重复请求不会把名次往后挤。</p>
 */
public final class RoomQueue {

    private final Deque<UUID> waiting = new ArrayDeque<>();
    private final Set<UUID> queued = new HashSet<>();
    private final int limit;

    /** limit 为 0 或负数表示不限长。 */
    public RoomQueue(int limit) {
        this.limit = limit;
    }

    /** 已在队列里时返回 true 但不重复入队。队列满时返回 false。 */
    public boolean enqueue(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (queued.contains(playerId)) {
            return true;
        }
        if (limit > 0 && waiting.size() >= limit) {
            return false;
        }
        waiting.addLast(playerId);
        queued.add(playerId);
        return true;
    }

    public boolean remove(UUID playerId) {
        if (playerId == null || !queued.remove(playerId)) {
            return false;
        }
        waiting.remove(playerId);
        return true;
    }

    /** 取队首，取不到返回空。 */
    public Optional<UUID> poll() {
        UUID next = waiting.pollFirst();
        if (next != null) {
            queued.remove(next);
        }
        return Optional.ofNullable(next);
    }

    /** 看一眼队首但不取出。队列要等房间真正腾出来才推进。 */
    public Optional<UUID> peek() {
        return Optional.ofNullable(waiting.peekFirst());
    }

    public boolean contains(UUID playerId) {
        return playerId != null && queued.contains(playerId);
    }

    /** 名次从 1 开始，不在队列里返回 0。 */
    public int positionOf(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        int position = 1;
        for (UUID id : waiting) {
            if (id.equals(playerId)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    public int size() {
        return waiting.size();
    }

    public boolean isEmpty() {
        return waiting.isEmpty();
    }

    public void clear() {
        waiting.clear();
        queued.clear();
    }

    /** 按排队顺序快照。 */
    public List<UUID> snapshot() {
        return new ArrayList<>(waiting);
    }
}
