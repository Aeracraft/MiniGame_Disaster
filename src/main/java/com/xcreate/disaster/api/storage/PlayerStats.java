package com.xcreate.disaster.api.storage;

import java.util.UUID;

/**
 * 一名玩家的累积统计。
 *
 * <p>这些数字全部由增量累加得到，所以只要明细还在，口径变了就能拿历史重算一遍。</p>
 */
public record PlayerStats(
        UUID playerId,
        String playerName,
        int matches,
        int wins,
        int deaths,
        long totalSurvivalSeconds,
        long bestSurvivalSeconds,
        long reputation,
        long updatedAtMillis) {

    public static PlayerStats empty(UUID playerId, String playerName) {
        return new PlayerStats(playerId, playerName, 0, 0, 0, 0L, 0L, 0L, 0L);
    }

    /** 无对局记录时返回 0，不做除零保护以外的任何特殊处理。 */
    public double winRate() {
        return matches == 0 ? 0.0 : (double) wins / matches;
    }
}
