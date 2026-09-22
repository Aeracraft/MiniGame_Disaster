package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 在地图边界内撒列。
 *
 * <p>酸雨这类全图型灾难不产生落点，它的作用对象是「一根柱子从地表往下」，不是空中某个坐标，
 * 所以取的是列不是点。只保证边界内均匀取、同一列不重复取——重复一次等于白撒。
 *
 * <p>纯算术，不碰世界，取列的结果可以直接单测。
 */
public final class ColumnSampler {

    private ColumnSampler() {
    }

    /** 一列的水平位置。 */
    public record Column(int x, int z) {
    }

    /**
     * 取 {@code count} 列。
     *
     * <p>用拒绝采样：撞重就重掷，给足次数还取不满就返回取到的那些。边界本来就是有限格，
     * 想取满整张图本来就做不到，返回少几个比死循环强。
     */
    public static List<Column> pick(MapBounds bounds, Random random, int count) {
        List<Column> columns = new ArrayList<>();
        if (bounds == null || random == null || count <= 0) {
            return columns;
        }
        int sizeX = bounds.sizeX();
        int sizeZ = bounds.sizeZ();
        if (sizeX <= 0 || sizeZ <= 0) {
            return columns;
        }

        long area = (long) sizeX * sizeZ;
        if (count >= area) {
            // 要的比整张图还多，直接全给，省得靠撞运气一个一个凑
            return everyColumn(bounds, sizeX, sizeZ);
        }
        long attempts = Math.min((long) count * 8 + 16, area);
        Set<Long> seen = new HashSet<>();
        for (long i = 0; i < attempts && columns.size() < count; i++) {
            int x = bounds.minX() + random.nextInt(sizeX);
            int z = bounds.minZ() + random.nextInt(sizeZ);
            if (seen.add(key(x, z))) {
                columns.add(new Column(x, z));
            }
        }
        return columns;
    }

    private static List<Column> everyColumn(MapBounds bounds, int sizeX, int sizeZ) {
        List<Column> all = new ArrayList<>(sizeX * sizeZ);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                all.add(new Column(x, z));
            }
        }
        return all;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
