package com.xcreate.disaster.disaster;

import com.xcreate.disaster.world.BlockWriter;
import org.bukkit.World;

/**
 * 灾难落到地上时能碰到的东西。
 *
 * @param world      副本世界
 * @param terrain    地表读取入口。真实实现读世界，测试实现读一张表
 * @param blocks     方块变更的唯一入口。改方块一律经它，别直接 setType
 * @param definition 本灾种的定义，自由参数从 {@code definition.options()} 里取
 */
public record EffectContext(World world, SpawnTerrain terrain, BlockWriter blocks,
                            DisasterDefinition definition) {
}
