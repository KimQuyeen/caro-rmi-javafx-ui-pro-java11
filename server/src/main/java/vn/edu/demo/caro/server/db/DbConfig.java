package vn.edu.demo.caro.server.db;

public final class DbConfig {
    private DbConfig() {}

    public static String url() {
        return System.getProperty("db.url", "jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    }
    public static String user() { return System.getProperty("db.user", "root"); }
    public static String pass() { return System.getProperty("db.pass", "@Trankimquyen123"); }
}
