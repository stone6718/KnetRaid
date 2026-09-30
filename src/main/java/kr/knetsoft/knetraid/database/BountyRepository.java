package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.BountyData;
import kr.knetsoft.knetraid.model.BountyRankEntry;
import kr.knetsoft.knetraid.model.BountyStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * knetraid_bounties 테이블에 대한 CRUD. 모든 메서드는 비동기 스레드에서 호출해야 한다.
 * 여러 이슈어가 같은 대상에게 건 현상금은 각각 별도 행(ACTIVE)으로 쌓이며,
 * 조회/처치 시 합산해서 다룬다("현상금 누적").
 */
public class BountyRepository {

    private static final String SELECT_COLUMNS = "id, target_uuid, issuer_uuid, amount, status, created_at, expires_at, claimed_by_uuid, claimed_at";

    private final DatabaseManager databaseManager;

    public BountyRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public BountyData create(UUID target, UUID issuer, double amount, long now, Long expiresAt) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO knetraid_bounties (id, target_uuid, issuer_uuid, amount, status, created_at, expires_at, claimed_by_uuid, claimed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, target.toString());
            statement.setString(3, issuer.toString());
            statement.setDouble(4, amount);
            statement.setString(5, BountyStatus.ACTIVE.name());
            statement.setLong(6, now);
            if (expiresAt != null) {
                statement.setLong(7, expiresAt);
            } else {
                statement.setNull(7, java.sql.Types.BIGINT);
            }
            statement.executeUpdate();
        }
        return new BountyData(id, target, issuer, amount, BountyStatus.ACTIVE, now, expiresAt, null, null);
    }

    public double sumActiveByTarget(UUID target) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM knetraid_bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble(1) : 0;
            }
        }
    }

    public int countActiveContributors(UUID target) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT issuer_uuid) FROM knetraid_bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public List<BountyData> listActiveByIssuerAndTarget(UUID issuer, UUID target) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM knetraid_bounties "
                + "WHERE issuer_uuid = ? AND target_uuid = ? AND status = 'ACTIVE'";
        return query(sql, issuer.toString(), target.toString());
    }

    public List<BountyData> listActiveByTarget(UUID target) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM knetraid_bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        return query(sql, target.toString());
    }

    public List<BountyData> listExpiredActive(long now) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM knetraid_bounties "
                + "WHERE status = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BountyData> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
                return result;
            }
        }
    }

    public List<BountyRankEntry> topTargetsByActiveTotal(int limit) throws SQLException {
        String sql = "SELECT target_uuid, SUM(amount) AS total, COUNT(DISTINCT issuer_uuid) AS contributors "
                + "FROM knetraid_bounties WHERE status = 'ACTIVE' GROUP BY target_uuid ORDER BY total DESC LIMIT ?";
        List<BountyRankEntry> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new BountyRankEntry(
                            UUID.fromString(resultSet.getString("target_uuid")),
                            resultSet.getDouble("total"),
                            resultSet.getInt("contributors")
                    ));
                }
            }
        }
        return result;
    }

    /**
     * 지정한 행 id들을 CANCELLED로 표시한다. 환불은 호출부에서 각 행의 issuer/amount로 처리한다.
     */
    public void markCancelled(List<String> ids) throws SQLException {
        updateStatus(ids, BountyStatus.CANCELLED);
    }

    public void markExpired(List<String> ids) throws SQLException {
        updateStatus(ids, BountyStatus.EXPIRED);
    }

    /**
     * 대상에 걸린 모든 ACTIVE 현상금을 한 번에 CLAIMED로 표시한다(처치 보상 지급).
     * status='ACTIVE' 조건으로만 갱신하므로, 이미 처리된 현상금이 중복 지급되지 않는다.
     */
    public List<BountyData> claimActiveForTarget(UUID target, UUID killer, long now) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<BountyData> toClaim = new ArrayList<>();
                String selectSql = "SELECT " + SELECT_COLUMNS + " FROM knetraid_bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setString(1, target.toString());
                    try (ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            toClaim.add(map(resultSet));
                        }
                    }
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE knetraid_bounties SET status = 'CLAIMED', claimed_by_uuid = ?, claimed_at = ? "
                                + "WHERE id = ? AND status = 'ACTIVE'")) {
                    for (BountyData bounty : toClaim) {
                        update.setString(1, killer.toString());
                        update.setLong(2, now);
                        update.setString(3, bounty.id());
                        update.addBatch();
                    }
                    update.executeBatch();
                }
                connection.commit();
                return toClaim;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void updateStatus(List<String> ids, BountyStatus status) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        String sql = "UPDATE knetraid_bounties SET status = ? WHERE id = ? AND status = 'ACTIVE'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String id : ids) {
                statement.setString(1, status.name());
                statement.setString(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<BountyData> query(String sql, String... params) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BountyData> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
                return result;
            }
        }
    }

    private BountyData map(ResultSet resultSet) throws SQLException {
        long expiresAtRaw = resultSet.getLong("expires_at");
        Long expiresAt = resultSet.wasNull() ? null : expiresAtRaw;
        String claimedByRaw = resultSet.getString("claimed_by_uuid");
        UUID claimedBy = claimedByRaw != null ? UUID.fromString(claimedByRaw) : null;
        long claimedAtRaw = resultSet.getLong("claimed_at");
        Long claimedAt = resultSet.wasNull() ? null : claimedAtRaw;

        return new BountyData(
                resultSet.getString("id"),
                UUID.fromString(resultSet.getString("target_uuid")),
                UUID.fromString(resultSet.getString("issuer_uuid")),
                resultSet.getDouble("amount"),
                BountyStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("created_at"),
                expiresAt,
                claimedBy,
                claimedAt
        );
    }
}
