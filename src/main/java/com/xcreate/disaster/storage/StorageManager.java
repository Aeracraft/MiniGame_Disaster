package com.xcreate.disaster.storage;

import com.xcreate.disaster.api.storage.StorageProvider;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.config.StorageType;
import com.xcreate.disaster.storage.mysql.MysqlStorageProvider;
import com.xcreate.disaster.storage.yaml.YamlStorageProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * 存储层的入口，负责挑后端和处理降级。
 *
 * <p>MySQL 起不来时告警并降级到本地 YAML——跨服统计退化成单服统计，总比整台服务器起不来强。
 * 两个后端都起不来时给一个空实现，插件照常跑，只是什么都不落盘。</p>
 */
public final class StorageManager implements AutoCloseable {

    private final JavaPlugin plugin;
    private final PluginConfig.Storage config;

    private StorageProvider provider = new NoopStorageProvider();
    private String degradedReason;

    public StorageManager(JavaPlugin plugin, PluginConfig.Storage config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (config.type() == StorageType.MYSQL) {
            MysqlStorageProvider mysql = new MysqlStorageProvider(config, plugin.getLogger());
            try {
                mysql.init();
                this.provider = mysql;
                plugin.getLogger().info("数据存储: MySQL " + config.host() + ":"
                        + config.port() + "/" + config.database()
                        + "（表前缀 " + config.tablePrefix() + "）");
                return;
            } catch (Throwable t) {
                mysql.close();
                this.degradedReason = describe(t);
                plugin.getLogger().log(Level.WARNING,
                        "MySQL 初始化失败，本次降级为本地存储: " + degradedReason, t);
            }
        }
        startYaml();
    }

    private void startYaml() {
        YamlStorageProvider yaml = new YamlStorageProvider(
                new File(plugin.getDataFolder(), "data"), plugin.getLogger());
        try {
            yaml.init();
            this.provider = yaml;
            plugin.getLogger().info("数据存储: 本地 YAML（" + plugin.getDataFolder() + "/data）");
        } catch (Throwable t) {
            yaml.close();
            this.degradedReason = describe(t);
            plugin.getLogger().log(Level.SEVERE,
                    "本地存储也初始化失败，本次运行不会保存任何数据: " + degradedReason, t);
        }
    }

    /** 当前生效的后端。永远不为 null，最差情况是空实现。 */
    public StorageProvider provider() {
        return provider;
    }

    public String providerId() {
        return provider.id();
    }

    public boolean degraded() {
        return degradedReason != null;
    }

    /** 降级原因，没发生过降级时为 null。 */
    public String degradedReason() {
        return degradedReason;
    }

    @Override
    public void close() {
        provider.close();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }
}
