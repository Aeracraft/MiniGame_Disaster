package com.xcreate.disaster.storage.yaml;

import com.xcreate.disaster.api.storage.AchievementEntry;
import com.xcreate.disaster.api.storage.MatchParticipant;
import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.MatchRecord;
import com.xcreate.disaster.api.storage.PlayerDelta;
import com.xcreate.disaster.api.storage.PlayerStats;
import com.xcreate.disaster.api.storage.ReputationEntry;
import com.xcreate.disaster.api.storage.StatField;
import com.xcreate.disaster.api.storage.StorageProvider;
import com.xcreate.disaster.storage.AsyncStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 本地 YAML 存储。
 *
 * <p>读写全在单线程执行器里跑，天然串行，不需要文件锁。落盘走「先写 .tmp 再替换」，
 * 中途断电最多丢掉最后一次写入，不会把原文件写坏。</p>
 *
 * <p>这个后端同时是 MySQL 起不来时的降级目标，所以刻意保持简单，不依赖任何外部东西。</p>
 */
public final class YamlStorageProvider implements StorageProvider {

    private static final String ID = "yaml";

    private final File folder;
    private final AsyncStorage async;
    private final Logger logger;

    private final YamlFile players;
    private final YamlFile matches;
    private final YamlFile ratings;
    private final YamlFile reputations;
    private final YamlFile achievements;

    private volatile boolean available;

    public YamlStorageProvider(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
        this.async = new AsyncStorage("ds-yaml", 1, logger);
        this.players = new YamlFile(new File(folder, "players.yml"), logger);
        this.matches = new YamlFile(new File(folder, "matches.yml"), logger);
        this.ratings = new YamlFile(new File(folder, "ratings.yml"), logger);
        this.reputations = new YamlFile(new File(folder, "reputation.yml"), logger);
        this.achievements = new YamlFile(new File(folder, "achievements.yml"), logger);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void init() throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("无法创建数据目录: " + folder);
        }
        players.load();
        matches.load();
        ratings.load();
        reputations.load();
        achievements.load();
        available = true;
    }

    @Override
    public void close() {
        available = false;
        async.close();
    }

    @Override
    public CompletableFuture<PlayerStats> loadStats(UUID playerId) {
        return async.supply(() -> readStats(playerId));
    }

    @Override
    public CompletableFuture<Void> applyDelta(UUID playerId, String playerName, PlayerDelta delta) {
        return async.run(() -> {
            ConfigurationSection section = players.section(key(playerId));
            section.set("name", playerName);
            section.set("matches", section.getInt("matches") + delta.matches());
            section.set("wins", section.getInt("wins") + delta.wins());
            section.set("deaths", section.getInt("deaths") + delta.deaths());
            section.set("total-survival",
                    section.getLong("total-survival") + delta.survivalSeconds());
            section.set("best-survival",
                    Math.max(section.getLong("best-survival"), delta.survivalSeconds()));
            section.set("updated-at", System.currentTimeMillis());
            save(players);
        });
    }

    @Override
    public CompletableFuture<List<PlayerStats>> topPlayers(StatField field, int limit) {
        return async.supply(() -> readAllStats().stream()
                .sorted(Comparator.comparingLong((PlayerStats stats) -> statOf(stats, field)).reversed())
                .limit(Math.max(0, limit))
                .toList());
    }

    @Override
    public CompletableFuture<Void> saveMatch(MatchRecord record) {
        return async.run(() -> {
            ConfigurationSection section = matches.section(record.matchId());
            section.set("map", record.mapId());
            section.set("room", record.roomId());
            section.set("server", record.serverId());
            section.set("started-at", record.startedAtMillis());
            section.set("ended-at", record.endedAtMillis());
            section.set("duration", record.durationSeconds());
            section.set("disasters", record.disasterIds());

            for (MatchParticipant participant : record.participants()) {
                ConfigurationSection node = section.createSection(
                        "players." + participant.playerId());
                node.set("name", participant.playerName());
                node.set("survived", participant.survived());
                node.set("survival", participant.survivalSeconds());
                node.set("death-cause", participant.deathCause());
            }
            save(matches);
        });
    }

    @Override
    public CompletableFuture<Optional<MatchRecord>> findMatch(String matchId) {
        return async.supply(() -> Optional.ofNullable(readMatch(matchId)));
    }

    @Override
    public CompletableFuture<List<MatchRecord>> recentMatches(UUID playerId, int limit) {
        return async.supply(() -> {
            List<MatchRecord> found = new ArrayList<>();
            for (String matchId : matches.config().getKeys(false)) {
                MatchRecord record = readMatch(matchId);
                if (record == null) {
                    continue;
                }
                boolean involved = record.participants().stream()
                        .anyMatch(p -> playerId.equals(p.playerId()));
                if (involved) {
                    found.add(record);
                }
            }
            found.sort(Comparator.comparingLong(MatchRecord::startedAtMillis).reversed());
            return found.stream().limit(Math.max(0, limit)).toList();
        });
    }

    @Override
    public CompletableFuture<Boolean> saveRating(MatchRating rating) {
        return async.supply(() -> {
            String path = rating.matchId() + "." + rating.raterId();
            if (ratings.config().isConfigurationSection(path)) {
                return false;
            }
            ConfigurationSection section = ratings.section(rating.matchId() + "." + rating.raterId());
            section.set("stars", rating.stars());
            section.set("tags", rating.issueTags());
            section.set("created-at", rating.createdAtMillis());
            save(ratings);
            return true;
        });
    }

    @Override
    public CompletableFuture<List<MatchRating>> ratingsOf(String matchId) {
        return async.supply(() -> {
            ConfigurationSection root = ratings.config().getConfigurationSection(matchId);
            if (root == null) {
                return List.of();
            }
            List<MatchRating> result = new ArrayList<>();
            for (String raw : root.getKeys(false)) {
                MatchRating rating = readRating(matchId, parseUuid(raw));
                if (rating != null) {
                    result.add(rating);
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Optional<MatchRating>> ownRating(String matchId, UUID raterId) {
        return async.supply(() -> Optional.ofNullable(readRating(matchId, raterId)));
    }

    @Override
    public CompletableFuture<Boolean> saveReputation(ReputationEntry entry) {
        return async.supply(() -> {
            String path = entry.matchId() + "." + entry.fromId() + "." + entry.toId();
            if (reputations.config().isConfigurationSection(path)) {
                return false;
            }
            ConfigurationSection section = reputations.section(path);
            section.set("tags", entry.tags());
            section.set("created-at", entry.createdAtMillis());
            save(reputations);

            // 收到的荣誉值就地累加到玩家统计，查询时不用回头扫全表。
            // 明细先落盘、计数器后落盘——计数器是派生数据，丢了也能从明细重算。
            ConfigurationSection target = players.section(key(entry.toId()));
            target.set("reputation", target.getLong("reputation") + 1L);
            target.set("updated-at", System.currentTimeMillis());
            save(players);
            return true;
        });
    }

    @Override
    public CompletableFuture<Long> reputationOf(UUID playerId) {
        return async.supply(() -> readStats(playerId).reputation());
    }

    @Override
    public CompletableFuture<Optional<Long>> lastReputationAt(UUID fromId, UUID toId) {
        return async.supply(() -> {
            long latest = -1L;
            for (String matchId : reputations.config().getKeys(false)) {
                ConfigurationSection from = reputations.config()
                        .getConfigurationSection(matchId + "." + fromId);
                if (from == null) {
                    continue;
                }
                ConfigurationSection node = from.getConfigurationSection(toId.toString());
                if (node != null) {
                    latest = Math.max(latest, node.getLong("created-at"));
                }
            }
            return latest < 0 ? Optional.<Long>empty() : Optional.of(latest);
        });
    }

    @Override
    public CompletableFuture<List<AchievementEntry>> loadAchievements(UUID playerId) {
        return async.supply(() -> {
            ConfigurationSection root = achievements.config().getConfigurationSection(key(playerId));
            if (root == null) {
                return List.of();
            }
            List<AchievementEntry> result = new ArrayList<>();
            for (String achievementId : root.getKeys(false)) {
                ConfigurationSection node = root.getConfigurationSection(achievementId);
                if (node == null) {
                    continue;
                }
                result.add(new AchievementEntry(playerId, achievementId,
                        node.getInt("progress"), node.getBoolean("unlocked"),
                        node.getLong("updated-at")));
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> saveAchievement(AchievementEntry entry) {
        return async.run(() -> {
            ConfigurationSection node = achievements.section(
                    key(entry.playerId()) + "." + entry.achievementId());
            node.set("progress", entry.progress());
            node.set("unlocked", entry.unlocked());
            node.set("updated-at", entry.updatedAtMillis());
            save(achievements);
        });
    }

    private PlayerStats readStats(UUID playerId) {
        ConfigurationSection section = players.config().getConfigurationSection(key(playerId));
        if (section == null) {
            return PlayerStats.empty(playerId, null);
        }
        return new PlayerStats(
                playerId,
                section.getString("name"),
                section.getInt("matches"),
                section.getInt("wins"),
                section.getInt("deaths"),
                section.getLong("total-survival"),
                section.getLong("best-survival"),
                section.getLong("reputation"),
                section.getLong("updated-at"));
    }

    private List<PlayerStats> readAllStats() {
        List<PlayerStats> result = new ArrayList<>();
        for (String raw : players.config().getKeys(false)) {
            UUID playerId = parseUuid(raw);
            if (playerId != null) {
                result.add(readStats(playerId));
            }
        }
        return result;
    }

    private MatchRecord readMatch(String matchId) {
        ConfigurationSection section = matches.config().getConfigurationSection(matchId);
        if (section == null) {
            return null;
        }

        List<MatchParticipant> participants = new ArrayList<>();
        ConfigurationSection playersSection = section.getConfigurationSection("players");
        if (playersSection != null) {
            for (String raw : playersSection.getKeys(false)) {
                UUID playerId = parseUuid(raw);
                ConfigurationSection node = playersSection.getConfigurationSection(raw);
                if (playerId == null || node == null) {
                    continue;
                }
                participants.add(new MatchParticipant(
                        playerId,
                        node.getString("name"),
                        node.getBoolean("survived"),
                        node.getLong("survival"),
                        node.getString("death-cause")));
            }
        }

        return new MatchRecord(
                matchId,
                section.getString("map"),
                section.getString("room"),
                section.getString("server"),
                section.getLong("started-at"),
                section.getLong("ended-at"),
                section.getInt("duration"),
                section.getStringList("disasters"),
                participants);
    }

    private MatchRating readRating(String matchId, UUID raterId) {
        if (raterId == null) {
            return null;
        }
        ConfigurationSection node = ratings.config()
                .getConfigurationSection(matchId + "." + raterId);
        if (node == null) {
            return null;
        }
        return new MatchRating(matchId, raterId, node.getInt("stars"),
                node.getStringList("tags"), node.getLong("created-at"));
    }

    private void save(YamlFile file) {
        try {
            file.save();
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 " + file.fileName() + " 失败", ex);
        }
    }

    private static long statOf(PlayerStats stats, StatField field) {
        return switch (field) {
            case WINS -> stats.wins();
            case MATCHES -> stats.matches();
            case BEST_SURVIVAL_SECONDS -> stats.bestSurvivalSeconds();
            case REPUTATION -> stats.reputation();
        };
    }

    private static String key(UUID playerId) {
        return playerId.toString();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 一个 YAML 文件。取 section 用同一个入口，省得各处判断 null。 */
    private static final class YamlFile {

        private final File file;
        private final Logger logger;
        private YamlConfiguration config = new YamlConfiguration();

        YamlFile(File file, Logger logger) {
            this.file = file;
            this.logger = logger;
        }

        void load() {
            if (!file.isFile()) {
                return;
            }
            try {
                config = YamlConfiguration.loadConfiguration(file);
            } catch (Throwable t) {
                logger.warning("读取 " + file.getName() + " 失败，本次按空文件处理: " + t);
                config = new YamlConfiguration();
            }
        }

        YamlConfiguration config() {
            return config;
        }

        /** 取一个 section，不存在就建。 */
        ConfigurationSection section(String path) {
            ConfigurationSection existing = config.getConfigurationSection(path);
            return existing != null ? existing : config.createSection(path);
        }

        String fileName() {
            return file.getName();
        }

        /**
         * 先写临时文件再替换。替换失败就退回直接写——保住数据比保住原子性重要。
         */
        void save() throws IOException {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            config.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                logger.warning("替换 " + file.getName() + " 失败，改用直接写入: " + ex);
                config.save(file);
                Files.deleteIfExists(tmp.toPath());
            }
        }
    }
}
