package com.xcreate.disaster.storage.mysql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 建表语句。
 *
 * <p>全部用 {@code CREATE TABLE IF NOT EXISTS}，每次启动都跑一遍，插件升级新增的表会自动补上。
 * 字段改动不做迁移，由服主自己处理——这个层次的表结构变动很少。</p>
 *
 * <p>累加字段（matches / wins / deaths / total_survival / reputation）一律用 {@code INT} 或
 * {@code BIGINT} 存整数，不用浮点，避免多台子服并发累加时出现精度误差。</p>
 */
final class MysqlSchema {

    private MysqlSchema() {
    }

    static void create(DataSource dataSource, String prefix) throws SQLException {
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS %splayers (
                    player_id      CHAR(36)     NOT NULL,
                    player_name    VARCHAR(32)  NULL,
                    matches        INT          NOT NULL DEFAULT 0,
                    wins           INT          NOT NULL DEFAULT 0,
                    deaths         INT          NOT NULL DEFAULT 0,
                    total_survival BIGINT       NOT NULL DEFAULT 0,
                    best_survival  BIGINT       NOT NULL DEFAULT 0,
                    reputation     BIGINT       NOT NULL DEFAULT 0,
                    updated_at     BIGINT       NOT NULL,
                    PRIMARY KEY (player_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS %smatches (
                    match_id   VARCHAR(64)  NOT NULL,
                    map_id     VARCHAR(64)  NULL,
                    room_id    VARCHAR(64)  NULL,
                    server_id  VARCHAR(64)  NULL,
                    started_at BIGINT       NOT NULL,
                    ended_at   BIGINT       NOT NULL,
                    duration   INT          NOT NULL,
                    disasters  VARCHAR(512) NULL,
                    PRIMARY KEY (match_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS %smatch_players (
                    match_id    VARCHAR(64) NOT NULL,
                    player_id   CHAR(36)    NOT NULL,
                    player_name VARCHAR(32) NULL,
                    survived    TINYINT(1)  NOT NULL DEFAULT 0,
                    survival    BIGINT      NOT NULL DEFAULT 0,
                    death_cause VARCHAR(64) NULL,
                    PRIMARY KEY (match_id, player_id),
                    KEY idx_match_players_player (player_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS %sratings (
                    match_id   VARCHAR(64) NOT NULL,
                    rater_id   CHAR(36)    NOT NULL,
                    stars      TINYINT     NOT NULL,
                    tags       VARCHAR(255) NULL,
                    created_at BIGINT      NOT NULL,
                    PRIMARY KEY (match_id, rater_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS %sreputation (
                    match_id   VARCHAR(64)  NOT NULL,
                    from_id    CHAR(36)     NOT NULL,
                    to_id      CHAR(36)     NOT NULL,
                    tags       VARCHAR(255) NULL,
                    created_at BIGINT       NOT NULL,
                    PRIMARY KEY (match_id, from_id, to_id),
                    KEY idx_reputation_pair (from_id, to_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS %sachievements (
                    player_id      CHAR(36)    NOT NULL,
                    achievement_id VARCHAR(64) NOT NULL,
                    progress       INT         NOT NULL DEFAULT 0,
                    unlocked       TINYINT(1)  NOT NULL DEFAULT 0,
                    updated_at     BIGINT      NOT NULL,
                    PRIMARY KEY (player_id, achievement_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String template : statements) {
                statement.execute(String.format(template, prefix));
            }
        }
    }
}
