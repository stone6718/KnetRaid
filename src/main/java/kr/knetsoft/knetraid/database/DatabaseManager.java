package kr.knetsoft.knetraid.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * database.yml 설정을 바탕으로 HikariCP 커넥션 풀을 구성하고 관리한다.
 * SQLite(기본, 단일 서버)와 MariaDB(확장, 다중 서버) 두 저장소를 지원한다.
 */
public class DatabaseManager {

    private final KnetRaid plugin;
    private HikariDataSource dataSource;
    private StorageType storageType = StorageType.SQLITE;

    public DatabaseManager(KnetRaid plugin) {
        this.plugin = plugin;
    }

    /**
     * database.yml을 읽어 커넥션 풀을 생성하고 연결을 검증한다.
     *
     * @return 연결 성공 여부
     */
    public boolean connect() {
        FileConfiguration db = plugin.getConfigManager().getModuleConfig("database.yml");
        String typeRaw = db.getString("storage.type", "SQLITE");
        try {
            storageType = StorageType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("database.yml의 storage.type 값이 올바르지 않습니다: '" + typeRaw
                    + "' (SQLITE로 대체합니다)");
            storageType = StorageType.SQLITE;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("KnetRaid-Pool");

        if (storageType == StorageType.SQLITE) {
            String fileName = db.getString("sqlite.file", "database.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite 파일은 동시 쓰기를 지원하지 않으므로 단일 커넥션으로 제한한다.
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            String host = db.getString("mariadb.host", "localhost");
            int port = db.getInt("mariadb.port", 3306);
            String database = db.getString("mariadb.database", "knetraid");
            boolean useSsl = db.getBoolean("mariadb.use-ssl", false);
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl);
            config.setUsername(db.getString("mariadb.username", "root"));
            config.setPassword(db.getString("mariadb.password", ""));
            config.setMaximumPoolSize(Math.max(1, db.getInt("mariadb.pool.maximum-pool-size", 10)));
            config.setMinimumIdle(Math.max(0, db.getInt("mariadb.pool.minimum-idle", 2)));
            config.setConnectionTimeout(Math.max(1000L, db.getLong("mariadb.pool.connection-timeout-ms", 5000L)));
            config.setMaxLifetime(Math.max(30000L, db.getLong("mariadb.pool.max-lifetime-ms", 1800000L)));
        }

        try {
            HikariDataSource newDataSource = new HikariDataSource(config);
            try (Connection connection = newDataSource.getConnection()) {
                // 연결 유효성을 즉시 검증한다.
                if (!connection.isValid(3)) {
                    throw new SQLException("연결 유효성 검사에 실패했습니다.");
                }
            }
            this.dataSource = newDataSource;
        } catch (Exception exception) {
            plugin.getLogger().severe("데이터베이스에 연결할 수 없습니다 (" + storageType + "): " + exception.getMessage());
            return false;
        }

        plugin.getLogger().info("데이터베이스에 연결되었습니다. (저장소: " + storageType + ")");
        return true;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("데이터베이스가 연결되어 있지 않습니다.");
        }
        return dataSource.getConnection();
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
