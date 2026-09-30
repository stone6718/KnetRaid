package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.RaidActionType;
import kr.knetsoft.knetraid.model.RaidLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * knetraid_raid_logs 테이블 기록/조회. 모든 메서드는 비동기 스레드에서 호출해야 한다.
 */
public class RaidLogRepository {

    private final DatabaseManager databaseManager;

    public RaidLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(RaidLogEntry entry) throws SQLException {
        String sql = "INSERT INTO knetraid_raid_logs (id, base_id, actor_uuid, action_type, world, x, y, z, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.id());
            statement.setString(2, entry.baseId());
            statement.setString(3, entry.actorUuid().toString());
            statement.setString(4, entry.actionType().name());
            statement.setString(5, entry.world());
            statement.setInt(6, entry.x());
            statement.setInt(7, entry.y());
            statement.setInt(8, entry.z());
            statement.setLong(9, entry.occurredAt());
            statement.executeUpdate();
        }
    }

    public List<RaidLogEntry> listRecentByBase(String baseId, int limit) throws SQLException {
        String sql = "SELECT id, base_id, actor_uuid, action_type, world, x, y, z, occurred_at "
                + "FROM knetraid_raid_logs WHERE base_id = ? ORDER BY occurred_at DESC LIMIT ?";
        List<RaidLogEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, baseId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new RaidLogEntry(
                            resultSet.getString("id"),
                            resultSet.getString("base_id"),
                            UUID.fromString(resultSet.getString("actor_uuid")),
                            RaidActionType.valueOf(resultSet.getString("action_type")),
                            resultSet.getString("world"),
                            resultSet.getInt("x"),
                            resultSet.getInt("y"),
                            resultSet.getInt("z"),
                            resultSet.getLong("occurred_at")
                    ));
                }
            }
        }
        return entries;
    }
}
