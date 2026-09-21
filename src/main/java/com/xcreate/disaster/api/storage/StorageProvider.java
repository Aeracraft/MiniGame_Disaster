package com.xcreate.disaster.api.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 数据存储后端。
 *
 * <p>一期有两个实现：本地 YAML 与 MySQL。MySQL 用于跨服共享，连不上时必须降级到 YAML，
 * 而不是让服务器起不来。</p>
 *
 * <p>所有方法都异步——实现里既有磁盘 I/O 也有 JDBC，都不允许出现在主线程 tick 路径上。</p>
 */
public interface StorageProvider extends AutoCloseable {

    /** 实现标识，{@code yaml} / {@code mysql}。 */
    String id();

    /** 初始化成功后是否可用。 */
    boolean available();

    /**
     * 建目录或建表并验证连通性。
     *
     * <p>失败直接抛异常，由上层决定降级还是禁用。安静地假成功比报错难查得多。</p>
     */
    void init() throws Exception;

    @Override
    void close();

    CompletableFuture<PlayerStats> loadStats(UUID playerId);

    /** 累加到现有统计上，没有记录则新建一条。 */
    CompletableFuture<Void> applyDelta(UUID playerId, String playerName, PlayerDelta delta);

    CompletableFuture<List<PlayerStats>> topPlayers(StatField field, int limit);

    CompletableFuture<Void> saveMatch(MatchRecord record);

    CompletableFuture<Optional<MatchRecord>> findMatch(String matchId);

    /** 按开始时间倒序返回该玩家参与过的对局。 */
    CompletableFuture<List<MatchRecord>> recentMatches(UUID playerId, int limit);

    /** 返回 false 表示这局这个人已经评过，本次提交被拒。 */
    CompletableFuture<Boolean> saveRating(MatchRating rating);

    CompletableFuture<List<MatchRating>> ratingsOf(String matchId);

    CompletableFuture<Optional<MatchRating>> ownRating(String matchId, UUID raterId);

    /**
     * 记录一次点赞。返回 false 表示这一局里同一对玩家已经点过了。
     *
     * <p>冷却天数之类的策略不在这里判定，上层拿 {@link #lastReputationAt} 自己决定。</p>
     */
    CompletableFuture<Boolean> saveReputation(ReputationEntry entry);

    CompletableFuture<Long> reputationOf(UUID playerId);

    CompletableFuture<Optional<Long>> lastReputationAt(UUID fromId, UUID toId);

    CompletableFuture<List<AchievementEntry>> loadAchievements(UUID playerId);

    CompletableFuture<Void> saveAchievement(AchievementEntry entry);
}
