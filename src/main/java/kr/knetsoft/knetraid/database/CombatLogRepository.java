package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.CombatLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * knetraid_combat_logs 테이블 기록. 모든 메서드는 비동기 스레드에서 호출해야 한다.
 */
public class CombatLogRepository {

    private final DatabaseManager databaseManager;

    public CombatLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(CombatLogEntry entry) throws SQLException {
        String sql = "INSERT INTO knetraid_combat_logs (id, player_uuid, opponent_uuid, event_type, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.id());
            statement.setString(2, entry.playerUuid().toString());
            statement.setString(3, entry.opponentUuid() != null ? entry.opponentUuid().toString() : null);
            statement.setString(4, entry.eventType().name());
            statement.setLong(5, entry.occurredAt());
            statement.executeUpdate();
        }
    }
}
