package com.xcreate.disaster.storage.mysql;

import com.xcreate.disaster.api.storage.AchievementEntry;
import com.xcreate.disaster.api.storage.MatchParticipant;
import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.MatchRecord;
import com.xcreate.disaster.api.storage.PlayerDelta;
import com.xcreate.disaster.api.storage.PlayerStats;
import com.xcreate.disaster.api.storage.ReputationEntry;
import com.xcreate.disaster.api.storage.StatField;
import com.xcreate.disaster.api.storage.StorageProvider;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.storage.AsyncStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MySQL / MariaDB 存储。
 *
 * <p>连接走 HikariCP，驱动是 MariaDB Connector/J（连 MySQL 协议）。这里刻意不设
 * {@code driverClassName}——打包时驱动被 relocate 到了别的包名下，写死类名会加载不到；
 * 交给 {@code DriverManager} 从 URL 前缀找驱动反而更稳。</p>
 *
 * <p>初始化失败直接抛异常，让上层降级到 YAML。数据库挂了不能连累服务器起不来。</p>
 */
public final class MysqlStorageProvider implements StorageProvider {

    private static final String ID = "mysql";

    private static final String PLAYER_COLUMNS =
            "player_id, player_name, matches, wins, deaths, "
                    + "total_survival, best_survival, reputation, updated_at";

    private final PluginConfig.Storage config;
    private final AsyncStorage async;

    private HikariDataSource dataSource;
    private volatile boolean available;

    public MysqlStorageProvider(PluginConfig.Storage config, Logger logger) {
        this.config = config;
        this.async = new AsyncStorage("ds-mysql", config.poolSize(), logger);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return available && dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void init() throws SQLException {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("disaster-storage");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(config.connectionTimeoutMs());
        // 库不存在时顺手建一个；没这个权限的话会在下面的连接阶段直接报错
        hikari.addDataSourceProperty("createDatabaseIfNotExist", "true");

        this.dataSource = new HikariDataSource(hikari);
        MysqlSchema.create(dataSource, config.tablePrefix());
        this.available = true;
    }

    @Override
    public void close() {
        available = false;
        async.close();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public CompletableFuture<PlayerStats> loadStats(UUID playerId) {
        return async.supply(() -> queryOne(
                "SELECT " + PLAYER_COLUMNS + " FROM " + table("players") + " WHERE player_id = ?",
                MysqlStorageProvider::readStats,
                playerId.toString())
                .orElseGet(() -> PlayerStats.empty(playerId, null)));
    }

    @Override
    public CompletableFuture<Void> applyDelta(UUID playerId, String playerName, PlayerDelta delta) {
        return async.run(() -> execute("""
                INSERT INTO %s (player_id, player_name, matches, wins, deaths,
                                total_survival, best_survival, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name    = VALUES(player_name),
                    matches        = matches + VALUES(matches),
                    wins           = wins + VALUES(wins),
                    deaths         = deaths + VALUES(deaths),
                    total_survival = total_survival + VALUES(total_survival),
                    best_survival  = GREATEST(best_survival, VALUES(best_survival)),
                    updated_at     = VALUES(updated_at)
                """.formatted(table("players")),
                playerId.toString(), playerName,
                delta.matches(), delta.wins(), delta.deaths(), delta.survivalSeconds(),
                delta.survivalSeconds(), System.currentTimeMillis()));
    }

    @Override
    public CompletableFuture<List<PlayerStats>> topPlayers(StatField field, int limit) {
        return async.supply(() -> queryList(
                "SELECT " + PLAYER_COLUMNS + " FROM " + table("players")
                        + " ORDER BY " + orderColumn(field) + " DESC LIMIT ?",
                MysqlStorageProvider::readStats,
                Math.max(0, limit)));
    }

    @Override
    public CompletableFuture<Void> saveMatch(MatchRecord record) {
        return async.run(() -> writeMatch(record));
    }

    /** 一局和它的参与者算一组写入，走同一个事务，免得留下只有局没有人的半截记录。 */
    private void writeMatch(MatchRecord record) {
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (match_id, map_id, room_id, server_id,
                                    started_at, ended_at, duration, disasters)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        map_id     = VALUES(map_id),
                        room_id    = VALUES(room_id),
                        server_id  = VALUES(server_id),
                        started_at = VALUES(started_at),
                        ended_at   = VALUES(ended_at),
                        duration   = VALUES(duration),
                        disasters  = VALUES(disasters)
                    """.formatted(table("matches")))) {
                statement.setString(1, record.matchId());
                statement.setString(2, record.mapId());
                statement.setString(3, record.roomId());
                statement.setString(4, record.serverId());
                statement.setLong(5, record.startedAtMillis());
                statement.setLong(6, record.endedAtMillis());
                statement.setInt(7, record.durationSeconds());
                statement.setString(8, join(record.disasterIds()));
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (match_id, player_id, player_name, survived, survival)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        player_name = VALUES(player_name),
                        survived    = VALUES(survived),
                        survival    = VALUES(survival)
                    """.formatted(table("match_players")))) {
                for (MatchParticipant participant : record.participants()) {
                    statement.setString(1, record.matchId());
                    statement.setString(2, participant.playerId().toString());
                    statement.setString(3, participant.playerName());
                    statement.setBoolean(4, participant.survived());
                    statement.setLong(5, participant.survivalSeconds());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<MatchRecord>> findMatch(String matchId) {
        return async.supply(() -> Optional.ofNullable(readMatch(matchId)));
    }

    @Override
    public CompletableFuture<List<MatchRecord>> recentMatches(UUID playerId, int limit) {
        return async.supply(() -> {
            List<String> ids = queryList(
                    "SELECT m.match_id FROM " + table("matches") + " m"
                            + " JOIN " + table("match_players") + " p ON p.match_id = m.match_id"
                            + " WHERE p.player_id = ? ORDER BY m.started_at DESC LIMIT ?",
                    rs -> rs.getString(1),
                    playerId.toString(), Math.max(0, limit));

            List<MatchRecord> result = new ArrayList<>(ids.size());
            for (String id : ids) {
                MatchRecord record = readMatch(id);
                if (record != null) {
                    result.add(record);
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveRating(MatchRating rating) {
        return async.supply(() -> insert("""
                INSERT INTO %s (match_id, rater_id, stars, tags, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(table("ratings")),
                rating.matchId(), rating.raterId().toString(), rating.stars(),
                join(rating.issueTags()), rating.createdAtMillis()));
    }

    @Override
    public CompletableFuture<List<MatchRating>> ratingsOf(String matchId) {
        return async.supply(() -> queryList(
                "SELECT rater_id, stars, tags, created_at FROM " + table("ratings")
                        + " WHERE match_id = ?",
                rs -> new MatchRating(matchId, UUID.fromString(rs.getString(1)), rs.getInt(2),
                        split(rs.getString(3)), rs.getLong(4)),
                matchId));
    }

    @Override
    public CompletableFuture<Optional<MatchRating>> ownRating(String matchId, UUID raterId) {
        return async.supply(() -> queryOne(
                "SELECT rater_id, stars, tags, created_at FROM " + table("ratings")
                        + " WHERE match_id = ? AND rater_id = ?",
                rs -> new MatchRating(matchId, raterId, rs.getInt(2),
                        split(rs.getString(3)), rs.getLong(4)),
                matchId, raterId.toString()));
    }

    @Override
    public CompletableFuture<Boolean> saveReputation(ReputationEntry entry) {
        return async.supply(() -> inTransaction(connection -> {
            boolean inserted = insert(connection, """
                    INSERT INTO %s (match_id, from_id, to_id, tags, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.formatted(table("reputation")),
                    entry.matchId(), entry.fromId().toString(), entry.toId().toString(),
                    join(entry.tags()), entry.createdAtMillis());
            if (!inserted) {
                return false;
            }

            // 收到的荣誉值就地累加，查询时不用回头扫明细表
            execute(connection, """
                    INSERT INTO %s (player_id, reputation, updated_at)
                    VALUES (?, 1, ?)
                    ON DUPLICATE KEY UPDATE
                        reputation = reputation + 1,
                        updated_at = VALUES(updated_at)
                    """.formatted(table("players")),
                    entry.toId().toString(), System.currentTimeMillis());
            return true;
        }));
    }

    @Override
    public CompletableFuture<Long> reputationOf(UUID playerId) {
        return async.supply(() -> queryOne(
                "SELECT reputation FROM " + table("players") + " WHERE player_id = ?",
                rs -> rs.getLong(1),
                playerId.toString()).orElse(0L));
    }

    @Override
    public CompletableFuture<Optional<Long>> lastReputationAt(UUID fromId, UUID toId) {
        return async.supply(() -> queryOne(
                "SELECT MAX(created_at) FROM " + table("reputation")
                        + " WHERE from_id = ? AND to_id = ?",
                rs -> rs.getLong(1),
                fromId.toString(), toId.toString()).filter(value -> value > 0L));
    }

    @Override
    public CompletableFuture<List<AchievementEntry>> loadAchievements(UUID playerId) {
        return async.supply(() -> queryList(
                "SELECT achievement_id, progress, unlocked, updated_at FROM "
                        + table("achievements") + " WHERE player_id = ?",
                rs -> new AchievementEntry(playerId, rs.getString(1), rs.getInt(2),
                        rs.getBoolean(3), rs.getLong(4)),
                playerId.toString()));
    }

    @Override
    public CompletableFuture<Void> saveAchievement(AchievementEntry entry) {
        return async.run(() -> execute("""
                INSERT INTO %s (player_id, achievement_id, progress, unlocked, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    progress   = VALUES(progress),
                    unlocked   = VALUES(unlocked),
                    updated_at = VALUES(updated_at)
                """.formatted(table("achievements")),
                entry.playerId().toString(), entry.achievementId(),
                entry.progress(), entry.unlocked(), entry.updatedAtMillis()));
    }

    private MatchRecord readMatch(String matchId) {
        Optional<Object[]> head = queryOne(
                "SELECT map_id, room_id, server_id, started_at, ended_at, duration, disasters"
                        + " FROM " + table("matches") + " WHERE match_id = ?",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getInt(6), rs.getString(7)},
                matchId);
        if (head.isEmpty()) {
            return null;
        }

        List<MatchParticipant> participants = queryList(
                "SELECT player_id, player_name, survived, survival, death_cause FROM "
                        + table("match_players") + " WHERE match_id = ?",
                rs -> new MatchParticipant(
                        UUID.fromString(rs.getString(1)), rs.getString(2),
                        rs.getBoolean(3), rs.getLong(4), rs.getString(5)),
                matchId);

        Object[] row = head.get();
        return new MatchRecord(
                matchId,
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (Long) row[3],
                (Long) row[4],
                (Integer) row[5],
                split((String) row[6]),
                participants);
    }

    private static PlayerStats readStats(ResultSet rs) throws SQLException {
        return new PlayerStats(
                UUID.fromString(rs.getString(1)),
                rs.getString(2),
                rs.getInt(3),
                rs.getInt(4),
                rs.getInt(5),
                rs.getLong(6),
                rs.getLong(7),
                rs.getLong(8),
                rs.getLong(9));
    }

    private static String orderColumn(StatField field) {
        return switch (field) {
            case WINS -> "wins";
            case MATCHES -> "matches";
            case BEST_SURVIVAL_SECONDS -> "best_survival";
            case REPUTATION -> "reputation";
        };
    }

    private <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw fail(sql, ex);
        }
    }

    private <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = queryList(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, sql, params);
        } catch (SQLException ex) {
            throw fail(sql, ex);
        }
    }

    private static void execute(Connection connection, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    /** 返回 false 表示撞了唯一键，也就是这条记录已经存在。 */
    private boolean insert(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return insert(connection, sql, params);
        } catch (SQLException ex) {
            throw fail(sql, ex);
        }
    }

    private static boolean insert(Connection connection, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            if (isDuplicateKey(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw fail("事务", ex);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    /** 唯一键冲突在 SQL 标准里属于 23 类，MySQL 与 MariaDB 都遵守。 */
    private static boolean isDuplicateKey(SQLException ex) {
        String state = ex.getSQLState();
        return state != null && state.startsWith("23");
    }

    private static RuntimeException fail(String sql, SQLException ex) {
        return new IllegalStateException(
                "执行 SQL 失败: " + sql.replaceAll("\\s+", " ") + " — " + ex.getMessage(), ex);
    }

    /** 表名一律带配置里的前缀，同一个库里放多份数据也不会撞名。 */
    private String table(String name) {
        return config.tablePrefix() + name;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(","));
    }

    private static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split(","));
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
