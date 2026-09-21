package com.xcreate.disaster.disaster;

/**
 * 一个灾种的定义。
 *
 * <p>不可变，读自 {@code disasters/<id>.yml}。这里只描述「掷骰时怎么算权重、生成时落点怎么取」，
 * 灾难具体做什么（打雷、涨水、刷怪）是代码里按 {@link #id()} 对应的运行时行为，不在定义里。</p>
 *
 * @param weight    掷骰时的相对权重
 * @param pointCount 本体一次产生几个落点。{@link SpawnStrategy#AROUND_EACH_PLAYER} 时是「每人几个」
 */
public record DisasterDefinition(
        String id,
        String displayName,
        DisasterTier tier,
        boolean enabled,
        double weight,
        SpawnStrategy strategy,
        int pointCount,
        DisasterOptions options) {

    public DisasterDefinition {
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        options = options == null ? DisasterOptions.empty() : options;
        pointCount = Math.max(0, pointCount);
    }

    /** 能进候选池：开关打开且权重为正。 */
    public boolean rolls() {
        return enabled && weight > 0;
    }

    /** 本体会产生落点。{@link SpawnStrategy#NONE} 与落点数为 0 的都是全图型。 */
    public boolean producesPoints() {
        return strategy.producesPoints() && pointCount > 0;
    }

    /** 按上限截断后的权重，供掷骰用。cap 非正表示不限制。 */
    public double effectiveWeight(double cap) {
        return cap > 0 ? Math.min(weight, cap) : weight;
    }
}
