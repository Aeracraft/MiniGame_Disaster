/**
 * 房间系统。
 *
 * <p>一个房间 = 一个副本世界 = 一局对局。并行的房间绑不同的地图，各自独立。</p>
 *
 * <p>这一层的写法全是被 Bukkit 的三条硬约束逼出来的：</p>
 * <ol>
 *   <li><b>同一个世界名同一时刻只能加载一次</b> → 房间标识直接用世界名，副本按
 *       {@code ds_<地图 id>_<序号>} 编号，见 {@link com.xcreate.disaster.room.RoomNaming}</li>
 *   <li><b>所有世界共享同一个主线程 tick</b> → 并行局数每加一，单 tick 负载叠加。
 *       所以这里的活儿都是「拷贝走异步、世界操作走主线程」，没有一处会随房间数变重</li>
 *   <li><b>加载、卸载、删除世界只能在主线程，且卸载会踢出该世界的玩家</b> →
 *       {@link com.xcreate.disaster.room.RoomManager#destroy} 的顺序固定为
 *       先传走玩家、再卸载世界、最后删目录</li>
 * </ol>
 */
package com.xcreate.disaster.room;
