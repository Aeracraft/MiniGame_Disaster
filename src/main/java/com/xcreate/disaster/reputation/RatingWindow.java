package com.xcreate.disaster.reputation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 「刚打完的那一局」的登记处。
 *
 * <p>结算阶段只有十秒，玩家来不及评价，而评价命令又得知道评的是哪一局。这里在对局结束时
 * 为每位参与者登记一条「局标识 + 参与者名单 + 到期时刻」，命令据此定位 matchId，
 * 过期即失效——设计里说的「离开结算界面就作废」就是靠这个落地的。</p>
 *
 * <p>参与者名单直接存在登记项里，不回头查存储：结算写入是异步的，玩家可能比它先到；
 * 名单本身很短，为它多查一次库不划算。</p>
 *
 * <p>只在主线程访问。</p>
 */
public final class RatingWindow {

    /**
     * 一条登记。同一局的每位参与者共享同一个对象。
     *
     * @param participants 本局参与者，UUID 到名字
     * @param expiresAtMillis 到期时刻，过了就不再受理
     */
    public record Entry(String matchId, Map<UUID, String> participants,
                        long expiresAtMillis) {

        public Entry {
            participants = Map.copyOf(participants);
        }

        public boolean isOpen(long nowMillis) {
            return nowMillis < expiresAtMillis;
        }

        public boolean contains(UUID playerId) {
            return participants.containsKey(playerId);
        }

        /** 按名字找参与者，忽略大小写。找不到返回 null。 */
        public UUID idOf(String name) {
            if (name == null) {
                return null;
            }
            for (Map.Entry<UUID, String> participant : participants.entrySet()) {
                if (participant.getValue().equalsIgnoreCase(name)) {
                    return participant.getKey();
                }
            }
            return null;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** 给一局的所有参与者开窗。同一玩家重复开窗会被后一局覆盖。 */
    public void open(Map<UUID, String> participants, String matchId, long expiresAtMillis) {
        Entry entry = new Entry(matchId, participants, expiresAtMillis);
        for (UUID playerId : entry.participants().keySet()) {
            entries.put(playerId, entry);
        }
    }

    /** 取某位玩家当前还能评价的那一局。过期的不返回，顺手清掉。 */
    public Optional<Entry> of(UUID playerId, long nowMillis) {
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isOpen(nowMillis)) {
            entries.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void close(UUID playerId) {
        entries.remove(playerId);
    }

    /** 清掉已过期的登记，返回涉及的局标识，供上层顺带回收按局存的临时数据。 */
    public Set<String> prune(long nowMillis) {
        Set<String> expired = new HashSet<>();
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!entry.isOpen(nowMillis)) {
                expired.add(entry.matchId());
                iterator.remove();
            }
        }
        return expired;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
