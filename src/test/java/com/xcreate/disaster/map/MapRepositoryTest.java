package com.xcreate.disaster.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 地图文件的读写回归。
 *
 * <p>地图文件是给服主手改的，格式一旦写歪，编译期完全看不出来，只会在管理员的机器上炸。
 * 这里把「写出去能读回来」「缺键能兜住」「文件名说了算」这几条钉死。</p>
 */
class MapRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger("map-repository-test");

    @TempDir
    Path tempDir;

    private MapRepository repository() {
        return new MapRepository(tempDir.toFile(), LOGGER);
    }

    private MapDefinition sample() {
        return new MapDefinition("city", "城市", "city", "city", true, 2.0,
                MapDefinition.FOLLOW_GLOBAL, 24,
                new MapBounds(-64, 0, -64, 64, 96, 64),
                List.of(
                        new MapPoint("city", "北门", 0.5, 65, -40.5, 0f, 0f),
                        new MapPoint("city", "南门", 0.5, 65, 40.5, 180f, 12.5f)),
                new MapPoint("city", "观战", 0.5, 140, 0.5, 0f, 45f),
                Map.of("meteor", List.of(new MapPoint("city", "广场", 0.5, 70, 0.5, 0f, 0f))),
                List.of(new MapPoint("city", "", 20.5, 65, 20.5, 0f, 0f)));
    }

    @Test
    void 写出再读回来内容一致() throws IOException {
        MapRepository repository = repository();
        MapDefinition original = sample();

        repository.save(original);
        MapDefinition loaded = repository.load("city").orElseThrow();

        assertEquals(original.id(), loaded.id());
        assertEquals(original.displayName(), loaded.displayName());
        assertEquals(original.templateFolder(), loaded.templateFolder());
        assertEquals(original.world(), loaded.world());
        assertEquals(original.weight(), loaded.weight());
        assertEquals(original.maxPlayers(), loaded.maxPlayers());
        assertEquals(original.bounds(), loaded.bounds());
        assertEquals(original.spawns(), loaded.spawns());
        assertEquals(original.spectatorSpawn(), loaded.spectatorSpawn());
        assertEquals(original.anchors(), loaded.anchors());
        assertEquals(original.lootChests(), loaded.lootChests());
    }

    @Test
    void 未设置权重时回到基准值() throws IOException {
        MapRepository repository = repository();
        repository.save(sample().toBuilder().weight(-5).build());

        assertEquals(1.0, repository.load("city").orElseThrow().weight());
    }

    @Test
    void 文件名决定地图id() throws IOException {
        MapRepository repository = repository();
        Files.writeString(tempDir.resolve("harbor.yml"),
                "id: something-else\ndisplay-name: 港口\n", StandardCharsets.UTF_8);

        MapDefinition loaded = repository.load("harbor").orElseThrow();

        assertEquals("harbor", loaded.id());
        assertEquals("港口", loaded.displayName());
        assertNull(loaded.bounds());
        assertTrue(loaded.spawns().isEmpty());
    }

    @Test
    void 半截文件也能读出来并且报错() throws IOException {
        MapRepository repository = repository();
        Files.writeString(tempDir.resolve("broken.yml"),
                "display-name: 半成品\nbounds:\n  min-x: 0\n",
                StandardCharsets.UTF_8);

        MapDefinition loaded = repository.load("broken").orElseThrow();

        assertNotNull(loaded.bounds());
        assertEquals(0, loaded.bounds().minX());
        assertFalse(loaded.playable());

        List<MapIssue> issues = MapCheck.errors(loaded);
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("出生点")));
    }

    @Test
    void 缺坐标的点位被丢掉而不是写出个零点() throws IOException {
        MapRepository repository = repository();
        Files.writeString(tempDir.resolve("picky.yml"),
                "world: picky\nspawns:\n  - name: 好点\n    x: 1\n    y: 2\n    z: 3\n"
                        + "  - name: 坏点\n    x: 1\n    y: 2\n",
                StandardCharsets.UTF_8);

        MapDefinition loaded = repository.load("picky").orElseThrow();

        assertEquals(1, loaded.spawns().size());
        assertEquals("好点", loaded.spawns().get(0).name());
    }

    @Test
    void 下划线开头的文件不参与加载() throws IOException {
        MapRepository repository = repository();
        repository.save(sample());
        Files.writeString(tempDir.resolve("_example.yml"), "display-name: 样例\n",
                StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("notes.txt"), "随手记\n", StandardCharsets.UTF_8);

        assertEquals(List.of("city"), repository.listIds());
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    void 坐标保留两位小数() throws IOException {
        MapRepository repository = repository();
        MapDefinition definition = sample().toBuilder()
                .addSpawn(new MapPoint("city", "", 0.123456789, 65.987654321, -0.5, 0f, 0f))
                .build();

        repository.save(definition);
        MapDefinition loaded = repository.load("city").orElseThrow();
        MapPoint point = loaded.spawns().get(loaded.spawns().size() - 1);

        assertEquals(0.12, point.x());
        assertEquals(65.99, point.y());
        assertEquals(-0.5, point.z());
    }

    @Test
    void 坐标为零时省略朝向字段() throws IOException {
        MapRepository repository = repository();
        repository.save(sample());

        String text = Files.readString(tempDir.resolve("city.yml"), StandardCharsets.UTF_8);

        assertTrue(text.contains("spectator-spawn"), "观战点应当写出去");
        assertTrue(text.contains("disaster-anchors"), "固定落点应当写出去");
        assertTrue(text.contains("yaw: 180.0"), "非零朝向应当保留");
    }

    @Test
    void 删除后不再加载() throws IOException {
        MapRepository repository = repository();
        repository.save(sample());
        File file = repository.fileOf("city");
        assertTrue(file.isFile());

        assertTrue(repository.delete("city"));

        assertFalse(repository.fileOf("city").exists());
        assertTrue(repository.loadAll().isEmpty());
        assertTrue(repository.delete("city"), "重复删除不应报错");
    }
}
