package com.xcreate.disaster.api.storage;

import java.util.UUID;

/**
 * 单个成就的进度。
 *
 * @param progress 当前进度，unlocked 为真时通常等于目标值
 */
public record AchievementEntry(
        UUID playerId,
        String achievementId,
        int progress,
        boolean unlocked,
        long updatedAtMillis) {
}
