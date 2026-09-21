package com.xcreate.disaster.disaster;

import java.util.Locale;

/**
 * 灾难层级。
 *
 * <p>主灾难每波必出，次灾难只按概率伴随出现。两者是两个独立的候选池，
 * 掷骰时不会互相挤占。</p>
 */
public enum DisasterTier {

    PRIMARY,

    SECONDARY;

    /** 写错了退回主灾，至少不会让一场灾难凭空消失。 */
    public static DisasterTier parse(String raw) {
        if (raw != null) {
            for (DisasterTier tier : values()) {
                if (tier.name().equalsIgnoreCase(raw.trim())) {
                    return tier;
                }
            }
        }
        return PRIMARY;
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return this == PRIMARY ? "主灾" : "次灾";
    }
}
