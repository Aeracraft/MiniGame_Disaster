package com.xcreate.disaster.api.storage;

import java.util.List;
import java.util.UUID;

/**
 * 玩家对一局的质量评价。
 *
 * <p>一局一人只能评一次，重复提交由存储层的唯一约束挡掉；收到的永远是匿名数据，
 * raterId 只用于查重，不对外展示。</p>
 *
 * @param stars     1–5
 * @param issueTags 问题标签，取自固定选项，不收自由文本
 */
public record MatchRating(
        String matchId,
        UUID raterId,
        int stars,
        List<String> issueTags,
        long createdAtMillis) {

    public MatchRating {
        issueTags = issueTags == null ? List.of() : List.copyOf(issueTags);
    }
}
