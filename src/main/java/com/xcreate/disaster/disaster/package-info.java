/**
 * 灾种目录与落点。
 *
 * <p>这一层解决两个问题：一波该砸哪几个灾难（{@link com.xcreate.disaster.disaster.WaveRoller}），
 * 以及砸在哪里（{@link com.xcreate.disaster.disaster.SpawnPlanner}）。灾难真正做什么——
 * 打雷、涨水、刷怪——不在这里，那部分按灾种 id 另挂运行时行为。</p>
 *
 * <p>落点必须过 {@link com.xcreate.disaster.disaster.SpawnValidator} 的校验链，并且一律先被
 * 地图边界裁掉：没标边界不许开局，边界外可能是隔壁世界的建筑。随机点凑不满就少落几个，
 * 绝不往没地面或者图外的坐标上砸。</p>
 *
 * <p>地形读取被收进 {@link com.xcreate.disaster.disaster.SpawnTerrain} 这一个接口，所以整条
 * 落点链路不依赖服务端也能跑测试；落点坐标用 {@code MapPoint} 承载，同样不带世界对象。</p>
 *
 * <p>落点的三个大类：全图覆盖（酸雨、洪水）没有落点，用 {@code SpawnStrategy.NONE}；
 * 边界内随机点（流星雨、落雷、地陷）走校验链；移动轨迹（龙卷风）在生成时定一个起点，
 * 之后的位移由灾难自身处理。</p>
 */
package com.xcreate.disaster.disaster;
