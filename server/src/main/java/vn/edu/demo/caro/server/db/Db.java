package vn.edu.demo.caro.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    private Db() {}

    static {
        try {
            // MySQL Connector/J auto-registers; keep for clarity
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ignored) {}
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DbConfig.url(), DbConfig.user(), DbConfig.pass());
    }
}
