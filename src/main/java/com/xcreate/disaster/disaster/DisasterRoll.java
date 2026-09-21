package com.xcreate.disaster.disaster;

import java.util.ArrayList;
import java.util.List;

/**
 * 一波掷骰的结果。
 *
 * <p>主灾与次灾分开列，因为它们的语义不同：主灾是这一波的主要威胁，次灾只是伴随而来。
 * 两者都可能是空列表——候选池被排除光了就是这种情况，此时这一波什么都不放，
 * 而不是硬凑一个出来。</p>
 */
public record DisasterRoll(int waveIndex, List<DisasterDefinition> primary,
                           List<DisasterDefinition> secondary) {

    public DisasterRoll {
        primary = primary == null ? List.of() : List.copyOf(primary);
        secondary = secondary == null ? List.of() : List.copyOf(secondary);
    }

    public static DisasterRoll empty(int waveIndex) {
        return new DisasterRoll(waveIndex, List.of(), List.of());
    }

    public boolean isEmpty() {
        return primary.isEmpty() && secondary.isEmpty();
    }

    public boolean hasSecondary() {
        return !secondary.isEmpty();
    }

    /** 主灾在前、次灾在后。 */
    public List<DisasterDefinition> all() {
        List<DisasterDefinition> combined = new ArrayList<>(primary.size() + secondary.size());
        combined.addAll(primary);
        combined.addAll(secondary);
        return combined;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (DisasterDefinition definition : all()) {
            ids.add(definition.id());
        }
        return ids;
    }

    /** 一行摘要，给命令输出与日志用。 */
    public String summary() {
        if (isEmpty()) {
            return "无（候选池为空）";
        }
        StringBuilder text = new StringBuilder();
        for (DisasterDefinition definition : primary) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(definition.displayName());
        }
        for (DisasterDefinition definition : secondary) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(definition.displayName()).append("（次）");
        }
        return text.toString();
    }
}
