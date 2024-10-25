package chuks.flatbok.fx.backend.account.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import chuks.flatbok.fx.common.account.profile.TraderAccountProfile;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;

public class TraderDB {

    private static final String DB_URL = "jdbc:sqlite:traders.db";
    private static HikariDataSource dataSource;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TraderDB.class.getName());

    // Static block to initialize HikariCP connection pool and create the table
    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setMaximumPoolSize(50);  // Set pool size
            config.setConnectionTimeout(30000);  // 30 seconds timeout
            config.setIdleTimeout(600000);  // 10 minutes idle timeout
            config.setMaxLifetime(1800000);  // 30 minutes max lifetime

            dataSource = new HikariDataSource(config);

            TraderDB.createTable();
        } catch (SQLException e) {
            logger.error("Could not initialize TraderDB", e);
        }
    }

    // Create traders table
    static public void createTable() throws SQLException {
        String sql = """
                     CREATE TABLE IF NOT EXISTS traders (
                      SN INTEGER PRIMARY KEY AUTOINCREMENT,
                      account_number INT UNIQUE,
                      account_name TEXT NOT NULL,
                      email VARCHAR(255) UNIQUE NOT NULL,
                      password TEXT NOT NULL,
                      registration_time DATETIME NOT NULL,
                      approval_time DATETIME NULL,
                      approved_by INT NULL,
                      is_active BOOL NULL,
                      is_enabled BOOL NULL,
                      closed_time DATETIME NULL
                     );""" //approvedBy_AdminID
                ;

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // Get new account ID
    public static synchronized int getNewAccountID() throws SQLException {
        String sql = "SELECT MAX(account_number) AS max_account_number FROM traders";
        int maxAccountNumber = -1;

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                maxAccountNumber = rs.getInt("max_account_number");
            }
        }

        if (maxAccountNumber == -1) {
            maxAccountNumber = 100000;
        }

        return maxAccountNumber + 1;
    }

    // Insert a trader
    static public void insertTraderRegistration(String email,
            String account_name,
            byte[] hash_password,
            long registration_time) throws SQLException {

        String sql = "INSERT INTO traders (email, account_name, password, registration_time) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            //NOTE: Account number will not be set now until after approval
            pstmt.setString(1, email);
            pstmt.setString(2, account_name);
            pstmt.setBytes(3, hash_password);
            pstmt.setLong(4, registration_time);
            pstmt.executeUpdate();
        } finally {
            Arrays.fill(hash_password, (byte) 0); // Clear sensitive data
        }
    }

    private static int countTradersBySQL(String sql) throws SQLException {
        int totalTraders = 0;
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery(sql)) {

            // Retrieve the total number of traders
            if (rs.next()) {
                totalTraders = rs.getInt("total_traders");
            }
        }

        return totalTraders;
    }

    public static int countTraders() throws SQLException {
        // SQL query to get the total number of traders
        String sql = "SELECT COUNT(*) AS total_traders"
                + " FROM traders";
        return countTradersBySQL(sql);
    }

    public static int countDeactivatedAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_traders"
                + " FROM traders"
                + " WHERE is_active = FALSE";
        return countTradersBySQL(sql);
    }

    public static int countDisabledAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_traders"
                + " FROM traders"
                + " WHERE is_enabled = FALSE";
        return countTradersBySQL(sql);
    }

    public static int countUnapprovedAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_traders"
                + " FROM traders"
                + " WHERE approval_time IS NULL";
        return countTradersBySQL(sql);
    }

    public static int countClosedAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_traders"
                + " FROM traders"
                + " WHERE closed_time IS NULL";
        return countTradersBySQL(sql);
    }

    public static List queryTraderRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM traders"
                + " LIMIT " + page_size 
                + " OFFSET " + (page_index * page_size);
        return queryTradersBySQL(sql);
    }

    public static List queryDeactivatedAccountRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM traders"
                + " WHERE is_active = FALSE"
                + " LIMIT " + page_size 
                + " OFFSET " + (page_index * page_size);
        return queryTradersBySQL(sql);
    }

    public static List queryDisabledAccountRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM traders"
                + " WHERE is_enabled = FALSE"
                + " LIMIT " + page_size
                + " OFFSET " + (page_index * page_size);
        return queryTradersBySQL(sql);
    }

    public static List queryUnapprovedAccountRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM traders"
                + " WHERE approval_time IS NULL"
                + " LIMIT  LIMIT " + page_size 
                + " OFFSET " + (page_index * page_size);
        return queryTradersBySQL(sql);
    }

    public static List queryClosedAccountRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM traders"
                + " WHERE closed_time IS NULL"
                + " LIMIT " + page_size 
                + " OFFSET " + (page_index * page_size);
        return queryTradersBySQL(sql);
    }

    static public void updateTraderAccountNumber(String email, int account_number) throws SQLException {
        String sql = "UPDATE traders SET account_number = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, account_number);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountName(String email, String account_name) throws SQLException {
        String sql = "UPDATE traders SET account_name = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, account_name);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountPassword(String email, byte[] hash_password) throws SQLException {
        String sql = "UPDATE traders SET password = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBytes(1, hash_password);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        } finally {
            Arrays.fill(hash_password, (byte) 0); // Clear sensitive data
        }
    }

    static public void updateTraderAccountEmailVerified(String email, long verified_time) throws SQLException {
        String sql = "UPDATE traders SET email_verified_time = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, verified_time);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountApproved(String email, int approved_by_admin_id) throws SQLException {
        String sql = "UPDATE traders SET approval_time = ?, approved_by = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setInt(2, approved_by_admin_id);
            pstmt.setString(3, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountEnabled(String email, boolean is_enable) throws SQLException {
        String sql = "UPDATE traders SET is_enabled = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, is_enable);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountActive(String email, boolean is_active) throws SQLException {
        String sql = "UPDATE traders SET is_active = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, is_active);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateTraderAccountClose(String email, boolean is_clsed) throws SQLException {
        String sql = "UPDATE traders SET closed_time = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    // Query all traders
    static public List<TraderAccountProfile> queryAllTraders() throws SQLException {
        String sql = "SELECT * FROM traders";
        return queryTradersBySQL(sql);
    }

    static private List<TraderAccountProfile> queryTradersBySQL(String sql) throws SQLException {

        List<TraderAccountProfile> traders = new ArrayList<>();

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                TraderAccountProfile trader = new TraderAccountProfile();
                trader.setAccountNumber(rs.getInt("account_number"));
                trader.setAccountName(rs.getString("account_name"));
                trader.setEmail(rs.getString("email"));
                trader.setPassword(rs.getBytes("password"));
                trader.setRegistrationTime(rs.getLong("registration_time"));
                trader.setApprovalTime(rs.getLong("approval_time"));
                trader.setApprovedBy(rs.getInt("approved_by"));
                trader.setActive(rs.getBoolean("is_active"));
                trader.setEnabled(rs.getBoolean("is_enabled"));
                traders.add(trader);
            }
        }
        return traders;
    }

    // Query a trader by account number
    static public TraderAccountProfile queryTraderByAccountNumber(int accountNumber) throws SQLException {
        String sql = "SELECT * FROM traders WHERE account_number = ?";
        TraderAccountProfile trader = null;

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, accountNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                trader = new TraderAccountProfile();
                trader.setAccountNumber(rs.getInt("account_number"));
                trader.setAccountName(rs.getString("account_name"));
                trader.setEmail(rs.getString("email"));
                trader.setPassword(rs.getBytes("password"));
                trader.setRegistrationTime(rs.getLong("registration_time"));
                trader.setApprovalTime(rs.getLong("approval_time"));
                trader.setApprovedBy(rs.getInt("approved_by"));
                trader.setActive(rs.getBoolean("is_active"));
                trader.setEnabled(rs.getBoolean("is_enabled"));
            }
        }
        
        return trader;
    }

    // Delete a trader
    static public void deleteTrader(int accountNumber) throws SQLException {
        String sql = "DELETE FROM traders WHERE account_number = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, accountNumber);
            pstmt.executeUpdate();
        }
    }

    // Shutdown the HikariCP connection pool
    public static void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

}
