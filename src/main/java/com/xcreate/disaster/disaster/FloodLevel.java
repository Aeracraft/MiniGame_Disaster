package com.xcreate.disaster.disaster;

/**
 * 洪水的水位。
 *
 * <p>水位只看开局时刻与涨水间隔，跟这一层铺到哪儿了无关——铺得慢只是水漫得慢，
 * 不该反过来把水位卡住，否则地图越大水越浅。</p>
 *
 * <p>纯算术，可单测。</p>
 */
public final class FloodLevel {

    private FloodLevel() {
    }

    /**
     * 第 {@code elapsedSeconds} 秒时的水位高度。
     *
     * @param riseSeconds 每涨一格要几秒。小于等于 0 时不涨
     */
    public static int at(int startLevel, int riseSeconds, int elapsedSeconds) {
        if (riseSeconds <= 0) {
            return startLevel;
        }
        return startLevel + Math.max(0, elapsedSeconds) / riseSeconds;
    }
}
