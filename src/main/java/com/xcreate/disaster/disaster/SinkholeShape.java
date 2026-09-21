package com.xcreate.disaster.disaster;

/**
 * 地陷的坑形。
 *
 * <p>碗形：中心最深，向边缘按距离平方衰减到 0。平方衰减而不是线性，是为了让坑沿平缓、
 * 坑底陡——线性衰减挖出来的是漏斗，玩家掉进去爬不出来，而碗形能走出来。</p>
 *
 * <p>纯算术，不碰世界，所以坑形可以直接单测。</p>
 */
public final class SinkholeShape {

    private SinkholeShape() {
    }

    /**
     * 某一列要往下挖几格。
     *
     * @param dx       相对坑心的 X 偏移
     * @param dz       相对坑心的 Z 偏移
     * @param radius   坑的半径（格）。小于 1 时什么都不挖
     * @param maxDepth 坑心处的深度（格）
     * @return 挖的格数，落在半径外或算出来不足一格时为 0
     */
    public static int depthAt(int dx, int dz, int radius, int maxDepth) {
        if (radius < 1 || maxDepth < 1) {
            return 0;
        }
        double distanceSq = (double) dx * dx + (double) dz * dz;
        double radiusSq = (double) radius * radius;
        if (distanceSq > radiusSq) {
            return 0;
        }
        double ratio = Math.sqrt(distanceSq) / radius;
        int depth = (int) Math.round(maxDepth * (1.0 - ratio * ratio));
        return Math.max(0, depth);
    }

    /**
     * 这个偏移在不在坑的范围内。
     *
     * <p>用圆而不是方的——方形坑在俯视图上一眼就是插件画的。</p>
     */
    public static boolean inside(int dx, int dz, int radius) {
        return radius > 0 && (long) dx * dx + (long) dz * dz <= (long) radius * radius;
    }
}
