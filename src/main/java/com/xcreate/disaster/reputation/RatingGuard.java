package com.xcreate.disaster.reputation;

import com.xcreate.disaster.api.reputation.RatingVerdict;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 六道防刷闸的判定规则，一道闸一个纯函数。
 *
 * <p>只管判，不取数——需要的事实由调用方取好传进来。这样六道闸都能脱离服务端与存储单测，
 * 而取数那部分（查冷却时间戳、数本局赞过几个人）本身没什么分支值得测。</p>
 *
 * <p>属于游戏语义的两道（时间窗、参与者）在服务层判，落库相关的几道在实现层判。</p>
 */
public final class RatingGuard {

    private RatingGuard() {
    }

    /** 参与者校验与时间窗一起看。窗口一过，本局不再受理任何评价。 */
    public static RatingVerdict checkWindow(boolean windowOpen, boolean participant) {
        if (!windowOpen) {
            return RatingVerdict.WINDOW_CLOSED;
        }
        return checkParticipant(participant);
    }

    /** 不在本局的人不能评。 */
    public static RatingVerdict checkParticipant(boolean participant) {
        return participant ? RatingVerdict.ACCEPTED : RatingVerdict.NOT_PARTICIPANT;
    }

    /** 星级必须在 1–5 之间。 */
    public static RatingVerdict checkStars(int stars) {
        return stars < 1 || stars > 5 ? RatingVerdict.INVALID : RatingVerdict.ACCEPTED;
    }

    /**
     * 标签必须逐个来自固定选项。
     *
     * <p>这一关不能省——放任何自填字符串进来，等于又开了一条自由文本通道，
     * 防辱骂那部分就白做了。</p>
     */
    public static RatingVerdict checkTags(Collection<String> tags, Set<String> allowed) {
        if (tags != null && !allowed.containsAll(tags)) {
            return RatingVerdict.INVALID;
        }
        return RatingVerdict.ACCEPTED;
    }

    /** 不能给自己点赞、不能重复赞同一人、和同一人的冷却还没过。 */
    public static RatingVerdict checkTarget(boolean self, boolean alreadyRecommended,
                                            long cooldownRemainingMillis) {
        if (self) {
            return RatingVerdict.SELF;
        }
        if (alreadyRecommended) {
            return RatingVerdict.ALREADY_RECOMMENDED;
        }
        return cooldownRemainingMillis > 0 ? RatingVerdict.COOLDOWN : RatingVerdict.ACCEPTED;
    }

    /** 每人每局点赞人数上限，0 表示不限。 */
    public static RatingVerdict checkQuota(int used, int limit) {
        return limit > 0 && used >= limit ? RatingVerdict.LIMIT_REACHED : RatingVerdict.ACCEPTED;
    }

    /** 去重并保住原有顺序，用于收敛玩家一次传进来的标签。 */
    public static List<String> distinct(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().distinct().toList();
    }
}
