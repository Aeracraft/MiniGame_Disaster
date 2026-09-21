package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapPoint;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一次落点规划的结果。
 *
 * <p>记的不只是「落在哪」，还包括「想要几个、实际落下几个、试了多少次、为什么被拒」。
 * 灾难高峰期只落下一半是很可能发生的（地图小、地面被打烂），这时候需要能解释清楚，
 * 而不是让服主看到一个安静少了一半的流星雨。</p>
 *
 * @param requested  这一波计划产生几个落点
 * @param points     实际确定下来的落点
 * @param attempts   为了凑出这些点，一共试了多少次
 * @param rejections 各类拒绝原因的次数
 */
public record SpawnOutcome(int requested, List<MapPoint> points, int attempts,
                           Map<SpawnRejection, Integer> rejections) {

    public SpawnOutcome {
        points = points == null ? List.of() : List.copyOf(points);
        rejections = rejections == null ? Map.of() : Map.copyOf(rejections);
    }

    public static SpawnOutcome none() {
        return new SpawnOutcome(0, List.of(), 0, Map.of());
    }

    /** 一个点都不该产生，但整体被拒了（比如没有边界）。 */
    public static SpawnOutcome rejected(int requested, SpawnRejection reason) {
        Map<SpawnRejection, Integer> counts = new EnumMap<>(SpawnRejection.class);
        counts.put(reason, Math.max(1, requested));
        return new SpawnOutcome(requested, List.of(), 0, counts);
    }

    public boolean placed() {
        return !points.isEmpty();
    }

    /** 没有任何落点，且有东西挡住了。整波被拒与逐点被拒都算。 */
    public boolean abandoned() {
        return points.isEmpty() && !rejections.isEmpty();
    }

    /** 落点数少于计划数，属于部分失败。 */
    public boolean partial() {
        return placed() && points.size() < requested;
    }

    public int dropped() {
        return Math.max(0, requested - points.size());
    }

    public String summary() {
        if (requested == 0 && rejections.isEmpty()) {
            return "本体不产生落点";
        }
        StringBuilder text = new StringBuilder();
        text.append(points.size()).append('/').append(requested);
        if (abandoned()) {
            text.append("（全部放弃）");
        } else if (partial()) {
            text.append("（部分落点失败）");
        }
        if (attempts > 0) {
            text.append("，试了 ").append(attempts).append(" 次");
        }
        if (!rejections.isEmpty()) {
            text.append("；拒绝原因：");
            boolean first = true;
            for (Map.Entry<SpawnRejection, Integer> entry : rejections.entrySet()) {
                if (!first) {
                    text.append("，");
                }
                first = false;
                text.append(entry.getKey().label()).append(' ')
                        .append(entry.getValue()).append(" 次");
            }
        }
        return text.toString();
    }

    /** 落点转成可读坐标，给命令输出用。 */
    public List<String> pointTexts() {
        return points.stream()
                .map(point -> String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                        point.x(), point.y(), point.z()))
                .toList();
    }
}
