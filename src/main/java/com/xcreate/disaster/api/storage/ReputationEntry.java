package com.xcreate.disaster.api.storage;

import java.util.List;
import java.util.UUID;

/**
 * 同局玩家之间的标签式点赞。
 *
 * <p>只有正向标签、没有自由文本——自由文本得另做内容审核，这个代价不值。</p>
 */
public record ReputationEntry(
        String matchId,
        UUID fromId,
        UUID toId,
        List<String> tags,
        long createdAtMillis) {

    public ReputationEntry {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
