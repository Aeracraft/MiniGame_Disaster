package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;

import java.util.List;

/**
 * 混战：开 PvP，并且存活人数掉到开局的一半就结束。
 *
 * <p>不动地形，改的是规则开关。开局时 PvP 一律是关的，所以「开打」这个动作本身
 * 就是这个灾种的全部效果。</p>
 *
 * <p>不返回活动实例：规则改完就一直在。也不必在结束时还原——房间一局一销毁，
 * 世界本来就是拷出来的副本。</p>
 */
public final class PurgeEffect implements DisasterEffect {

    public static final String ID = "purge";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int apply(EffectContext context, List<MapPoint> points) {
        context.rules().pvp(true);
        context.rules().halfSurvivorsEnd(true);
        context.world().setPVP(true);
        return 0;
    }
}
