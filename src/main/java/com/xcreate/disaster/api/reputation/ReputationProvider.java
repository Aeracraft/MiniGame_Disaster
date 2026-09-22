package com.xcreate.disaster.api.reputation;

import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.ReputationEntry;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 对局评价与玩家互评的可插拔契约。
 *
 * <p>Disaster 自带一份实现，走 {@code StorageProvider} 落库。第三方在 {@code ServicesManager}
 * 注册本接口即替换掉内置实现。有两道闸在实现方之上、由插件固定执行，不交给实现方——
 * 评价时间窗与参与者校验，它们属于游戏语义而不是存储策略。其余几道（内容合法性、重复提交、
 * 互评冷却、每局配额）由实现方自行决定。只想旁听而不接管的，监听
 * {@code DisasterMatchRatedEvent} 与 {@code DisasterPlayerRepEvent} 即可，那是另一条独立的插入点。</p>
 *
 * <p>依赖方向单向：接口在本插件侧，第三方 {@code compileOnly} 依赖本插件，反过来不成立。</p>
 *
 * <p>所有方法都可能被主线程调用，实现方自行异步化，别在里面做阻塞 I/O。</p>
 */
public interface ReputationProvider {

    /** 实现标识，例如 {@code builtin}。 */
    String id();

    /** 依赖缺失、存储不可用等情况下返回 false，调用方据此回落到内置实现。 */
    boolean available();

    /** 受理一条对局质量评价。重复提交由实现方按 {@code matchId + raterId} 挡掉。 */
    CompletableFuture<RatingVerdict> submit(MatchRating rating);

    /**
     * 受理一次玩家互赞。
     *
     * <p>冷却与每局上限这类策略由实现方决定——内置实现会查冷却时间戳并限制本局点赞人数。</p>
     */
    CompletableFuture<RatingVerdict> recommend(ReputationEntry entry);

    /** 某位玩家的累积荣誉值。 */
    CompletableFuture<Long> reputationOf(UUID playerId);
}
