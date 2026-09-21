package com.xcreate.disaster.api.replay;

import java.util.List;
import java.util.UUID;

/**
 * 一次待录制的对局。
 *
 * <p>由 Disaster 在开局时构造并交给 {@link ReplayProvider#startRecording}。
 * 所有字段都是不可变的。</p>
 *
 * @param matchId        本局唯一 ID，同时也是录像的检索键
 * @param mapId          地图定义 ID（英文标识，非显示名）
 * @param roomId         房间标识
 * @param serverId       子服标识；单服部署时可留空字符串
 * @param players        本局参与者。正版验证服务器上这些就是 Mojang UUID
 * @param startTimeMillis 录制开始时间（{@code System.currentTimeMillis()}）
 */
public record ReplaySession(
        String matchId,
        String mapId,
        String roomId,
        String serverId,
        List<UUID> players,
        long startTimeMillis
) {

    public ReplaySession {
        players = players == null ? List.of() : List.copyOf(players);
        serverId = serverId == null ? "" : serverId;
    }

    public int playerCount() {
        return players.size();
    }
}
