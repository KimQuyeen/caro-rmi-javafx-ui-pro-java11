package vn.edu.demo.caro.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
    private final DbConfig config;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ignored) {}
    }

    // Constructor
    public Db(DbConfig config) {
        this.config = config;
    }

    // Hàm connect() thay cho static getConnection()
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
    }
    
    // (Optional) Giữ static để hỗ trợ code cũ chưa kịp sửa hết (chỉ dùng nếu DbConfig rỗng)
    @Deprecated
    public static Connection getConnection() throws SQLException {
        return new Db(new DbConfig()).connect();
    }
}