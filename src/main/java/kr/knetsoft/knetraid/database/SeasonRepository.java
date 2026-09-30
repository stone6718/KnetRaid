package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.FactionSeasonStatEntry;
import kr.knetsoft.knetraid.model.SeasonData;
import kr.knetsoft.knetraid.model.SeasonStatEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * knetraid_seasons / knetraid_season_stats 테이블에 대한 CRUD.
 * 모든 메서드는 비동기 스레드에서 호출해야 한다.
 * SQLite와 MariaDB가 각기 다른 UPSERT 문법을 쓰므로, 통계 증가는
 * "조회 후 있으면 UPDATE, 없으면 INSERT" 방식으로 두 엔진 모두에서 동작하게 처리한다.
 */
public class SeasonRepository {

    private final DatabaseManager databaseManager;

    public SeasonRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public SeasonData createSeason(String name, long startedAt) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO knetraid_seasons (id, name, started_at, ended_at) VALUES (?, ?, ?, NULL)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setLong(3, startedAt);
            statement.executeUpdate();
        }
        return new SeasonData(id, name, startedAt, null);
    }

    public void endSeason(String seasonId, long endedAt) throws SQLException {
        String sql = "UPDATE knetraid_seasons SET ended_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, endedAt);
            statement.setString(2, seasonId);
            statement.executeUpdate();
        }
    }

    public Optional<SeasonData> findActiveSeason() throws SQLException {
        String sql = "SELECT id, name, started_at, ended_at FROM knetraid_seasons WHERE ended_at IS NULL "
                + "ORDER BY started_at DESC LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return Optional.of(mapSeason(resultSet));
            }
        }
        return Optional.empty();
    }

    public Optional<SeasonData> findById(String id) throws SQLException {
        String sql = "SELECT id, name, started_at, ended_at FROM knetraid_seasons WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapSeason(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public int countSeasons() throws SQLException {
        String sql = "SELECT COUNT(*) FROM knetraid_seasons";
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public void addKill(String seasonId, UUID player) throws SQLException {
        incrementStat(seasonId, player, "kills", 1);
    }

    public void addDeath(String seasonId, UUID player) throws SQLException {
        incrementStat(seasonId, player, "deaths", 1);
    }

    public void addRaid(String seasonId, UUID player) throws SQLException {
        incrementStat(seasonId, player, "raids", 1);
    }

    public void addBountyEarned(String seasonId, UUID player, double amount) throws SQLException {
        incrementStat(seasonId, player, "bounty_earned", amount);
    }

    public Optional<SeasonStatEntry> findStat(String seasonId, UUID player) throws SQLException {
        String sql = "SELECT player_uuid, kills, deaths, raids, bounty_earned FROM knetraid_season_stats "
                + "WHERE season_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, seasonId);
            statement.setString(2, player.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapStat(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<SeasonStatEntry> topByKills(String seasonId, int limit) throws SQLException {
        return topBy(seasonId, "kills", limit);
    }

    public List<SeasonStatEntry> topByRaids(String seasonId, int limit) throws SQLException {
        return topBy(seasonId, "raids", limit);
    }

    public List<SeasonStatEntry> topByBountyEarned(String seasonId, int limit) throws SQLException {
        return topBy(seasonId, "bounty_earned", limit);
    }

    /**
     * 현재 세력원 기준으로 시즌 킬 수를 합산한 세력 랭킹. 과거 탈퇴한 세력원의 기록은
     * 반영되지 않는 단순화된 집계이다(세력 이동 이력까지 추적하지는 않는다).
     */
    public List<FactionSeasonStatEntry> topFactionsByKills(String seasonId, int limit) throws SQLException {
        String sql = "SELECT f.id, f.name, SUM(s.kills) AS total_kills "
                + "FROM knetraid_season_stats s "
                + "JOIN knetraid_faction_members m ON s.player_uuid = m.player_uuid "
                + "JOIN knetraid_factions f ON m.faction_id = f.id "
                + "WHERE s.season_id = ? "
                + "GROUP BY f.id, f.name ORDER BY total_kills DESC LIMIT ?";
        List<FactionSeasonStatEntry> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, seasonId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new FactionSeasonStatEntry(
                            resultSet.getString(1), resultSet.getString(2), resultSet.getLong(3)));
                }
            }
        }
        return result;
    }

    private List<SeasonStatEntry> topBy(String seasonId, String column, int limit) throws SQLException {
        String sql = "SELECT player_uuid, kills, deaths, raids, bounty_earned FROM knetraid_season_stats "
                + "WHERE season_id = ? ORDER BY " + column + " DESC LIMIT ?";
        List<SeasonStatEntry> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, seasonId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapStat(resultSet));
                }
            }
        }
        return result;
    }

    private void incrementStat(String seasonId, UUID player, String column, double delta) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean exists;
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM knetraid_season_stats WHERE season_id = ? AND player_uuid = ?")) {
                    check.setString(1, seasonId);
                    check.setString(2, player.toString());
                    try (ResultSet resultSet = check.executeQuery()) {
                        exists = resultSet.next();
                    }
                }
                if (!exists) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO knetraid_season_stats (season_id, player_uuid, kills, deaths, raids, bounty_earned) "
                                    + "VALUES (?, ?, 0, 0, 0, 0)")) {
                        insert.setString(1, seasonId);
                        insert.setString(2, player.toString());
                        insert.executeUpdate();
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE knetraid_season_stats SET " + column + " = " + column + " + ? "
                                + "WHERE season_id = ? AND player_uuid = ?")) {
                    update.setDouble(1, delta);
                    update.setString(2, seasonId);
                    update.setString(3, player.toString());
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private SeasonData mapSeason(ResultSet resultSet) throws SQLException {
        long endedAtRaw = resultSet.getLong("ended_at");
        Long endedAt = resultSet.wasNull() ? null : endedAtRaw;
        return new SeasonData(
                resultSet.getString("id"),
                resultSet.getString("name"),
                resultSet.getLong("started_at"),
                endedAt
        );
    }

    private SeasonStatEntry mapStat(ResultSet resultSet) throws SQLException {
        return new SeasonStatEntry(
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getInt("kills"),
                resultSet.getInt("deaths"),
                resultSet.getInt("raids"),
                resultSet.getDouble("bounty_earned")
        );
    }
}
