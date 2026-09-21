package com.xcreate.disaster.disaster;

/**
 * 落点校验时要问的地形信息。
 *
 * <p>只暴露一个方法是有意的：校验链关心的是「这一列能不能站人」，而「跳过液体、跳过空气、
 * 避开已经被打烂的地面」都是同一件事的不同说法，全都能在找地面的过程中一并处理掉。
 * 拆成多个方法只会让校验方重复走一遍方块读取。</p>
 *
 * <p>抽成接口是为了让校验链能脱离服务端跑测试——真实实现只读世界，测试实现读一张表。</p>
 */
public interface SpawnTerrain {

    /** 该列没有可落点的地方（整列被破坏、或只有液体）。 */
    int NO_GROUND = Integer.MIN_VALUE;

    /**
     * 该列可落点的方块 Y：从地表往下找到的第一块可站立方块。
     *
     * <p>找不到返回 {@link #NO_GROUND}。</p>
     */
    int groundY(int x, int z);
}
