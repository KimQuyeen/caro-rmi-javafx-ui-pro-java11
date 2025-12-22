package vn.edu.demo.caro.server;

import vn.edu.demo.caro.common.rmi.AdminService;
import vn.edu.demo.caro.common.rmi.LobbyService;
import vn.edu.demo.caro.server.admin.AdminConsole;
import vn.edu.demo.caro.server.service.AdminServiceImpl;
import vn.edu.demo.caro.server.service.LobbyServiceImpl;
import vn.edu.demo.caro.server.state.ServerState;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    public static void main(String[] args) throws Exception {
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

        ServerState state = new ServerState();
        LobbyService lobby = new LobbyServiceImpl(state);
        AdminService admin = new AdminServiceImpl(state);

        registry.rebind(lobbyName, lobby);
        registry.rebind("CaroAdmin", admin);

        System.out.println("Server ready. Bound names: " + lobbyName + ", CaroAdmin");
        System.out.println("DB config: -Ddb.url=jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC -Ddb.user=root -Ddb.pass=@Trankimquyen123");
        System.out.println("Admin console: type 'help' then Enter");
        new AdminConsole(admin).run();
    }
}
