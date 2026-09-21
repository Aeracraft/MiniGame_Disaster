package com.xcreate.disaster.disaster;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 读真实世界的地形信息。
 *
 * <p>走服务端的区块高度图拿地表，不做全图扫描——每多一局并行，主线程 tick 就更紧，
 * 灾难的取点不能变成一次地形普查。</p>
 *
 * <p>高度图给的是最高非空气方块，水面也在内，所以还要往下退几格找真正的可站立方块。
 * 退的格数有上限：万一某列被彻底打空，一路退到底只会白读几千个方块。</p>
 */
public final class WorldTerrain implements SpawnTerrain {

    private static final int MAX_DESCEND = 8;

    private final World world;

    public WorldTerrain(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    @Override
    public int groundY(int x, int z) {
        int floor = world.getMinHeight();
        int top = world.getHighestBlockYAt(x, z);
        int lowest = Math.max(floor, top - MAX_DESCEND);

        for (int y = top; y >= lowest; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isSolid() && !block.isLiquid()) {
                return y;
            }
        }
        return NO_GROUND;
    }
}
