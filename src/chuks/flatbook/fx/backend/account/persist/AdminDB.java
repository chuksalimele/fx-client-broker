/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import chuks.flatbook.fx.common.account.profile.AdminProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class AdminDB {

    

    private static final String DB_URL = "jdbc:sqlite:admins.db";
    private static HikariDataSource dataSource;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AdminDB.class.getName());

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

            AdminDB.createTable();
        } catch (SQLException e) {
            logger.error("Could not initialize AdminDB", e);
        }
    }

    // Create admins table
    static public void createTable() throws SQLException {
        String sql = """
                     CREATE TABLE IF NOT EXISTS admins (
                      SN INTEGER PRIMARY KEY AUTOINCREMENT,
                      admin_id INT UNIQUE,
                      admin_name TEXT NOT NULL,
                      email VARCHAR(255) UNIQUE NOT NULL,
                      password TEXT NOT NULL,
                      registration_time DATETIME NOT NULL,
                      approval_time DATETIME NULL,
                      approved_by INT NULL,
                      server_cofig_priviledge BOOL NULL,
                      account_alter_priviledge BOOL NULL,
                      account_view_priviledge BOOL NULL,                     
                      closed_time DATETIME NULL
                     );""" //approvedBy_AdminID
                ;

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static boolean hasServerConfigPriviledge(int admin_id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("server_cofig_priviledge");
            }
        }
        
        return false;
    }

    public static boolean hasAccountAlterPriviledge(int admin_id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("account_alter_priviledge");
            }
        } 
        
        return false;
    }
    
    public static boolean hasAccountViewPriviledge(int admin_id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("account_view_priviledge");
            }
        }
        
        return false;
    }

    static int countAdminsBySQL(String sql) throws SQLException {
        int totalAdmins = 0;
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery(sql)) {

            // Retrieve the total number of admins
            if (rs.next()) {
                totalAdmins = rs.getInt("total_admins");
            }
        }

        return totalAdmins;
    }

    public static int countAdmins() throws SQLException {
        // SQL query to get the total number of admins
        String sql = "SELECT COUNT(*) AS total_admins"
                + " FROM admins";
        return countAdminsBySQL(sql);
    }


    static public void updateAdminNumber(String email, int admin_id) throws SQLException {
        String sql = "UPDATE admins SET admin_id = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateAdminName(String email, String admin_name) throws SQLException {
        String sql = "UPDATE admins SET admin_name = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, admin_name);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        }
    }

    static public void updateAdminPassword(String email, byte[] hash_password) throws SQLException {
        String sql = "UPDATE admins SET password = ?"
                + " WHERE email = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBytes(1, hash_password);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
        } finally {
            Arrays.fill(hash_password, (byte) 0); // Clear sensitive data
        }
    }


    // Query all admins
    static public List<AdminProfile> queryAllAdmins() throws SQLException {
        String sql = "SELECT * FROM admins";
        return queryAdminsBySQL(sql);
    }

    public static List queryAdminsRange(int page_index, int page_size) throws SQLException {
        String sql = "SELECT * FROM admins"
                + " LIMIT " + page_size 
                + " OFFSET " + (page_index * page_size);
        return queryAdminsBySQL(sql);
    }

    static private List<AdminProfile> queryAdminsBySQL(String sql) throws SQLException {

        List<AdminProfile> admins = new ArrayList<>();

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                AdminProfile admin = new AdminProfile();
                admin.setAdminID(rs.getInt("admin_id"));
                admin.setAdminName(rs.getString("admin_name"));
                admin.setEmail(rs.getString("email"));
                admin.setPassword(rs.getBytes("password"));
                admin.setRegistrationTime(rs.getLong("registration_time"));
                admin.setApprovalTime(rs.getLong("approval_time"));
                admin.setApprovedBy(rs.getInt("approved_by"));
                admins.add(admin);
            }
        }
        
        return admins;
    }

    // Query a admin by account number
    static public AdminProfile queryAdminByID(int admin_id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";
        AdminProfile admin = null;

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                admin = new AdminProfile();
                admin.setAdminID(rs.getInt("admin_id"));
                admin.setAdminName(rs.getString("admin_name"));
                admin.setEmail(rs.getString("email"));
                admin.setPassword(rs.getBytes("password"));
                admin.setRegistrationTime(rs.getLong("registration_time"));
                admin.setApprovalTime(rs.getLong("approval_time"));
                admin.setApprovedBy(rs.getInt("approved_by"));
            }
        }
        
        return admin;
    }

    // Delete a admin
    static public void deleteAdmin(int admin_id) throws SQLException {
        String sql = "DELETE FROM admins WHERE admin_id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, admin_id);
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
