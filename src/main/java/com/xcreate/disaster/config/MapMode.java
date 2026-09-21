package com.xcreate.disaster.config;

import java.util.Locale;

/** 抽图模式，对应 config.yml 的 {@code map-selection.mode}。 */
public enum MapMode {

    /** 按权重随机。 */
    RANDOM,

    /** 玩家投票决定；投票关掉或没人投时退回随机。 */
    VOTE,

    /** 按 id 顺序轮转。 */
    ROTATE,

    /** 固定用某一张图，见 {@code map-selection.fixed-map}。 */
    FIXED;

    /** 写错了就退回默认的随机，不抛异常。 */
    public static MapMode parse(String raw) {
        if (raw == null) {
            return RANDOM;
        }
        for (MapMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return RANDOM;
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
