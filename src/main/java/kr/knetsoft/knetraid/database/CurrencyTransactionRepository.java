package kr.knetsoft.knetraid.database;

import kr.knetsoft.knetraid.model.CurrencyTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * knetraid_currency_transactions 테이블 기록. 모든 메서드는 비동기 스레드에서 호출해야 한다.
 */
public class CurrencyTransactionRepository {

    private final DatabaseManager databaseManager;

    public CurrencyTransactionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(CurrencyTransaction transaction) throws SQLException {
        String sql = "INSERT INTO knetraid_currency_transactions (id, player_uuid, amount, reason, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transaction.id());
            statement.setString(2, transaction.playerUuid().toString());
            statement.setDouble(3, transaction.amount());
            statement.setString(4, transaction.reason().name());
            statement.setLong(5, transaction.occurredAt());
            statement.executeUpdate();
        }
    }

    public void log(UUID player, double amount, kr.knetsoft.knetraid.model.CurrencyReason reason) throws SQLException {
        insert(new CurrencyTransaction(UUID.randomUUID().toString(), player, amount, reason, System.currentTimeMillis()));
    }
}
