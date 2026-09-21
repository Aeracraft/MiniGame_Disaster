package com.xcreate.disaster.api.storage;

import java.util.UUID;

/**
 * 一局里的一个参与者。
 *
 * @param playerId        跨平台关联只用这个，名字会改
 * @param survived        是否活到对局结束
 * @param survivalSeconds 存活时长
 * @param deathCause      死因标识，存活者为 null
 */
public record MatchParticipant(
        UUID playerId,
        String playerName,
        boolean survived,
        long survivalSeconds,
        String deathCause) {
}
