package com.xcreate.disaster.api.room;

import org.bukkit.World;

import java.io.File;

/**
 * 申请一个房间世界。
 *
 * @param roomId       房间标识，本地实现里就是世界名
 * @param worldName    期望的世界名
 * @param templateFolder 模板世界目录，作为拷贝源
 * @param environment  世界维度。模板世界已加载时按它的实际维度填，否则为主世界
 */
public record RoomRequest(String roomId, String worldName, File templateFolder,
                          World.Environment environment) {
}
