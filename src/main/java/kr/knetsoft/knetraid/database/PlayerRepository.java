package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * knetraid_players 테이블에 대한 CRUD.
 * 모든 메서드는 JDBC 블로킹 호출이므로 반드시 비동기 스레드에서 호출해야 한다.
 */
public class PlayerRepository {

    private final DatabaseManager databaseManager;

    public PlayerRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<PlayerData> find(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, nickname, first_join_at, last_join_at, last_quit_at "
                + "FROM knetraid_players WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> findUuidByNickname(String nickname) throws SQLException {
        String sql = "SELECT uuid FROM knetraid_players WHERE LOWER(nickname) = LOWER(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nickname);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(UUID.fromString(resultSet.getString("uuid")));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 플레이어가 접속할 때 호출한다. 기존 레코드가 있으면 닉네임과 최근 접속 시각을 갱신하고,
     * 없으면 새 레코드를 생성한다. 닉네임이 바뀌어도 uuid로 동일 인물임을 식별한다.
     */
    public PlayerData upsertOnJoin(UUID uuid, String nickname) throws SQLException {
        long now = System.currentTimeMillis();
        Optional<PlayerData> existing = find(uuid);

        if (existing.isPresent()) {
            String sql = "UPDATE knetraid_players SET nickname = ?, last_join_at = ? WHERE uuid = ?";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                statement.setLong(2, now);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
            PlayerData data = existing.get();
            data.setNickname(nickname);
            data.setLastJoinAt(now);
            return data;
        }

        String sql = "INSERT INTO knetraid_players (uuid, nickname, first_join_at, last_join_at, last_quit_at) "
                + "VALUES (?, ?, ?, ?, NULL)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, nickname);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
        return new PlayerData(uuid, nickname, now, now, null);
    }

    public void updateLastQuit(UUID uuid, long timestamp) throws SQLException {
        String sql = "UPDATE knetraid_players SET last_quit_at = ? WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, timestamp);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    private PlayerData map(ResultSet resultSet) throws SQLException {
        long lastQuitAt = resultSet.getLong("last_quit_at");
        Long lastQuit = resultSet.wasNull() ? null : lastQuitAt;
        return new PlayerData(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("nickname"),
                resultSet.getLong("first_join_at"),
                resultSet.getLong("last_join_at"),
                lastQuit
        );
    }
}
