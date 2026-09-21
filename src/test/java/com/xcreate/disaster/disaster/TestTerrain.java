package com.xcreate.disaster.disaster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 一张写在表里的地形，给落点相关测试用。
 *
 * <p>默认整片同一高度，需要时单独把某一列抬高或标成「没地面」。</p>
 */
final class TestTerrain implements SpawnTerrain {

    private final int defaultY;
    private final Map<Long, Integer> columns = new HashMap<>();
    private final Set<Long> broken = new HashSet<>();

    TestTerrain(int defaultY) {
        this.defaultY = defaultY;
    }

    TestTerrain raise(int x, int z, int y) {
        columns.put(key(x, z), y);
        return this;
    }

    /** 整列被打空，没有可站立的地方。 */
    TestTerrain breakColumn(int x, int z) {
        broken.add(key(x, z));
        return this;
    }

    /** 把一片区域打成空的，用来测试「随机取点都取不到地面」。 */
    TestTerrain breakArea(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                broken.add(key(x, z));
            }
        }
        return this;
    }

    @Override
    public int groundY(int x, int z) {
        long key = key(x, z);
        if (broken.contains(key)) {
            return NO_GROUND;
        }
        Integer override = columns.get(key);
        return override == null ? defaultY : override;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
