package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;

import java.util.Random;

/**
 * 龙卷风的进场点与行进方向。
 *
 * <p>从四条边里随机挑一条的中段进来，方向指着图心，所以穿过的是整张图而不是擦个边就走。</p>
 *
 * <p>纯算术，不碰世界，进场点与推进都能单测。</p>
 */
public final class TornadoPath {

    private TornadoPath() {
    }

    /** 平面上的一个位置，高度由使用方自己定。 */
    public record Spot(double x, double z) {

        public Spot moved(Spot direction, double distance) {
            return new Spot(x + direction.x() * distance, z + direction.z() * distance);
        }
    }

    public static Spot enter(MapBounds bounds, Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new Spot(bounds.minX() + 0.5, between(bounds.minZ(), bounds.maxZ(), random));
            case 1 -> new Spot(bounds.maxX() + 0.5, between(bounds.minZ(), bounds.maxZ(), random));
            case 2 -> new Spot(between(bounds.minX(), bounds.maxX(), random), bounds.minZ() + 0.5);
            default -> new Spot(between(bounds.minX(), bounds.maxX(), random), bounds.maxZ() + 0.5);
        };
    }

    /** 指向图心的单位向量。进场点正好落在图心时给一个兜底方向，避免除零。 */
    public static Spot direction(MapBounds bounds, Spot from) {
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double dx = centerX - from.x();
        double dz = centerZ - from.z();
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) {
            return new Spot(1.0, 0.0);
        }
        return new Spot(dx / length, dz / length);
    }

    /** 已经走完全程。留一点余量，别在图边上一闪就没。 */
    public static boolean done(MapBounds bounds, Spot at) {
        double margin = 8.0;
        return at.x() < bounds.minX() - margin || at.x() > bounds.maxX() + margin
                || at.z() < bounds.minZ() - margin || at.z() > bounds.maxZ() + margin;
    }

    public static double distanceTo(Spot at, double x, double z) {
        return Math.hypot(at.x() - x, at.z() - z);
    }

    private static double between(int min, int max, Random random) {
        if (max <= min) {
            return min + 0.5;
        }
        return min + 0.5 + random.nextInt(max - min + 1);
    }
}
