package com.xcreate.disaster.world;

/**
 * 一个方块坐标。
 *
 * <p>形状计算（地陷挖多大、酸雨淋到哪一层）只用整数方块坐标，不带世界对象，
 * 这样算出来的结果能直接单测，执行时再由世界上下文补上「改哪个世界」。</p>
 */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
