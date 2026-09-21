package com.xcreate.disaster.disaster;

/**
 * 灾难落点怎么取。
 *
 * <p>描述的是「灾难生成那一刻落点怎么定」，不描述落点之后怎么动——龙卷风、洪水这类
 * 位移与蔓延由灾难自己的运行时行为处理。</p>
 *
 * <p>取不到「地面」的灾难（酸雨、洪水、脚下岩浆、混战）用 {@link #NONE}：它们要么全图生效，
 * 要么作用在玩家脚下，本来就没有落点。</p>
 */
public enum SpawnStrategy {

    /** 全图生效或跟随玩家，本次不产生落点。 */
    NONE,

    /** 边界内均匀随机。 */
    RANDOM_IN_BOUNDS,

    /** 集中在一名随机玩家附近。 */
    NEAR_PLAYER,

    /** 地图上的高处。按列高度采样取前几名，不做全图扫描。 */
    HIGH_POINTS,

    /** 每名玩家附近各取几个点，用于需要追着人打的灾难。 */
    AROUND_EACH_PLAYER,

    /** 贴着边界的一圈，用于「从图外涌入」的灾难。 */
    FROM_EDGE,

    /** 地图正中心，多点时在中心周围小幅散开。 */
    AT_MAP_CENTER;

    /** 写错了退回边界内随机，至少落点还在图里。 */
    public static SpawnStrategy parse(String raw) {
        if (raw != null) {
            for (SpawnStrategy strategy : values()) {
                if (strategy.name().equalsIgnoreCase(raw.trim())) {
                    return strategy;
                }
            }
        }
        return RANDOM_IN_BOUNDS;
    }

    public boolean producesPoints() {
        return this != NONE;
    }

    /** 需要玩家坐标才能定落点。 */
    public boolean needsPlayers() {
        return this == NEAR_PLAYER || this == AROUND_EACH_PLAYER;
    }

    public String label() {
        return switch (this) {
            case NONE -> "不产生落点";
            case RANDOM_IN_BOUNDS -> "边界内随机";
            case NEAR_PLAYER -> "随机一名玩家附近";
            case HIGH_POINTS -> "地图高处";
            case AROUND_EACH_PLAYER -> "每名玩家附近";
            case FROM_EDGE -> "地图边缘";
            case AT_MAP_CENTER -> "地图中心";
        };
    }
}
