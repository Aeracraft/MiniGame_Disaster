package com.xcreate.disaster.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全部地图定义的注册表。
 *
 * <p>内存里留一份定义，抽图与开局直接读它。文件写入是同步的——地图文件只有几 KB，
 * 而且改地图是管理员偶尔为之的动作，不进游戏的 tick 路径，没必要为它开线程池。</p>
 */
public final class MapRegistry {

    /** 地图定义放在插件数据目录的这个子目录下。 */
    private static final String MAPS_DIR = "maps";

    /** 首次运行释放的样例文件，带全字段注释，也是地图文件的字段说明。 */
    private static final String EXAMPLE_RESOURCE = "maps/_example.yml";

    private static final String EXAMPLE_FILE = "_example.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File folder;
    private final MapRepository repository;
    private final Map<String, MapDefinition> definitions = new ConcurrentHashMap<>();

    public MapRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.folder = new File(plugin.getDataFolder(), MAPS_DIR);
        this.repository = new MapRepository(folder, logger);
    }

    /** 扫描目录载入全部定义，返回成功载入的数量。 */
    public int reload() {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.severe("无法创建地图目录 " + folder + "，地图功能不可用。");
            definitions.clear();
            return 0;
        }
        writeExampleIfMissing();

        List<MapDefinition> loaded = repository.loadAll();
        definitions.clear();
        for (MapDefinition definition : loaded) {
            definitions.put(key(definition.id()), definition);
            List<MapIssue> issues = MapCheck.check(definition);
            if (!definition.playable()) {
                logger.warning("地图 " + definition.id() + " 尚不能开局：" + firstError(issues));
            } else if (!issues.isEmpty()) {
                logger.info("地图 " + definition.id() + " 已载入，有 " + issues.size() + " 条提示。");
            }
        }
        return definitions.size();
    }

    public Collection<MapDefinition> all() {
        return definitions.values();
    }

    /** id 不区分大小写，服主手敲时大小写常错。 */
    public Optional<MapDefinition> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(definitions.get(key(id)));
    }

    public boolean exists(String id) {
        return id != null && definitions.containsKey(key(id));
    }

    /** 可开局的地图，按 id 排序，抽图与列表都读这一份。 */
    public List<MapDefinition> playable() {
        List<MapDefinition> result = new ArrayList<>();
        for (MapDefinition definition : definitions.values()) {
            if (definition.playable()) {
                result.add(definition);
            }
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return result;
    }

    /** 写入定义并更新内存。文件写失败会抛出去，由命令层告诉管理员。 */
    public void save(MapDefinition definition) throws IOException {
        repository.save(definition);
        definitions.put(key(definition.id()), definition);
    }

    public boolean delete(String id) {
        boolean removed = repository.delete(id);
        definitions.remove(key(id));
        return removed;
    }

    /** 模板世界目录。副本地图就是从这个目录拷出来的。 */
    public File templateFolder(MapDefinition definition) {
        return new File(Bukkit.getWorldContainer(), definition.templateFolder());
    }

    public boolean templateExists(MapDefinition definition) {
        return new File(templateFolder(definition), "level.dat").isFile();
    }

    /**
     * 取当前已加载的模板世界，没加载则返回 null。
     *
     * <p>作图时模板世界通常是加载着的；开局则由 {@link #templateFolder} 拷出副本，
     * 不依赖它是否已加载。</p>
     */
    public World loadedTemplateWorld(MapDefinition definition) {
        World world = Bukkit.getWorld(definition.world());
        return world != null ? world : Bukkit.getWorld(definition.templateFolder());
    }

    private void writeExampleIfMissing() {
        File example = new File(folder, EXAMPLE_FILE);
        if (example.isFile()) {
            return;
        }
        try {
            plugin.saveResource(EXAMPLE_RESOURCE, false);
        } catch (Throwable t) {
            logger.log(Level.FINE, "释放地图样例文件失败。", t);
        }
    }

    private static String firstError(List<MapIssue> issues) {
        for (MapIssue issue : issues) {
            if (issue.isError()) {
                return issue.message();
            }
        }
        return "原因未知";
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
