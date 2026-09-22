/**
 * 对局评价与玩家互评的可插拔契约。
 *
 * <p>两个互相独立的子系统共用这一套接口：对局质量打分（匿名给整局评星，供服主按地图与
 * 灾种聚合分析）与玩家互评点赞（同局标签式互赞，累积荣誉值）。两者都只收结构化选项，
 * 不收自由文本——没有文本就没有辱骂与灌水通道，也省掉一整套内容审核。</p>
 *
 * <p>插入点有三层：内置实现；{@link com.xcreate.disaster.api.reputation.ReputationProvider}
 * 注册到 {@code ServicesManager}（整体替换内置）；以及监听
 * {@link com.xcreate.disaster.api.event.DisasterMatchRatedEvent} 与
 * {@link com.xcreate.disaster.api.event.DisasterPlayerRepEvent} 旁听。只想记录不想接管时用第三层。</p>
 */
package com.xcreate.disaster.api.reputation;
