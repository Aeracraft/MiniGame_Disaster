package com.xcreate.disaster;

import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.api.room.RoomProvisioner;
import com.xcreate.disaster.command.DisasterRootCommand;
import com.xcreate.disaster.compat.Platform;
import com.xcreate.disaster.compat.ServerVersion;
import com.xcreate.disaster.config.MessageService;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.disaster.DisasterRegistry;
import com.xcreate.disaster.disaster.DisasterTier;
import com.xcreate.disaster.disaster.SpawnPlanner;
import com.xcreate.disaster.disaster.WaveRoller;
import com.xcreate.disaster.listener.PlayerSessionListener;
import com.xcreate.disaster.map.MapRegistry;
import com.xcreate.disaster.map.MapSelector;
import com.xcreate.disaster.permission.PermissionBridge;
import com.xcreate.disaster.permission.PermissionCache;
import com.xcreate.disaster.permission.PermissionService;
import com.xcreate.disaster.permission.TitleProvider;
import com.xcreate.disaster.room.LocalRoomProvisioner;
import com.xcreate.disaster.room.RoomManager;
import com.xcreate.disaster.storage.StorageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

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
    private MapRegistry maps;
    private MapSelector mapSelector;
    private PermissionService permissions;
    private RoomProvisioner provisioner;
    private RoomManager rooms;
    private Random matchRandom;
    private DisasterRegistry disasters;
    private SpawnPlanner spawnPlanner;
    private WaveRoller waveRoller;

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

        this.maps = new MapRegistry(this);
        this.mapSelector = new MapSelector(pluginConfig.maps());
        int loaded = maps.reload();
        getLogger().info("地图: 载入 " + loaded + " 张，其中可开局 " + maps.playable().size() + " 张。");

        this.disasters = new DisasterRegistry(this);
        int disasterCount = disasters.reload();
        getLogger().info("灾难: 载入 " + disasterCount + " 个，其中主灾 "
                + disasters.byTier(DisasterTier.PRIMARY).size() + " 个、次灾 "
                + disasters.byTier(DisasterTier.SECONDARY).size() + " 个。");

        // 随机源每局开局换一次；固定种子时整局可复现
        this.matchRandom = newMatchRandom();
        this.spawnPlanner = new SpawnPlanner(pluginConfig, matchRandom);
        this.waveRoller = new WaveRoller(disasters, pluginConfig, matchRandom);

        this.permissions = new PermissionService(
                new PermissionCache(pluginConfig.permission().cacheTtlSeconds()),
                service(PermissionBridge.class),
                service(TitleProvider.class));
        if (!permissions.bridgeId().isEmpty()) {
            getLogger().info("权限桥接: " + permissions.bridgeId());
        }

        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);

        // 有第三方注册了房间来源就用它的（跨服实现走这条路），否则用本地拷贝
        RoomProvisioner remote = service(RoomProvisioner.class);
        this.provisioner = remote != null && remote.available() ? remote : new LocalRoomProvisioner(this);
        this.rooms = new RoomManager(this, maps, mapSelector, provisioner, pluginConfig);
        this.rooms.start();

        registerCommands();

        getLogger().info("Disaster 已启用，耗时 " + (System.currentTimeMillis() - startedAt) + " ms。");
    }

    @Override
    public void onDisable() {
        // 房间要先清掉：卸载世界、删副本目录都得趁服务端还在正常跑
        if (rooms != null) {
            rooms.shutdown();
        }
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
        DisasterRootCommand executor = new DisasterRootCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** 重载配置、消息文件与地图定义，由 {@code /ds reload} 触发。 */
    public void reloadAll() {
        reloadConfig();
        this.pluginConfig = PluginConfig.parse(getConfig());
        this.messages = new MessageService(this);
        if (permissions != null) {
            permissions.applyTtl(pluginConfig.permission().cacheTtlSeconds());
        }
        if (mapSelector != null) {
            mapSelector.apply(pluginConfig.maps());
        }
        if (maps != null) {
            maps.reload();
        }
        if (disasters != null) {
            disasters.reload();
        }
        // 随机源不在这里换：重载配置不该把正在跑的对局掷骰序列打断
        if (spawnPlanner != null) {
            spawnPlanner.apply(pluginConfig);
        }
        if (waveRoller != null) {
            waveRoller.apply(pluginConfig);
        }
        if (rooms != null) {
            rooms.apply(pluginConfig);
        }
    }

    /** 每局开局取一次随机源。配置里固定了种子就复现同一局。 */
    private Random newMatchRandom() {
        return pluginConfig.disasters().hasFixedSeed()
                ? new Random(pluginConfig.disasters().randomSeed())
                : new Random();
    }

    /** 每局开局换一份随机源。固定了种子就每局都复现同一条序列，方便照着日志重演。 */
    public void reseedMatchRandom() {
        this.matchRandom = newMatchRandom();
        this.spawnPlanner = new SpawnPlanner(pluginConfig, matchRandom);
        this.waveRoller.reseed();
    }

    /**
     * 取已注册的回放引擎，没有可用实现时返回 {@code null}，调用方据此静默降级。
     *
     * <p>依赖方向单向——本插件只查服务，不认识任何具体回放实现。</p>
     */
    public ReplayProvider replayProvider() {
        ReplayProvider provider = service(ReplayProvider.class);
        return provider == null || !provider.available() ? null : provider;
    }

    /** 取第三方注册的服务，没人注册时返回 null。 */
    private <T> T service(Class<T> type) {
        RegisteredServiceProvider<T> registration =
                getServer().getServicesManager().getRegistration(type);
        return registration == null ? null : registration.getProvider();
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

    public MapRegistry maps() {
        return maps;
    }

    public MapSelector mapSelector() {
        return mapSelector;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public RoomManager rooms() {
        return rooms;
    }

    public RoomProvisioner provisioner() {
        return provisioner;
    }

    public DisasterRegistry disasters() {
        return disasters;
    }

    public SpawnPlanner spawnPlanner() {
        return spawnPlanner;
    }

    public WaveRoller waveRoller() {
        return waveRoller;
    }

    /** 对局用的随机源。固定种子时整局可复现——排查「这波怎么砸成这样」不必靠运气重演。 */
    public Random matchRandom() {
        return matchRandom;
    }
}
