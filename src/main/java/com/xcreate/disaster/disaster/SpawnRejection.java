package com.xcreate.disaster.disaster;

/**
 * 落点被拒的原因。
 *
 * <p>带上原因是为了能回头看「这一波为什么只落下一半」——只报一句「失败」的话，
 * 服主只能靠猜是地图太小、边界标错，还是地面已经被前面的灾难打没了。</p>
 */
public enum SpawnRejection {

    /** 地图没标边界。没边界不许开局，正常跑不到这里。 */
    NO_BOUNDS,

    /** 落在边界外。 */
    OUT_OF_BOUNDS,

    /** 该列没有可站立的地面（被打空、只剩液体、或压根没生成）。 */
    NO_GROUND,

    /** 离地图出生点太近。 */
    TOO_CLOSE_TO_SPAWN,

    /** 与已确定的其他落点挤在一起。 */
    TOO_CLOSE_TO_POINT,

    /** 这个灾种要围着玩家取点，但场上没有玩家。 */
    NO_PLAYERS;

    public String label() {
        return switch (this) {
            case NO_BOUNDS -> "地图没标边界";
            case OUT_OF_BOUNDS -> "越出边界";
            case NO_GROUND -> "没有可站立的地面";
            case TOO_CLOSE_TO_SPAWN -> "离出生点太近";
            case TOO_CLOSE_TO_POINT -> "与其他落点太挤";
            case NO_PLAYERS -> "场上没有玩家";
        };
    }
}
