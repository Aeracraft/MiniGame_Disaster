package com.xcreate.disaster.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * config.yml 的强类型视图。
 *
 * <p>所有取值都带默认值，不抛异常——配置写坏了应该降级运行并告警，而不是让服务器起不来。</p>
 */
public final class PluginConfig {

    private final FileConfiguration yaml;
    private final Storage storage;
    private final Maps maps;
    private final Rooms rooms;
    private final Game game;
    private final Disasters disasters;
    private final Afk afk;
    private final Webhook webhook;
    private final Permission permission;
    private final boolean debug;

    private PluginConfig(FileConfiguration yaml) {
        this.yaml = yaml;
        this.storage = new Storage(yaml);
        this.maps = new Maps(yaml);
        this.rooms = new Rooms(yaml);
        this.game = new Game(yaml);
        this.disasters = new Disasters(yaml);
        this.afk = new Afk(yaml);
        this.webhook = new Webhook(yaml);
        this.permission = new Permission(yaml);
        this.debug = yaml.getBoolean("logging.debug", false);
    }

    public static PluginConfig parse(FileConfiguration yaml) {
        return new PluginConfig(yaml);
    }

    public Storage storage() {
        return storage;
    }

    public Maps maps() {
        return maps;
    }

    public Rooms rooms() {
        return rooms;
    }

    public Game game() {
        return game;
    }

    public Disasters disasters() {
        return disasters;
    }

    public Afk afk() {
        return afk;
    }

    public Webhook webhook() {
        return webhook;
    }

    public Permission permission() {
        return permission;
    }

    public boolean debug() {
        return debug;
    }

    /** 底层配置对象，给尚未纳入强类型视图的字段用。 */
    public FileConfiguration raw() {
        return yaml;
    }

    public static final class Storage {

        private final StorageType type;
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;
        private final String tablePrefix;
        private final int poolSize;
        private final int connectionTimeoutMs;

        private Storage(FileConfiguration yaml) {
            this.type = StorageType.parse(yaml.getString("storage.type"));
            this.host = yaml.getString("storage.mysql.host", "127.0.0.1");
            this.port = yaml.getInt("storage.mysql.port", 3306);
            this.database = yaml.getString("storage.mysql.database", "disaster");
            this.username = yaml.getString("storage.mysql.username", "root");
            this.password = yaml.getString("storage.mysql.password", "");
            this.tablePrefix = yaml.getString("storage.mysql.table-prefix", "ds_");
            this.poolSize = yaml.getInt("storage.mysql.pool-size", 6);
            this.connectionTimeoutMs = yaml.getInt("storage.mysql.connection-timeout-ms", 5000);
        }

        public StorageType type() {
            return type;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public String database() {
            return database;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public String tablePrefix() {
            return tablePrefix;
        }

        public int poolSize() {
            return poolSize;
        }

        public int connectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        /** 走 MariaDB 驱动连 MySQL 协议。 */
        public String jdbcUrl() {
            return "jdbc:mariadb://" + host + ":" + port + "/" + database;
        }
    }

    public static final class Maps {

        private final MapMode mode;
        private final boolean allowVote;
        private final boolean avoidRepeat;
        private final int avoidRepeatHistory;
        private final String fixedMap;
        private final int voteDurationSeconds;

        private Maps(FileConfiguration yaml) {
            this.mode = MapMode.parse(yaml.getString("map-selection.mode"));
            this.allowVote = yaml.getBoolean("map-selection.allow-vote", true);
            this.avoidRepeat = yaml.getBoolean("map-selection.avoid-repeat", true);
            this.avoidRepeatHistory = yaml.getInt("map-selection.avoid-repeat-history", 3);
            this.fixedMap = yaml.getString("map-selection.fixed-map", "");
            this.voteDurationSeconds = yaml.getInt("map-selection.vote-duration-seconds", 20);
        }

        public MapMode mode() {
            return mode;
        }

        public boolean allowVote() {
            return allowVote;
        }

        public boolean avoidRepeat() {
            return avoidRepeat;
        }

        /** 记住最近多少张用过的图，避免连抽。 */
        public int avoidRepeatHistory() {
            return avoidRepeatHistory;
        }

        /** 仅 {@link MapMode#FIXED} 模式下生效。 */
        public String fixedMap() {
            return fixedMap == null ? "" : fixedMap;
        }

        public int voteDurationSeconds() {
            return voteDurationSeconds;
        }
    }

    public static final class Rooms {

        private final int maxRooms;
        private final int idleTimeoutSeconds;
        private final int reapIntervalSeconds;
        private final int queueLimit;

        private Rooms(FileConfiguration yaml) {
            this.maxRooms = Math.max(1, yaml.getInt("rooms.max-rooms", 4));
            this.idleTimeoutSeconds = Math.max(0, yaml.getInt("rooms.idle-timeout-seconds", 300));
            this.reapIntervalSeconds = Math.max(1, yaml.getInt("rooms.reap-interval-seconds", 30));
            this.queueLimit = Math.max(0, yaml.getInt("rooms.queue-limit", 100));
        }

        public int maxRooms() {
            return maxRooms;
        }

        public int idleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        /** 0 表示不自动回收，房间只能手动关。 */
        public long idleTimeoutMillis() {
            return idleTimeoutSeconds * 1000L;
        }

        public int reapIntervalSeconds() {
            return reapIntervalSeconds;
        }

        /** 排队人数上限，0 表示不限。 */
        public int queueLimit() {
            return queueLimit;
        }
    }

    public static final class Game {

        private final int minPlayers;
        private final int maxPlayers;
        private final int prepareSeconds;
        private final int waveIntervalSeconds;
        private final int primaryPerWave;
        private final double secondaryDisasterChance;
        private final boolean naturalRegeneration;
        private final boolean fallDamage;
        private final int maxHealth;
        private final boolean keepInventory;

        private Game(FileConfiguration yaml) {
            this.minPlayers = yaml.getInt("game.min-players", 4);
            this.maxPlayers = yaml.getInt("game.max-players", 16);
            this.prepareSeconds = yaml.getInt("game.prepare-seconds", 10);
            this.waveIntervalSeconds = yaml.getInt("game.wave-interval-seconds", 60);
            this.primaryPerWave = Math.max(1, yaml.getInt("game.primary-per-wave", 1));
            this.secondaryDisasterChance = yaml.getDouble("game.secondary-disaster-chance", 0.5);
            this.naturalRegeneration = yaml.getBoolean("game.modifiers.natural-regeneration", false);
            this.fallDamage = yaml.getBoolean("game.modifiers.fall-damage", true);
            this.maxHealth = yaml.getInt("game.modifiers.max-health", 20);
            this.keepInventory = yaml.getBoolean("game.modifiers.keep-inventory", false);
        }

        public int minPlayers() {
            return minPlayers;
        }

        public int maxPlayers() {
            return maxPlayers;
        }

        public int prepareSeconds() {
            return prepareSeconds;
        }

        public int waveIntervalSeconds() {
            return waveIntervalSeconds;
        }

        public double secondaryDisasterChance() {
            return secondaryDisasterChance;
        }

        /** 每波必出几个主灾难。一般 1 个，调高就是混沌局。 */
        public int primaryPerWave() {
            return primaryPerWave;
        }

        public boolean naturalRegeneration() {
            return naturalRegeneration;
        }

        public boolean fallDamage() {
            return fallDamage;
        }

        public int maxHealth() {
            return maxHealth;
        }

        public boolean keepInventory() {
            return keepInventory;
        }
    }

    public static final class Disasters {

        private final long randomSeed;
        private final double weightCap;
        private final int lightningWarningTicks;
        private final int minDistanceFromSpawn;
        private final int minDistanceBetweenPoints;
        private final int maxRetries;

        private Disasters(FileConfiguration yaml) {
            this.randomSeed = yaml.getLong("disaster.random-seed", -1L);
            this.weightCap = yaml.getDouble("disaster.weight-cap", 1.75);
            this.lightningWarningTicks = yaml.getInt("disaster.lightning-warning-ticks", 30);
            this.minDistanceFromSpawn = yaml.getInt("disaster.spawn-validation.min-distance-from-spawn", 8);
            this.minDistanceBetweenPoints = yaml.getInt("disaster.spawn-validation.min-distance-between-points", 3);
            this.maxRetries = yaml.getInt("disaster.spawn-validation.max-retries", 20);
        }

        /** -1 表示每局随机，固定值用于复现同一局。 */
        public long randomSeed() {
            return randomSeed;
        }

        public boolean hasFixedSeed() {
            return randomSeed >= 0;
        }

        /** 掷骰时权重按这个上限截断，0 或负数表示不限制。 */
        public double weightCap() {
            return weightCap;
        }

        public int lightningWarningTicks() {
            return lightningWarningTicks;
        }

        public int minDistanceFromSpawn() {
            return minDistanceFromSpawn;
        }

        public int minDistanceBetweenPoints() {
            return minDistanceBetweenPoints;
        }

        public int maxRetries() {
            return maxRetries;
        }
    }

    public static final class Afk {

        private final boolean enabled;
        private final int warnThresholdSeconds;
        private final int actionThresholdSeconds;

        private Afk(FileConfiguration yaml) {
            this.enabled = yaml.getBoolean("afk-detection.enabled", true);
            this.warnThresholdSeconds = yaml.getInt("afk-detection.warn-threshold-seconds", 60);
            this.actionThresholdSeconds = yaml.getInt("afk-detection.action-threshold-seconds", 120);
        }

        public boolean enabled() {
            return enabled;
        }

        public int warnThresholdSeconds() {
            return warnThresholdSeconds;
        }

        public int actionThresholdSeconds() {
            return actionThresholdSeconds;
        }
    }

    public static final class Permission {

        private final int cacheTtlSeconds;

        private Permission(FileConfiguration yaml) {
            this.cacheTtlSeconds = Math.max(0, yaml.getInt("permission.cache-ttl-seconds", 10));
        }

        /** 权限快照缓存有效期（秒），0 表示不缓存。 */
        public int cacheTtlSeconds() {
            return cacheTtlSeconds;
        }
    }

    public static final class Webhook {

        private final boolean enabled;
        private final String url;
        private final String secret;
        private final int timeoutMs;
        private final List<String> events;

        private Webhook(FileConfiguration yaml) {
            this.enabled = yaml.getBoolean("webhook.enabled", false);
            this.url = yaml.getString("webhook.url", "");
            this.secret = yaml.getString("webhook.secret", "");
            this.timeoutMs = yaml.getInt("webhook.timeout-ms", 5000);
            this.events = List.copyOf(yaml.getStringList("webhook.events"));
        }

        public boolean enabled() {
            return enabled;
        }

        /** 开关打开且地址非空才算真正可用。 */
        public boolean usable() {
            return enabled && url != null && !url.isBlank();
        }

        public String url() {
            return url;
        }

        public String secret() {
            return secret;
        }

        public int timeoutMs() {
            return timeoutMs;
        }

        public List<String> events() {
            return events;
        }
    }
}
