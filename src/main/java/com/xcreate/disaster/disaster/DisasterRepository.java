package com.xcreate.disaster.disaster;

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
 * 灾种定义文件（{@code disasters/<id>.yml}）的读写。
 *
 * <p>和地图文件一样，读取时对缺键、类型写错一律容忍：读不动的那一项退回默认值，
 * 一个文件写坏不能让插件起不来。文件名决定 id，文件里写的 {@code id} 只作参考。</p>
 */
public final class DisasterRepository {

    /** 下划线开头的文件不参与加载，用来放样例与备份。 */
    static final String IGNORED_PREFIX = "_";

    private static final String EXTENSION = ".yml";

    private final File folder;
    private final Logger logger;

    public DisasterRepository(File folder, Logger logger) {
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

    /** 扫一遍目录。单个文件读失败只记一条日志，不影响其他灾种。 */
    public List<DisasterDefinition> loadAll() {
        List<DisasterDefinition> definitions = new ArrayList<>();
        for (String id : listIds()) {
            try {
                read(fileOf(id)).ifPresent(definitions::add);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "灾种文件 " + id + EXTENSION + " 读取失败，已跳过。", t);
            }
        }
        return definitions;
    }

    public void save(DisasterDefinition definition) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "灾种定义。改完执行 /ds reload 生效。",
                "id 以文件名为准，改这里的 id 无效。",
                "spawn-strategy 取 NONE / RANDOM_IN_BOUNDS / NEAR_PLAYER / HIGH_POINTS /",
                "  AROUND_EACH_PLAYER / FROM_EDGE / AT_MAP_CENTER。",
                "point-count 是本体一次产生几个落点；AROUND_EACH_PLAYER 时表示每人几个。",
                "options 是各灾种自己的参数，见 _example.yml。"));

        yaml.set("id", definition.id());
        yaml.set("display-name", definition.displayName());
        yaml.set("tier", definition.tier().name());
        yaml.set("enabled", definition.enabled());
        yaml.set("weight", definition.weight());
        yaml.set("spawn-strategy", definition.strategy().name());
        yaml.set("point-count", definition.pointCount());
        if (!definition.options().isEmpty()) {
            yaml.set("options", definition.options().raw());
        }

        yaml.save(fileOf(definition.id()));
    }

    private Optional<DisasterDefinition> read(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = baseName(file);

        String declared = yaml.getString("id");
        if (declared != null && !declared.equalsIgnoreCase(id)) {
            logger.warning("灾种文件 " + file.getName() + " 里写的 id（" + declared
                    + "）与文件名不符，以文件名为准。");
        }

        return Optional.of(new DisasterDefinition(
                id,
                yaml.getString("display-name", id),
                DisasterTier.parse(yaml.getString("tier")),
                yaml.getBoolean("enabled", true),
                yaml.getDouble("weight", 1.0),
                SpawnStrategy.parse(yaml.getString("spawn-strategy")),
                yaml.getInt("point-count", 0),
                DisasterOptions.of(readOptions(yaml.getConfigurationSection("options")))));
    }

    private static Map<String, Object> readOptions(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static String baseName(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }
}
