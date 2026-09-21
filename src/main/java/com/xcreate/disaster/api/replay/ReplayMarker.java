package com.xcreate.disaster.api.replay;

import java.util.Map;

/**
 * 录像时间轴上的一个语义标记。
 *
 * <p>录像本身只记录「看得见的东西」（数据包），而「第 5 分 12 秒掷中的是流星雨」
 * 这类语义信息只有玩法插件知道。标记就是用来把语义补进录像的，
 * 有了它才能做战报、精彩片段定位、按灾难检索录像。</p>
 *
 * @param type       标记类型，例如 {@code disaster_spawned}、{@code player_eliminated}
 * @param second     相对录制起点的秒数
 * @param attributes 附带的键值对，由各标记类型自行约定
 */
public record ReplayMarker(
        String type,
        int second,
        Map<String, String> attributes
) {

    public static final String TYPE_MATCH_START = "match_start";
    public static final String TYPE_DISASTER_SPAWNED = "disaster_spawned";
    public static final String TYPE_PLAYER_ELIMINATED = "player_eliminated";
    public static final String TYPE_MATCH_END = "match_end";

    public ReplayMarker {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ReplayMarker of(String type, int second) {
        return new ReplayMarker(type, second, Map.of());
    }
}
