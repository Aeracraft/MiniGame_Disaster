package com.xcreate.disaster.match;

import com.xcreate.disaster.api.storage.MatchParticipant;
import com.xcreate.disaster.api.storage.MatchRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 对局规则的回归：淘汰记账、存活判定、什么时候该收局、结算记录。 */
class MatchTest {

    private static final long START = 1_700_000_000_000L;

    @Test
    void 开局时全员存活且还没掷过波() {
        Match match = match(3);

        assertEquals(3, match.alive().size());
        assertEquals(3, match.survivors().size());
        assertEquals(0, match.waveIndex());
        assertEquals(0, match.elapsedSeconds(START));
        assertTrue(match.usedDisasters().isEmpty());
    }

    @Test
    void 经过时间按毫秒折算成秒() {
        Match match = match(2);

        assertEquals(59, match.elapsedSeconds(START + 59_500L));
        assertEquals(60, match.elapsedSeconds(START + 60_000L));
    }

    @Test
    void 同一个人不会被淘汰两次() {
        Match match = match(2);
        UUID victim = match.participants().get(0);

        assertTrue(match.eliminate(victim, "lava", null, 10));
        assertFalse(match.eliminate(victim, "fall", null, 20));
        assertEquals("lava", match.eliminationOf(victim).cause());
        assertEquals(10, match.eliminationOf(victim).elapsedSeconds());
    }

    @Test
    void 不在名单上的人淘汰不掉() {
        Match match = match(2);
        assertFalse(match.eliminate(UUID.randomUUID(), "lava", null, 5));
    }

    @Test
    void 被淘汰的不算存活() {
        Match match = match(3);
        UUID victim = match.participants().get(1);
        match.eliminate(victim, "pvp", match.participants().get(0), 30);

        assertEquals(2, match.alive().size());
        assertFalse(match.alive().contains(victim));
        assertTrue(match.isEliminated(victim));
    }

    @Test
    void 名字在开局时快照下来() {
        Match match = match(2);
        UUID known = match.participants().get(0);

        assertEquals("玩家0", match.nameOf(known));
        assertEquals("", match.nameOf(UUID.randomUUID()));
    }

    @Test
    void 波次从一递增() {
        Match match = match(2);

        assertEquals(1, match.nextWave());
        assertEquals(2, match.nextWave());
        assertEquals(2, match.waveIndex());
    }

    @Test
    void 掷中过的灾种会去重且忽略空白() {
        Match match = match(2);
        match.markUsed("sinkhole");
        match.markUsed("sinkhole");
        match.markUsed("  ");
        match.markUsed(null);

        assertEquals(1, match.usedDisasters().size());
        assertTrue(match.usedDisasters().contains("sinkhole"));
    }

    @Test
    void 到时长就该收局() {
        Match match = match(4);

        assertFalse(match.isOver(START + 599_000L, 600));
        assertTrue(match.isOver(START + 600_000L, 600));
    }

    @Test
    void 只剩一个人就提前收局() {
        Match match = match(4);
        match.eliminate(match.participants().get(0), "lava", null, 5);
        assertFalse(match.isOver(START, 600), "还剩三个不该收");

        match.eliminate(match.participants().get(1), "lava", null, 6);
        match.eliminate(match.participants().get(2), "lava", null, 7);
        assertTrue(match.isOver(START, 600));
    }

    @Test
    void 全部出局也算收局() {
        Match match = match(2);
        match.eliminate(match.participants().get(0), "lava", null, 5);
        match.eliminate(match.participants().get(1), "lava", null, 6);

        assertTrue(match.isOver(START, 600));
        assertTrue(match.survivors().isEmpty(), "无人获胜是合法结果");
    }

    @Test
    void 单人局不会一开局就收掉() {
        Match solo = match(1);

        assertFalse(solo.isOver(START, 600));
        assertFalse(solo.isOver(START + 1000L, 600));
    }

    @Test
    void 结算记录按人区分存活与死因() {
        Match match = match(2);
        UUID winner = match.participants().get(0);
        UUID fallen = match.participants().get(1);
        match.markUsed("sinkhole");
        match.eliminate(fallen, "fall", winner, 120);

        MatchRecord record = match.toRecord("sub-1", START + 300_000L);

        assertEquals("sub-1", record.serverId());
        assertEquals(300, record.durationSeconds());
        assertEquals(List.of("sinkhole"), record.disasterIds());
        assertEquals(List.of(winner), record.survivors().stream()
                .map(MatchParticipant::playerId).toList());

        Map<UUID, MatchParticipant> byId = new LinkedHashMap<>();
        for (MatchParticipant participant : record.participants()) {
            byId.put(participant.playerId(), participant);
        }
        MatchParticipant survivorRecord = byId.get(winner);
        assertTrue(survivorRecord.survived());
        assertNull(survivorRecord.deathCause());
        assertEquals(300, survivorRecord.survivalSeconds());

        MatchParticipant fallenRecord = byId.get(fallen);
        assertFalse(fallenRecord.survived());
        assertEquals("fall", fallenRecord.deathCause());
        assertEquals(120, fallenRecord.survivalSeconds());
    }

    @Test
    void 对局标识带上房间名且随时间变化() {
        String first = Match.idOf("ds_city_1", START);
        String sameMoment = Match.idOf("ds_city_1", START);
        String later = Match.idOf("ds_city_1", START + 1);

        assertEquals(first, sameMoment);
        assertNotEquals(first, later);
        assertTrue(first.startsWith("ds_city_1-"));
    }

    private static Match match(int players) {
        List<UUID> ids = new java.util.ArrayList<>(players);
        Map<UUID, String> names = new LinkedHashMap<>();
        for (int i = 0; i < players; i++) {
            UUID playerId = new UUID(0L, i + 1);
            ids.add(playerId);
            names.put(playerId, "玩家" + i);
        }
        return new Match(Match.idOf("ds_city_1", START), "ds_city_1", "city", START, ids, names);
    }
}
