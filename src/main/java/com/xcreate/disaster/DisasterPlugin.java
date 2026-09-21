package com.xcreate.disaster;

import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.command.DisasterCommand;
import com.xcreate.disaster.compat.Platform;
import com.xcreate.disaster.compat.ServerVersion;
import com.xcreate.disaster.config.MessageService;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.storage.StorageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件入口。
 *
 * <p>1.20.4 起，Spigot 打底、探测到 Paper 时启用增强。跨版本易碎点一律走
 * {@code compat} 包，主逻辑不直接静态引用。</p>
 *
 * <p>数据只出不进——不监听任何端口，只把对局数据推到服主自建的 webhook 服务。</p>
 */
public final class DisasterPlugin extends JavaPlugin {

    private ServerVersion serverVersion;
    private Platform platform;
    private PluginConfig pluginConfig;
    private MessageService messages;
    private StorageManager storage;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();

        // 先探明运行环境，版本不够就别继续了
        this.serverVersion = ServerVersion.detect();
        this.platform = Platform.detect();

        getLogger().info("运行环境: " + platform.brand());
        getLogger().info("Minecraft " + serverVersion
                + "（最低支持 " + ServerVersion.MIN_SUPPORTED + "）");

        if (!serverVersion.isSupported()) {
            getLogger().severe("当前服务端版本低于最低支持版本 " + ServerVersion.MIN_SUPPORTED
                    + "，Disaster 不会启用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (platform.isFolia()) {
            getLogger().warning("检测到 Folia 系调度模型。本插件未适配 Folia，"
                    + "世界操作与调度行为可能不符合预期。");
        }

        saveDefaultConfig();
        this.pluginConfig = PluginConfig.parse(getConfig());
        this.messages = new MessageService(this);

        this.storage = new StorageManager(this, pluginConfig.storage());
        this.storage.start();

        registerCommands();

        getLogger().info("Disaster 已启用，耗时 " + (System.currentTimeMillis() - startedAt) + " ms。");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        getLogger().info("Disaster 已停用。");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("disaster");
        if (command == null) {
            getLogger().warning("plugin.yml 中未声明 disaster 命令，/ds 将不可用。");
            return;
        }
        DisasterCommand executor = new DisasterCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** 重载配置与消息文件，由 {@code /ds reload} 触发。 */
    public void reloadAll() {
        reloadConfig();
        this.pluginConfig = PluginConfig.parse(getConfig());
        this.messages = new MessageService(this);
    }

    /**
     * 取已注册的回放引擎，没有可用实现时返回 {@code null}，调用方据此静默降级。
     *
     * <p>依赖方向单向——本插件只查服务，不认识任何具体回放实现。</p>
     */
    public ReplayProvider replayProvider() {
        RegisteredServiceProvider<ReplayProvider> registration =
                getServer().getServicesManager().getRegistration(ReplayProvider.class);
        if (registration == null) {
            return null;
        }
        ReplayProvider provider = registration.getProvider();
        if (provider == null || !provider.available()) {
            return null;
        }
        return provider;
    }

    public ServerVersion serverVersion() {
        return serverVersion;
    }

    public Platform platform() {
        return platform;
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public MessageService messages() {
        return messages;
    }

    public StorageManager storage() {
        return storage;
    }
}
