package com.xcreate.disaster.storage;

import com.xcreate.disaster.api.storage.AchievementEntry;
import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.MatchRecord;
import com.xcreate.disaster.api.storage.PlayerDelta;
import com.xcreate.disaster.api.storage.PlayerStats;
import com.xcreate.disaster.api.storage.ReputationEntry;
import com.xcreate.disaster.api.storage.StatField;
import com.xcreate.disaster.api.storage.StorageProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 什么都不做的存储实现。
 *
 * <p>只在 MySQL 与 YAML 双双起不来时兜底，好让上层不用到处判空。查询一律返回空结果，
 * 写入直接丢弃——真走到这一步，启动时那条 SEVERE 日志已经是唯一的信号了。</p>
 */
final class NoopStorageProvider implements StorageProvider {

    private static final String ID = "none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void init() {
    }

    @Override
    public void close() {
    }

    @Override
    public CompletableFuture<PlayerStats> loadStats(UUID playerId) {
        return CompletableFuture.completedFuture(PlayerStats.empty(playerId, null));
    }

    @Override
    public CompletableFuture<Void> applyDelta(UUID playerId, String playerName, PlayerDelta delta) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<PlayerStats>> topPlayers(StatField field, int limit) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Void> saveMatch(MatchRecord record) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<MatchRecord>> findMatch(String matchId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<MatchRecord>> recentMatches(UUID playerId, int limit) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Boolean> saveRating(MatchRating rating) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<List<MatchRating>> ratingsOf(String matchId) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<MatchRating>> ownRating(String matchId, UUID raterId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> saveReputation(ReputationEntry entry) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Long> reputationOf(UUID playerId) {
        return CompletableFuture.completedFuture(0L);
    }

    @Override
    public CompletableFuture<Optional<Long>> lastReputationAt(UUID fromId, UUID toId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<AchievementEntry>> loadAchievements(UUID playerId) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Void> saveAchievement(AchievementEntry entry) {
        return CompletableFuture.completedFuture(null);
    }
}
