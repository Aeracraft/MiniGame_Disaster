package com.xcreate.disaster.api.storage;

import java.util.List;

/**
 * 一局的完整记录。
 *
 * @param matchId      全局唯一，同时充当上报幂等键
 * @param serverId     跑这一局的子服标识，跨服时用于区分来源
 * @param disasterIds  本局出现过的灾难标识，供服主按灾难聚合分析
 */
public record MatchRecord(
        String matchId,
        String mapId,
        String roomId,
        String serverId,
        long startedAtMillis,
        long endedAtMillis,
        int durationSeconds,
        List<String> disasterIds,
        List<MatchParticipant> participants) {

    public MatchRecord {
        disasterIds = disasterIds == null ? List.of() : List.copyOf(disasterIds);
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    /** 可能是空列表——存在没人活下来的对局。 */
    public List<MatchParticipant> survivors() {
        return participants.stream().filter(MatchParticipant::survived).toList();
    }
}
