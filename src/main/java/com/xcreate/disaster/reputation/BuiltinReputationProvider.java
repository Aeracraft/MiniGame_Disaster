package com.xcreate.disaster.reputation;

import com.xcreate.disaster.api.reputation.RatingVerdict;
import com.xcreate.disaster.api.reputation.ReputationProvider;
import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.ReputationEntry;
import com.xcreate.disaster.api.storage.StorageProvider;
import com.xcreate.disaster.config.PluginConfig;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 走 {@code StorageProvider} 落库的内置实现。
 *
 * <p>「每人每局赞过几个人」的计数放在内存里，不落库：一局只在一台子服的一个房间里跑，
 * 同一局的请求都落在同一个进程内，内存计数就是准的。清理由窗口结束触发，重启丢掉也无妨
 * ——那几分钟的窗口早过了。</p>
 *
 * <p>一局一次、一局对一人一次的「去重」不靠内存，交给存储的唯一键：并发下「先查再写」必然漏。</p>
 */
public final class BuiltinReputationProvider implements ReputationProvider {

    public static final String ID = "builtin";

    private final StorageProvider storage;
    private PluginConfig.Rating config;

    /** 局标识 → 谁赞过哪些人。用集合而非计数，天然去重。 */
    private final Map<String, Map<UUID, Set<UUID>>> recommended = new ConcurrentHashMap<>();

    public BuiltinReputationProvider(StorageProvider storage, PluginConfig.Rating config) {
        this.storage = storage;
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    /** 重载配置后换一份，正在跑的窗口与已记的计数都留着。 */
    public void apply(PluginConfig.Rating config) {
        this.config = config;
    }

    @Override
    public boolean available() {
        return storage != null && storage.available();
    }

    @Override
    public CompletableFuture<RatingVerdict> submit(MatchRating rating) {
        if (!available()) {
            return CompletableFuture.completedFuture(RatingVerdict.STORAGE_UNAVAILABLE);
        }
        RatingVerdict verdict = RatingGuard.checkStars(rating.stars());
        if (!verdict.accepted()) {
            return CompletableFuture.completedFuture(verdict);
        }
        verdict = RatingGuard.checkTags(rating.issueTags(), config.tagSet());
        if (!verdict.accepted()) {
            return CompletableFuture.completedFuture(verdict);
        }
        return storage.saveRating(rating).thenApply(saved ->
                saved ? RatingVerdict.ACCEPTED : RatingVerdict.ALREADY_SUBMITTED);
    }

    @Override
    public CompletableFuture<RatingVerdict> recommend(ReputationEntry entry) {
        if (!available()) {
            return CompletableFuture.completedFuture(RatingVerdict.STORAGE_UNAVAILABLE);
        }
        RatingVerdict verdict = RatingGuard.checkTags(entry.tags(), config.repTagSet());
        if (!verdict.accepted()) {
            return CompletableFuture.completedFuture(verdict);
        }

        Set<UUID> targets = targetsOf(entry.matchId(), entry.fromId());
        verdict = RatingGuard.checkTarget(entry.fromId().equals(entry.toId()),
                targets.contains(entry.toId()), 0L);
        if (!verdict.accepted()) {
            return CompletableFuture.completedFuture(verdict);
        }
        verdict = RatingGuard.checkQuota(targets.size(), config.perMatchLimit());
        if (!verdict.accepted()) {
            return CompletableFuture.completedFuture(verdict);
        }

        long now = System.currentTimeMillis();
        return storage.lastReputationAt(entry.fromId(), entry.toId()).thenCompose(last -> {
            long remaining = last.map(at -> config.cooldownMillis() - (now - at)).orElse(0L);
            RatingVerdict cooled = RatingGuard.checkTarget(false, false, Math.max(0L, remaining));
            if (!cooled.accepted()) {
                return CompletableFuture.completedFuture(cooled);
            }
            return storage.saveReputation(entry).thenApply(saved -> {
                if (!saved) {
                    return RatingVerdict.ALREADY_RECOMMENDED;
                }
                targets.add(entry.toId());
                return RatingVerdict.ACCEPTED;
            });
        });
    }

    @Override
    public CompletableFuture<Long> reputationOf(UUID playerId) {
        return available()
                ? storage.reputationOf(playerId)
                : CompletableFuture.completedFuture(0L);
    }

    /** 一局的评价窗口结束后把计数丢掉，不然会一直堆在内存里。 */
    public void forget(String matchId) {
        recommended.remove(matchId);
    }

    /** 某人在本局已经赞过几个人。 */
    public int usedCount(String matchId, UUID fromId) {
        Map<UUID, Set<UUID>> byPlayer = recommended.get(matchId);
        if (byPlayer == null) {
            return 0;
        }
        Set<UUID> targets = byPlayer.get(fromId);
        return targets == null ? 0 : targets.size();
    }

    private Set<UUID> targetsOf(String matchId, UUID fromId) {
        return recommended
                .computeIfAbsent(matchId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(fromId, key -> ConcurrentHashMap.newKeySet());
    }
}
