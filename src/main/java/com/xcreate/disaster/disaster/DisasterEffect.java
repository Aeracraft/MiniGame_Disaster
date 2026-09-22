package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;

import java.util.List;
import java.util.Optional;

/**
 * 一个灾难落到地上之后干什么。
 *
 * <p>与落点规划分开：落点只回答「砸在哪」，效果只回答「砸下来什么」。分开之后落点那套校验链
 * 与形状计算都能各自单测，加新灾种也不必回头动取点逻辑。</p>
 */
public interface DisasterEffect {

    /** 灾种标识，与 {@code disasters/<id>.yml} 的文件名一致。 */
    String id();

    /**
     * 生效。一次性灾种的全部行为都在这里，持续型灾种的首次生效也可以放这里。
     *
     * <p>落点为空表示这个灾种是全图型（酸雨、洪水），它们的作用域是整个地图而不是某个坐标。</p>
     *
     * @return 实际改动的方块数，用于日志与调试
     */
    int apply(EffectContext context, List<MapPoint> points);

    /**
     * 落地并交出后续。
     *
     * <p>一次性灾种不用覆盖它，默认就是「没有后续」。持续型灾种覆盖它，把首次生效放进去，
     * 再返回一个活动实例交给对局循环每秒推进。</p>
     */
    default Optional<ActiveDisaster> activate(EffectContext context, List<MapPoint> points) {
        return Optional.empty();
    }
}
