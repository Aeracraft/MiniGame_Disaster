package com.xcreate.disaster.api.room;

import org.bukkit.World;

import java.io.File;

/**
 * 准备好的房间世界。
 *
 * <p>失败时 {@link #world()} 为 {@code null}，原因在 {@link #error()} 里。用值对象而不是抛异常，
 * 是因为申请与就绪之间隔着线程切换，异常传回主线程反而更难处理。</p>
 */
public record ProvisionedRoom(String roomId, String worldName, World world, File folder,
                              String error) {

    public static ProvisionedRoom ready(String roomId, String worldName, World world, File folder) {
        return new ProvisionedRoom(roomId, worldName, world, folder, "");
    }

    public static ProvisionedRoom failed(String roomId, String worldName, String error) {
        return new ProvisionedRoom(roomId, worldName, null, null,
                error == null || error.isBlank() ? "未知原因" : error);
    }

    public boolean success() {
        return world != null;
    }
}
