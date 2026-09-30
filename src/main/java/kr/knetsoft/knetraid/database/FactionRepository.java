package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.FactionData;
import kr.knetsoft.knetraid.model.FactionMember;
import kr.knetsoft.knetraid.model.FactionRelationType;
import kr.knetsoft.knetraid.model.FactionRelationView;
import kr.knetsoft.knetraid.model.FactionRole;

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
 * knetraid_factions / knetraid_faction_members / knetraid_faction_relations 테이블에 대한 CRUD.
 * 모든 메서드는 JDBC 블로킹 호출이므로 반드시 비동기 스레드에서 호출해야 한다.
 */
public class FactionRepository {

    private final DatabaseManager databaseManager;

    public FactionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<FactionData> findByName(String name) throws SQLException {
        String sql = "SELECT id, name, tag, leader_uuid, created_at FROM knetraid_factions WHERE LOWER(name) = LOWER(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapFaction(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<FactionData> findById(String id) throws SQLException {
        String sql = "SELECT id, name, tag, leader_uuid, created_at FROM knetraid_factions WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapFaction(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<FactionMember> findMember(UUID playerUuid) throws SQLException {
        String sql = "SELECT player_uuid, faction_id, member_role, joined_at FROM knetraid_faction_members WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapMember(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<FactionData> findFactionOfPlayer(UUID playerUuid) throws SQLException {
        String sql = "SELECT f.id, f.name, f.tag, f.leader_uuid, f.created_at "
                + "FROM knetraid_faction_members m JOIN knetraid_factions f ON m.faction_id = f.id "
                + "WHERE m.player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapFaction(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<FactionMember> listMembers(String factionId) throws SQLException {
        String sql = "SELECT player_uuid, faction_id, member_role, joined_at FROM knetraid_faction_members "
                + "WHERE faction_id = ? ORDER BY joined_at ASC";
        List<FactionMember> members = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, factionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapMember(resultSet));
                }
            }
        }
        return members;
    }

    public int countMembers(String factionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM knetraid_faction_members WHERE faction_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, factionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public List<FactionData> listAll() throws SQLException {
        String sql = "SELECT id, name, tag, leader_uuid, created_at FROM knetraid_factions ORDER BY name ASC";
        List<FactionData> factions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                factions.add(mapFaction(resultSet));
            }
        }
        return factions;
    }

    /**
     * 세력을 생성하고 생성자를 세력장으로 등록한다. 두 작업을 하나의 트랜잭션으로 처리한다.
     */
    public void createFactionWithLeader(FactionData faction, long joinedAt) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insertFaction = connection.prepareStatement(
                        "INSERT INTO knetraid_factions (id, name, tag, leader_uuid, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    insertFaction.setString(1, faction.getId());
                    insertFaction.setString(2, faction.getName());
                    insertFaction.setString(3, faction.getTag());
                    insertFaction.setString(4, faction.getLeaderUuid().toString());
                    insertFaction.setLong(5, faction.getCreatedAt());
                    insertFaction.executeUpdate();
                }
                try (PreparedStatement insertMember = connection.prepareStatement(
                        "INSERT INTO knetraid_faction_members (player_uuid, faction_id, member_role, joined_at) VALUES (?, ?, ?, ?)")) {
                    insertMember.setString(1, faction.getLeaderUuid().toString());
                    insertMember.setString(2, faction.getId());
                    insertMember.setString(3, FactionRole.LEADER.name());
                    insertMember.setLong(4, joinedAt);
                    insertMember.executeUpdate();
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

    public void addMember(String factionId, UUID playerUuid, FactionRole role, long joinedAt) throws SQLException {
        String sql = "INSERT INTO knetraid_faction_members (player_uuid, faction_id, member_role, joined_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, factionId);
            statement.setString(3, role.name());
            statement.setLong(4, joinedAt);
            statement.executeUpdate();
        }
    }

    public void removeMember(UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM knetraid_faction_members WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateMemberRole(UUID playerUuid, FactionRole role) throws SQLException {
        String sql = "UPDATE knetraid_faction_members SET member_role = ? WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateLeader(String factionId, UUID newLeaderUuid) throws SQLException {
        String sql = "UPDATE knetraid_factions SET leader_uuid = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newLeaderUuid.toString());
            statement.setString(2, factionId);
            statement.executeUpdate();
        }
    }

    /**
     * 세력과 소속 세력원, 관련된 동맹/적대 관계를 모두 삭제한다(세력 해체).
     */
    public void deleteFactionCascade(String factionId) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteRelations = connection.prepareStatement(
                        "DELETE FROM knetraid_faction_relations WHERE faction_id = ? OR related_faction_id = ?")) {
                    deleteRelations.setString(1, factionId);
                    deleteRelations.setString(2, factionId);
                    deleteRelations.executeUpdate();
                }
                try (PreparedStatement deleteMembers = connection.prepareStatement(
                        "DELETE FROM knetraid_faction_members WHERE faction_id = ?")) {
                    deleteMembers.setString(1, factionId);
                    deleteMembers.executeUpdate();
                }
                try (PreparedStatement deleteFaction = connection.prepareStatement(
                        "DELETE FROM knetraid_factions WHERE id = ?")) {
                    deleteFaction.setString(1, factionId);
                    deleteFaction.executeUpdate();
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

    public List<FactionRelationView> listRelations(String factionId) throws SQLException {
        String sql = "SELECT r.relation, f.name FROM knetraid_faction_relations r "
                + "JOIN knetraid_factions f ON r.related_faction_id = f.id "
                + "WHERE r.faction_id = ? ORDER BY f.name ASC";
        List<FactionRelationView> relations = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, factionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    relations.add(new FactionRelationView(
                            FactionRelationType.valueOf(resultSet.getString(1)),
                            resultSet.getString(2)
                    ));
                }
            }
        }
        return relations;
    }

    public Optional<FactionRelationType> findRelation(String factionId, String otherFactionId) throws SQLException {
        String sql = "SELECT relation FROM knetraid_faction_relations WHERE faction_id = ? AND related_faction_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, factionId);
            statement.setString(2, otherFactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(FactionRelationType.valueOf(resultSet.getString(1)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 두 세력 간 관계를 양방향으로 동일하게 기록한다(상호 일관성 유지).
     */
    public void setRelationMutual(String factionId, String otherFactionId, FactionRelationType type) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertRelation(connection, factionId, otherFactionId, type);
                upsertRelation(connection, otherFactionId, factionId, type);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void upsertRelation(Connection connection, String factionId, String otherFactionId, FactionRelationType type)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM knetraid_faction_relations WHERE faction_id = ? AND related_faction_id = ?")) {
            delete.setString(1, factionId);
            delete.setString(2, otherFactionId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO knetraid_faction_relations (faction_id, related_faction_id, relation) VALUES (?, ?, ?)")) {
            insert.setString(1, factionId);
            insert.setString(2, otherFactionId);
            insert.setString(3, type.name());
            insert.executeUpdate();
        }
    }

    private FactionData mapFaction(ResultSet resultSet) throws SQLException {
        return new FactionData(
                resultSet.getString("id"),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                UUID.fromString(resultSet.getString("leader_uuid")),
                resultSet.getLong("created_at")
        );
    }

    private FactionMember mapMember(ResultSet resultSet) throws SQLException {
        return new FactionMember(
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("faction_id"),
                FactionRole.valueOf(resultSet.getString("member_role")),
                resultSet.getLong("joined_at")
        );
    }
}
