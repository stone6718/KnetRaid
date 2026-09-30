package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.BaseData;

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
 * knetraid_bases 테이블에 대한 CRUD. 모든 메서드는 비동기 스레드에서 호출해야 한다.
 * 조회 쿼리는 knetraid_players와 조인하여 소유자의 first_join_at을 함께 가져온다.
 * (신규 플레이어 보호 기간 판정을 이벤트마다 별도 DB 조회 없이 할 수 있도록 BaseData에 캐시한다.)
 */
public class BaseRepository {

    private static final String SELECT_BASE = "SELECT b.id, b.name, b.owner_uuid, b.faction_id, b.world, "
            + "b.x, b.y, b.z, b.registered_at, b.last_raid_at, p.first_join_at, p.nickname "
            + "FROM knetraid_bases b JOIN knetraid_players p ON b.owner_uuid = p.uuid ";

    private final DatabaseManager databaseManager;

    public BaseRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void create(BaseData base) throws SQLException {
        String sql = "INSERT INTO knetraid_bases (id, name, owner_uuid, faction_id, world, x, y, z, registered_at, last_raid_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, base.getId());
            statement.setString(2, base.getName());
            statement.setString(3, base.getOwnerUuid().toString());
            statement.setString(4, base.getFactionId());
            statement.setString(5, base.getWorld());
            statement.setDouble(6, base.getX());
            statement.setDouble(7, base.getY());
            statement.setDouble(8, base.getZ());
            statement.setLong(9, base.getRegisteredAt());
            statement.executeUpdate();
        }
    }

    public Optional<BaseData> findByOwnerAndName(UUID ownerUuid, String name) throws SQLException {
        String sql = SELECT_BASE + "WHERE b.owner_uuid = ? AND LOWER(b.name) = LOWER(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BaseData> findById(String id) throws SQLException {
        String sql = SELECT_BASE + "WHERE b.id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<BaseData> listByOwner(UUID ownerUuid) throws SQLException {
        String sql = SELECT_BASE + "WHERE b.owner_uuid = ? ORDER BY b.registered_at ASC";
        List<BaseData> bases = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bases.add(map(resultSet));
                }
            }
        }
        return bases;
    }

    public List<BaseData> listAll() throws SQLException {
        String sql = SELECT_BASE + "ORDER BY b.registered_at ASC";
        List<BaseData> bases = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                bases.add(map(resultSet));
            }
        }
        return bases;
    }

    public void delete(String id) throws SQLException {
        String sql = "DELETE FROM knetraid_bases WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    public void updateLastRaidAt(String id, long timestamp) throws SQLException {
        String sql = "UPDATE knetraid_bases SET last_raid_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, timestamp);
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }

    private BaseData map(ResultSet resultSet) throws SQLException {
        long lastRaidAtRaw = resultSet.getLong("last_raid_at");
        Long lastRaidAt = resultSet.wasNull() ? null : lastRaidAtRaw;
        return new BaseData(
                resultSet.getString("id"),
                resultSet.getString("name"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("faction_id"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getLong("registered_at"),
                lastRaidAt,
                resultSet.getLong("first_join_at"),
                resultSet.getString("nickname")
        );
    }
}
