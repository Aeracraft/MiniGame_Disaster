package com.xcreate.disaster;

import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.command.DisasterCommand;
import com.xcreate.disaster.compat.Platform;
import com.xcreate.disaster.compat.ServerVersion;
import com.xcreate.disaster.config.MessageService;
import com.xcreate.disaster.config.PluginConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Disaster 主类。
 *
 * <h2>兼容策略</h2>
 * Minecraft 1.20.4 及以上，Spigot 打底，探测到 Paper 时启用增强。
 * 所有跨版本易碎点必须经由 {@code compat} 包处理，主逻辑禁止直接静态引用。
 *
 * <h2>外发数据方向</h2>
 * 本插件<b>只出不进</b>：不监听任何端口，只把对局数据推送到服主自建的 webhook 服务。
 */
public final class DisasterPlugin extends JavaPlugin {

    private ServerVersion serverVersion;
    private Platform platform;
    private PluginConfig pluginConfig;
    private MessageService messages;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();

        // ---- 1. 兼容层：先探明运行环境，再决定要不要继续 ----
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

        // ---- 2. 配置 ----
        saveDefaultConfig();
        this.pluginConfig = PluginConfig.parse(getConfig());
        this.messages = new MessageService(this);

        // ---- 3. 命令 ----
        registerCommands();

        // ---- 4. 服务注册骨架 ----
        // 回放契约只做查询，没有 provider 时静默跳过，不报错。
        // 对局评价的 ReputationProvider 将沿用同一套机制。

        // ---- 里程碑 M1 到此为止 ----
        // 后续：存储层（yaml / mysql）、地图定义与标点、房间管理、
        //       灾难注册与权重掷骰、落点校验链、对局评价、webhook 上报。
        getLogger().info("Disaster 已启用，耗时 " + (System.currentTimeMillis() - startedAt) + " ms。");
    }

    @Override
    public void onDisable() {
        // 后续里程碑在此处收尾：停止全部房间、落盘上报队列、关闭连接池。
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

    /** 重载配置与消息文件。由 {@code /ds reload} 调用。 */
    public void reloadAll() {
        reloadConfig();
        this.pluginConfig = PluginConfig.parse(getConfig());
        this.messages = new MessageService(this);
    }

    /**
     * 取得已注册的回放引擎。
     *
     * <p>依赖方向是单向的：本插件只做服务查询，不认识任何具体回放实现。
     * 返回 {@code null} 表示没有可用实现，调用方必须据此静默降级。</p>
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
}
