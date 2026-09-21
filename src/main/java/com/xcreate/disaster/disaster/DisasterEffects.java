package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 灾种效果的分发表。
 *
 * <p>还没登记效果的灾种静默跳过：掷骰与落点已经能跑，效果是一个一个补的，
 * 掷到尚未实现的灾种时不该报错刷屏。服主想知道哪些灾种「只有形没有实」，
 * 用 {@link #ids()} 比对灾种清单即可。</p>
 */
public final class DisasterEffects {

    private final Map<String, DisasterEffect> byId = new LinkedHashMap<>();

    /** 已经做好的效果。补一个新灾种就在这里登记一个。 */
    public static DisasterEffects builtin() {
        DisasterEffects effects = new DisasterEffects();
        effects.register(new SinkholeEffect());
        return effects;
    }

    public void register(DisasterEffect effect) {
        if (effect == null || effect.id() == null || effect.id().isBlank()) {
            return;
        }
        byId.put(effect.id().toLowerCase(Locale.ROOT), effect);
    }

    /** 已经实现的灾种，按登记顺序——输出到命令与日志时顺序才稳定。 */
    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }

    public boolean has(String disasterId) {
        return disasterId != null && byId.containsKey(disasterId.toLowerCase(Locale.ROOT));
    }

    public Optional<DisasterEffect> find(String disasterId) {
        return disasterId == null
                ? Optional.empty()
                : Optional.ofNullable(byId.get(disasterId.toLowerCase(Locale.ROOT)));
    }

    /** 生效。没登记过这个灾种就什么都不做，返回 0。 */
    public int apply(EffectContext context, List<MapPoint> points) {
        return find(context.definition().id())
                .map(effect -> effect.apply(context, points))
                .orElse(0);
    }
}
