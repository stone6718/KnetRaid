package kr.knetsoft.knetraid.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * 버전 기반 스키마 마이그레이션 실행기.
 * knetraid_schema_version 테이블에 현재 스키마 버전을 기록하고,
 * 아직 적용되지 않은 버전의 DDL만 순서대로 실행한다.
 * 새 단계에서 테이블/컬럼이 추가되면 이 클래스에 V2, V3 ... 순서로 이어 붙인다.
 */
public class SchemaMigrator {

    private static final List<String> V1_INITIAL_SCHEMA = List.of(
            """
            CREATE TABLE IF NOT EXISTS knetraid_players (
                uuid TEXT PRIMARY KEY,
                nickname TEXT NOT NULL,
                first_join_at BIGINT NOT NULL,
                last_join_at BIGINT NOT NULL,
                last_quit_at BIGINT
            )
            """,
            "CREATE INDEX idx_knetraid_players_nickname ON knetraid_players (nickname)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_factions (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                tag TEXT NOT NULL UNIQUE,
                leader_uuid TEXT NOT NULL,
                created_at BIGINT NOT NULL
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS knetraid_faction_members (
                player_uuid TEXT PRIMARY KEY,
                faction_id TEXT NOT NULL,
                member_role TEXT NOT NULL,
                joined_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX idx_knetraid_faction_members_faction ON knetraid_faction_members (faction_id)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_faction_relations (
                faction_id TEXT NOT NULL,
                related_faction_id TEXT NOT NULL,
                relation TEXT NOT NULL,
                PRIMARY KEY (faction_id, related_faction_id)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS knetraid_bounties (
                id TEXT PRIMARY KEY,
                target_uuid TEXT NOT NULL,
                issuer_uuid TEXT NOT NULL,
                amount BIGINT NOT NULL,
                status TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT,
                claimed_by_uuid TEXT,
                claimed_at BIGINT
            )
            """,
            "CREATE INDEX idx_knetraid_bounties_target ON knetraid_bounties (target_uuid, status)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_bases (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                owner_uuid TEXT NOT NULL,
                faction_id TEXT,
                world TEXT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                registered_at BIGINT NOT NULL,
                last_raid_at BIGINT
            )
            """,
            "CREATE INDEX idx_knetraid_bases_owner ON knetraid_bases (owner_uuid)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_raid_logs (
                id TEXT PRIMARY KEY,
                base_id TEXT,
                actor_uuid TEXT NOT NULL,
                action_type TEXT NOT NULL,
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                occurred_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX idx_knetraid_raid_logs_base ON knetraid_raid_logs (base_id, occurred_at)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_combat_logs (
                id TEXT PRIMARY KEY,
                player_uuid TEXT NOT NULL,
                opponent_uuid TEXT,
                event_type TEXT NOT NULL,
                occurred_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX idx_knetraid_combat_logs_player ON knetraid_combat_logs (player_uuid, occurred_at)",

            """
            CREATE TABLE IF NOT EXISTS knetraid_seasons (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                started_at BIGINT NOT NULL,
                ended_at BIGINT
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS knetraid_season_stats (
                season_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                kills INTEGER NOT NULL DEFAULT 0,
                deaths INTEGER NOT NULL DEFAULT 0,
                raids INTEGER NOT NULL DEFAULT 0,
                bounty_earned BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (season_id, player_uuid)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS knetraid_currency_transactions (
                id TEXT PRIMARY KEY,
                player_uuid TEXT NOT NULL,
                amount BIGINT NOT NULL,
                reason TEXT NOT NULL,
                occurred_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX idx_knetraid_currency_tx_player ON knetraid_currency_transactions (player_uuid, occurred_at)"
    );

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public SchemaMigrator(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /**
     * 아직 적용되지 않은 스키마 버전을 순서대로 적용한다.
     *
     * @return 성공 여부
     */
    public boolean migrate() {
        try (Connection connection = databaseManager.getConnection()) {
            ensureVersionTable(connection);
            int current = readVersion(connection);

            if (current < 1) {
                applyVersion(connection, 1, V1_INITIAL_SCHEMA);
                logger.info("데이터베이스 스키마를 버전 1로 초기화했습니다.");
            }
            return true;
        } catch (SQLException exception) {
            logger.severe("데이터베이스 스키마 마이그레이션 중 오류가 발생했습니다: " + exception.getMessage());
            return false;
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS knetraid_schema_version (version INTEGER NOT NULL)");
        }
    }

    private int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM knetraid_schema_version LIMIT 1")) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }

    private void applyVersion(Connection connection, int version, List<String> statements) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.executeUpdate("DELETE FROM knetraid_schema_version");
            statement.executeUpdate("INSERT INTO knetraid_schema_version (version) VALUES (" + version + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }
}
