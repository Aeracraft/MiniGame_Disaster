package com.xcreate.disaster.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 地图定义文件（{@code maps/<id>.yml}）的读写。
 *
 * <p>文件是给服主手改的，所以读取时对缺键、类型写错一律容忍——读不动的那一项退回默认值，
 * 能救回来多少算多少，绝不因为一个文件写坏就让插件起不来。</p>
 *
 * <p>文件名决定地图 id，文件里写的 {@code id} 只作参考，两者不一致时以文件名为准。</p>
 */
public final class MapRepository {

    /** 下划线开头的文件不参与加载，用来放样例与备份。 */
    static final String IGNORED_PREFIX = "_";

    private static final String EXTENSION = ".yml";

    private final File folder;
    private final Logger logger;

    public MapRepository(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    public File folder() {
        return folder;
    }

    public File fileOf(String id) {
        return new File(folder, id.toLowerCase(Locale.ROOT) + EXTENSION);
    }

    public List<String> listIds() {
        String[] names = folder.list();
        if (names == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(IGNORED_PREFIX) || !lower.endsWith(EXTENSION)) {
                continue;
            }
            ids.add(lower.substring(0, lower.length() - EXTENSION.length()));
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** 扫一遍目录。单个文件读失败只记一条日志，不影响其他地图。 */
    public List<MapDefinition> loadAll() {
        List<MapDefinition> definitions = new ArrayList<>();
        for (String id : listIds()) {
            try {
                read(fileOf(id)).ifPresent(definitions::add);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "地图文件 " + id + EXTENSION + " 读取失败，已跳过。", t);
            }
        }
        return definitions;
    }

    public Optional<MapDefinition> load(String id) {
        File file = fileOf(id);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            return read(file);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "地图文件 " + file.getName() + " 读取失败。", t);
            return Optional.empty();
        }
    }

    public void save(MapDefinition definition) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "地图定义。可以用 /ds map 命令改，也可以直接手改本文件，改完执行 /ds map reload。",
                "id 以文件名为准，改这里的 id 无效。",
                "world 是作图时用的模板世界名，定义里的坐标都相对它；开局时整组坐标会挪到副本世界。",
                "点位朝向为零时不写 yaw/pitch，含义就是朝北平视。"));

        yaml.set("id", definition.id());
        yaml.set("display-name", definition.displayName());
        yaml.set("template-folder", definition.templateFolder());
        yaml.set("world", definition.world());
        yaml.set("enabled", definition.enabled());
        yaml.set("weight", definition.weight());
        yaml.set("min-players", definition.minPlayers());
        yaml.set("max-players", definition.maxPlayers());

        MapBounds bounds = definition.bounds();
        if (bounds != null) {
            yaml.set("bounds.min-x", bounds.minX());
            yaml.set("bounds.min-y", bounds.minY());
            yaml.set("bounds.min-z", bounds.minZ());
            yaml.set("bounds.max-x", bounds.maxX());
            yaml.set("bounds.max-y", bounds.maxY());
            yaml.set("bounds.max-z", bounds.maxZ());
        }

        if (!definition.spawns().isEmpty()) {
            yaml.set("spawns", definition.spawns().stream().map(MapRepository::toMap).toList());
        }
        if (definition.spectatorSpawn() != null) {
            yaml.set("spectator-spawn", toMap(definition.spectatorSpawn()));
        }
        if (!definition.anchors().isEmpty()) {
            Map<String, List<Map<String, Object>>> anchors = new LinkedHashMap<>();
            definition.anchors().forEach((disasterId, points) ->
                    anchors.put(disasterId, points.stream().map(MapRepository::toMap).toList()));
            yaml.set("disaster-anchors", anchors);
        }
        if (!definition.lootChests().isEmpty()) {
            yaml.set("loot-chests", definition.lootChests().stream().map(MapRepository::toMap).toList());
        }

        yaml.save(fileOf(definition.id()));
    }

    public boolean delete(String id) {
        File file = fileOf(id);
        return !file.exists() || file.delete();
    }

    private Optional<MapDefinition> read(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = baseName(file);

        String declared = yaml.getString("id");
        if (declared != null && !declared.equalsIgnoreCase(id)) {
            logger.warning("地图文件 " + file.getName() + " 里写的 id（" + declared
                    + "）与文件名不符，以文件名为准。");
        }

        String world = orEmpty(yaml.getString("world"));

        return Optional.of(new MapDefinition(
                id,
                yaml.getString("display-name", id),
                yaml.getString("template-folder", id),
                world,
                yaml.getBoolean("enabled", true),
                yaml.getDouble("weight", 1.0),
                yaml.getInt("min-players", MapDefinition.FOLLOW_GLOBAL),
                yaml.getInt("max-players", MapDefinition.FOLLOW_GLOBAL),
                readBounds(yaml.getConfigurationSection("bounds")),
                readPoints(yaml.getMapList("spawns"), world),
                readPoint(yaml.getConfigurationSection("spectator-spawn"), world, file),
                readAnchors(yaml.getConfigurationSection("disaster-anchors"), world, file),
                readPoints(yaml.getMapList("loot-chests"), world)));
    }

    private static MapBounds readBounds(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new MapBounds(
                section.getInt("min-x"), section.getInt("min-y"), section.getInt("min-z"),
                section.getInt("max-x"), section.getInt("max-y"), section.getInt("max-z"));
    }

    private static List<MapPoint> readPoints(List<Map<?, ?>> raw, String world) {
        List<MapPoint> points = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            MapPoint point = fromMap(entry, world);
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    private MapPoint readPoint(ConfigurationSection section, String world, File file) {
        if (section == null) {
            return null;
        }
        MapPoint point = fromMap(section.getValues(false), world);
        if (point == null) {
            logger.warning("地图文件 " + file.getName() + " 里的 spectator-spawn 缺少坐标，已忽略。");
        }
        return point;
    }

    private Map<String, List<MapPoint>> readAnchors(ConfigurationSection section,
                                                    String world, File file) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<MapPoint>> anchors = new LinkedHashMap<>();
        for (String disasterId : section.getKeys(false)) {
            List<MapPoint> points = readPoints(section.getMapList(disasterId), world);
            if (points.isEmpty()) {
                logger.warning("地图文件 " + file.getName() + " 里灾难 " + disasterId
                        + " 的固定落点全部无坐标，已忽略。");
                continue;
            }
            anchors.put(disasterId, points);
        }
        return anchors;
    }

    private static MapPoint fromMap(Map<?, ?> entry, String world) {
        Double x = number(entry.get("x"));
        Double y = number(entry.get("y"));
        Double z = number(entry.get("z"));
        if (x == null || y == null || z == null) {
            return null;
        }
        Object name = entry.get("name");
        return new MapPoint(world, name == null ? "" : String.valueOf(name),
                x, y, z, numberOrZero(entry.get("yaw")), numberOrZero(entry.get("pitch")));
    }

    private static Map<String, Object> toMap(MapPoint point) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!point.name().isBlank()) {
            map.put("name", point.name());
        }
        map.put("x", point.x());
        map.put("y", point.y());
        map.put("z", point.z());
        if (point.yaw() != 0f || point.pitch() != 0f) {
            map.put("yaw", point.yaw());
            map.put("pitch", point.pitch());
        }
        return map;
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static float numberOrZero(Object value) {
        return value instanceof Number number ? number.floatValue() : 0f;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String baseName(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }
}
