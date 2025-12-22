package vn.edu.demo.caro.server.admin;

import vn.edu.demo.caro.common.model.RoomInfo;
import vn.edu.demo.caro.common.rmi.AdminService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class AdminConsole {
    private final AdminService admin;

    public AdminConsole(AdminService admin) {
        this.admin = admin;
    }

    public void run() {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String line = br.readLine();
                    if (line == null) break;
                    handle(line.trim());
                }
            } catch (Exception e) {
                System.out.println("AdminConsole stopped: " + e.getMessage());
            }
        }, "admin-console");
        t.setDaemon(false);
        t.start();
    }

    private void handle(String line) throws Exception {
        if (line.isBlank()) return;
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                System.out.println("Commands:");
                System.out.println("  online");
                System.out.println("  rooms");
                System.out.println("  broadcast <message>");
                System.out.println("  warn <user> <message>");
                System.out.println("  ban <user> <minutes> <reason>");
                System.out.println("  exit");
                break;
            case "online": {
                List<String> users = admin.listOnlineUsers();
                System.out.println("Online (" + users.size() + "): " + users);
                break;
            }
            case "rooms": {
                List<RoomInfo> rooms = admin.listRooms();
                System.out.println("Rooms (" + rooms.size() + "):");
                for (RoomInfo r : rooms) System.out.println(" - " + r.getId() + " | " + r);
                break;
            }
            case "broadcast":
                admin.broadcast(line.substring("broadcast".length()).trim());
                System.out.println("OK");
                break;
            case "warn":
                if (parts.length < 3) { System.out.println("Usage: warn <user> <message>"); return; }
                admin.warnUser(parts[1], parts[2]);
                System.out.println("OK");
                break;
            case "ban": {
                String[] p = line.split("\\s+", 4);
                if (p.length < 4) { System.out.println("Usage: ban <user> <minutes> <reason>"); return; }
                String user = p[1];
                int min = Integer.parseInt(p[2]);
                String reason = p[3];
                admin.banUser(user, reason, min);
                System.out.println("OK");
                break;
            }
            case "exit":
                System.exit(0);
                break;
            default:
                System.out.println("Unknown command. Type 'help'.");
        }
    }
}
