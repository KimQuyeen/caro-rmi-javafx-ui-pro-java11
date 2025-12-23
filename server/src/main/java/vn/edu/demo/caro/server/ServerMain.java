package vn.edu.demo.caro.server;

import vn.edu.demo.caro.common.rmi.AdminService;
import vn.edu.demo.caro.common.rmi.LobbyService;
import vn.edu.demo.caro.server.admin.AdminConsole;
import vn.edu.demo.caro.server.dao.FriendDao;
import vn.edu.demo.caro.server.dao.MatchDao;
import vn.edu.demo.caro.server.dao.UserDao;
import vn.edu.demo.caro.server.db.Db;
import vn.edu.demo.caro.server.db.DbConfig;
import vn.edu.demo.caro.server.service.AdminServiceImpl;
import vn.edu.demo.caro.server.service.LobbyServiceImpl;
import vn.edu.demo.caro.server.state.ServerState;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        // 1. Cấu hình DB
        // Bạn có thể sửa user/pass trực tiếp ở đây nếu muốn chạy bản Console này
        DbConfig config = new DbConfig(
            "jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "root",
            "@Trankimquyen123"
        );
        Db db = new Db(config);

        // 2. Khởi tạo các DAO
        UserDao userDao = new UserDao(db);
        MatchDao matchDao = new MatchDao(db);
        FriendDao friendDao = new FriendDao(db);

        // 3. Khởi tạo ServerState (Phiên bản mới yêu cầu tham số)
        ServerState state = new ServerState(userDao, matchDao, friendDao);

        // 4. Khởi tạo Services
        LobbyService lobby = new LobbyServiceImpl(state);
        AdminService admin = new AdminServiceImpl(state);

        // 5. Mở cổng RMI
        int port = Integer.parseInt(System.getProperty("rmi.port", "1099"));
        String lobbyName = System.getProperty("rmi.name", "CaroLobby");

        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(port);
            System.out.println("RMI registry created on port " + port);
        } catch (Exception ex) {
            registry = LocateRegistry.getRegistry(port);
            System.out.println("Using existing RMI registry on port " + port);
        }

        registry.rebind(lobbyName, lobby);
        registry.rebind("CaroAdmin", admin);

        System.out.println("Server ready. Bound names: " + lobbyName + ", CaroAdmin");
        System.out.println("Admin console: type 'help' then Enter");
        
        // Chạy Console Admin
        new AdminConsole(admin).run();
    }
}