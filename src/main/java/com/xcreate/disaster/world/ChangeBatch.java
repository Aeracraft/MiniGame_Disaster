package com.xcreate.disaster.world;

import com.xcreate.disaster.api.replay.BlockChange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 攒起来的一批方块变更，按来源分组。
 *
 * <p>灾难高峰期一秒能改上千个方块，逐条广播会把事件总线淹掉，所以按来源攒成批，
 * 每个 tick 每个来源各发一次事件。</p>
 *
 * <p>只负责攒与取，不碰世界，方便单测。</p>
 */
public final class ChangeBatch {

    /** 单个来源的批次上限。到了就先冲出去，免得一波灾难把内存顶起来。 */
    public static final int MAX_PER_CAUSE = 4096;

    private final Map<String, List<BlockChange>> pending = new LinkedHashMap<>();

    /** 记一条。返回 true 表示这个来源攒满了，调用方应当先冲一次。 */
    public boolean record(String cause, BlockChange change) {
        List<BlockChange> list = pending.computeIfAbsent(normalize(cause), key -> new ArrayList<>());
        list.add(change);
        return list.size() >= MAX_PER_CAUSE;
    }

    public int size() {
        int total = 0;
        for (List<BlockChange> list : pending.values()) {
            total += list.size();
        }
        return total;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public boolean has(String cause) {
        return pending.containsKey(normalize(cause));
    }

    /** 取走某个来源攒下的变更，该来源随之清空。 */
    public List<BlockChange> drain(String cause) {
        List<BlockChange> list = pending.remove(normalize(cause));
        return list == null ? List.of() : list;
    }

    /** 取走全部，按来源分组，顺序与记录顺序一致。取完即清空。 */
    public Map<String, List<BlockChange>> drainAll() {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<String, List<BlockChange>> taken = new LinkedHashMap<>(pending);
        pending.clear();
        return taken;
    }

    private static String normalize(String cause) {
        return cause == null || cause.isBlank() ? "unknown" : cause;
    }
}
