package vn.edu.demo.caro.server.db;

public class DbConfig {
    private String url;
    private String user;
    private String password;

    // Constructor nhận tham số (để ServerController truyền vào)
    public DbConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    // Constructor rỗng (để dùng giá trị mặc định nếu cần)
    public DbConfig() {
        this.url = System.getProperty("db.url", "jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        this.user = System.getProperty("db.user", "root");
        this.password = System.getProperty("db.pass", "@Trankimquyen123");
    }

    public String getUrl() { return url; }
    public String getUser() { return user; }
    public String getPassword() { return password; }
}