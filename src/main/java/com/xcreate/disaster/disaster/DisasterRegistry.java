package com.xcreate.disaster.disaster;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全部灾种定义的注册表。
 *
 * <p>首次运行时把内置定义释放到数据目录，之后以副本文件为准——服主改权重、关掉某个灾种
 * 都直接改文件。缺失的内置文件会补回来，所以删掉一个文件等于恢复默认，而不是让灾种消失。</p>
 */
public final class DisasterRegistry {

    private static final String DISASTERS_DIR = "disasters";

    private JavaPlugin plugin;
    private final Logger logger;
    private final File folder;
    private final DisasterRepository repository;
    private final Map<String, DisasterDefinition> definitions = new ConcurrentHashMap<>();

    public DisasterRegistry(JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), DISASTERS_DIR), plugin.getLogger());
        this.plugin = plugin;
    }

    /** 不带插件的那一份，用来在测试里直接喂一个目录。 */
    public DisasterRegistry(File folder, Logger logger) {
        this.logger = logger;
        this.folder = folder;
        this.repository = new DisasterRepository(folder, logger);
    }

    /** 释放内置文件后扫描目录，返回载入的灾种数量。 */
    public int reload() {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.severe("无法创建灾种目录 " + folder + "，灾难系统不可用。");
            definitions.clear();
            return 0;
        }
        releaseBuiltins();

        List<DisasterDefinition> loaded = repository.loadAll();
        definitions.clear();
        for (DisasterDefinition definition : loaded) {
            definitions.put(key(definition.id()), definition);
        }
        warnMissingBuiltins();
        return definitions.size();
    }

    public Collection<DisasterDefinition> all() {
        return definitions.values();
    }

    /** id 不区分大小写，服主手敲时大小写常错。 */
    public Optional<DisasterDefinition> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(definitions.get(key(id)));
    }

    /** 某一层级的全部灾种，按 id 排序。 */
    public List<DisasterDefinition> byTier(DisasterTier tier) {
        List<DisasterDefinition> result = new ArrayList<>();
        for (DisasterDefinition definition : definitions.values()) {
            if (definition.tier() == tier) {
                result.add(definition);
            }
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return result;
    }

    /**
     * 可掷骰的候选池。
     *
     * @param excluded 已激活或本局已经用过的灾种 id，持续型灾难不该被重复掷中
     */
    public List<DisasterDefinition> pool(DisasterTier tier, Set<String> excluded) {
        Set<String> skip = new HashSet<>();
        if (excluded != null) {
            for (String id : excluded) {
                if (id != null) {
                    skip.add(key(id));
                }
            }
        }
        List<DisasterDefinition> result = new ArrayList<>();
        for (DisasterDefinition definition : byTier(tier)) {
            if (definition.rolls() && !skip.contains(key(definition.id()))) {
                result.add(definition);
            }
        }
        return result;
    }

    public File folder() {
        return folder;
    }

    /** 写入定义并更新内存。文件写失败会抛出去，由调用方决定怎么报。 */
    public void save(DisasterDefinition definition) throws IOException {
        repository.save(definition);
        definitions.put(key(definition.id()), definition);
    }

    /** 内置 id 一个都没载入通常意味着目录被清空了，值得提一句。 */
    private void warnMissingBuiltins() {
        int missing = 0;
        for (String id : BuiltinDisasters.IDS) {
            if (!definitions.containsKey(key(id))) {
                missing++;
            }
        }
        if (missing == BuiltinDisasters.IDS.size()) {
            logger.severe("灾种目录里没有任何内置灾种，灾难掷骰将无池可选。");
        } else if (missing > 0) {
            logger.warning("有 " + missing + " 个内置灾种未能载入，相关灾难本局不会出现。");
        }
    }

    private void releaseBuiltins() {
        writeIfMissing(BuiltinDisasters.EXAMPLE_FILE);
        for (String id : BuiltinDisasters.IDS) {
            writeIfMissing(id + ".yml");
        }
    }

    private void writeIfMissing(String fileName) {
        File target = new File(folder, fileName);
        if (target.isFile()) {
            return;
        }
        try {
            plugin.saveResource("disasters/" + fileName, false);
        } catch (Throwable t) {
            logger.log(Level.FINE, "释放灾种文件 " + fileName + " 失败。", t);
        }
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
