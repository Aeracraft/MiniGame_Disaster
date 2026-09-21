package com.xcreate.disaster.world;

/**
 * 方块与世界写入。
 *
 * <p>{@link BlockWriter} 是<b>对局世界</b>里改方块的唯一入口——灾难砸地形、将来的世界重置都走它。
 * 收敛到一处是为了两件事：高度图缓存能跟着每次变更增量更新，录像能拿到「哪些方块是插件改的」。
 * 任何在副本世界里直接 {@code block.setType} 的写法都会破坏这两个前提。</p>
 *
 * <p>模板世界里的施工（{@code /ds map scaffold} 生成测试城市）不走这里，它不在对局语义内，
 * 也不该出现在录像里。</p>
 *
 * <p>{@link ChangeBatch} 与 {@link BlockPos} 不依赖服务端，形状计算与批次合并因此可以脱离
 * 世界单测。</p>
 */
