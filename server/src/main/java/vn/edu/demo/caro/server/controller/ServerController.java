package vn.edu.demo.caro.server.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import vn.edu.demo.caro.common.model.Enums;
import vn.edu.demo.caro.server.dao.FriendDao;
import vn.edu.demo.caro.server.dao.MatchDao;
import vn.edu.demo.caro.server.dao.UserDao;
import vn.edu.demo.caro.server.db.Db;
import vn.edu.demo.caro.server.db.DbConfig;
import vn.edu.demo.caro.server.service.LobbyServiceImpl;
import vn.edu.demo.caro.server.state.Room;
import vn.edu.demo.caro.server.state.ServerState;

import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerController {

    // --- Header ---
    @FXML private Label lbStatus;
    @FXML private Circle statusDot;
    @FXML private Button btnToggle;

    // --- Tab Logs ---
    @FXML private TextArea txtLog;

    // --- Tab Users ---
    @FXML private TableView<UserRow> tvUsers;
    @FXML private TableColumn<UserRow, String> colUser;
    @FXML private TableColumn<UserRow, String> colElo;
    @FXML private TableColumn<UserRow, String> colWins;
    @FXML private TableColumn<UserRow, String> colStatus;
    @FXML private Label lbUserCount;

    // --- Tab Rooms ---
    @FXML private TableView<RoomRow> tvRooms;
    @FXML private TableColumn<RoomRow, String> colRoomId;
    @FXML private TableColumn<RoomRow, String> colRoomName;
    @FXML private TableColumn<RoomRow, String> colOwner;
    @FXML private TableColumn<RoomRow, String> colPlayers;
    @FXML private TableColumn<RoomRow, String> colRoomStatus;
    @FXML private Label lbRoomCount;

    // --- Tab Broadcast ---
    @FXML private TextArea txtBroadcast;

    // --- Logic Vars ---
    private boolean isRunning = false;
    private Registry registry;
    private ServerState state;
    private ScheduledExecutorService monitor;

    @FXML
    public void initialize() {
        redirectConsole();
        log(">>> Ready to start server...");

        // Setup Columns cho User Table
        colUser.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().username));
        colElo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().elo));
        colWins.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().wins));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status));

        // Setup Columns cho Room Table
        colRoomId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().id));
        colRoomName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        colOwner.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().owner));
        colPlayers.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().players));
        colRoomStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status));
    }

    @FXML
    private void onToggleServer() {
        if (isRunning) stopServer();
        else startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try {
                log("--- Starting Server ---");
                // Cấu hình DB (Hardcode hoặc lấy từ env)
                System.setProperty("db.url", "jdbc:mysql://127.0.0.1:3306/caro_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
                System.setProperty("db.user", "root");
                System.setProperty("db.pass", "@Trankimquyen123");

                DbConfig dbConfig = new DbConfig(
                        System.getProperty("db.url"), System.getProperty("db.user"), System.getProperty("db.pass"));
                Db db = new Db(dbConfig);
                state = new ServerState(new UserDao(db), new MatchDao(db), new FriendDao(db));

                LobbyServiceImpl lobbyService = new LobbyServiceImpl(state);
                registry = LocateRegistry.createRegistry(1099);
                registry.rebind("CaroLobby", lobbyService);

                isRunning = true;
                Platform.runLater(() -> {
                    statusDot.setFill(Color.LIGHTGREEN);
                    lbStatus.setText("RUNNING (Port 1099)");
                    btnToggle.setText("STOP SERVER");
                    btnToggle.getStyleClass().add("btn-stop");
                    log(">>> Server Started Successfully!");
                });

                startMonitoring();

            } catch (Exception e) {
                log("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void stopServer() {
        try {
            if (registry != null) UnicastRemoteObject.unexportObject(registry, true);
            if (monitor != null) monitor.shutdown();
            isRunning = false;
            statusDot.setFill(Color.GRAY);
            lbStatus.setText("STOPPED");
            btnToggle.setText("START SERVER");
            btnToggle.getStyleClass().remove("btn-stop");
            log(">>> Server Stopped.");
        } catch (Exception e) {
            log("Error stopping: " + e.getMessage());
        }
    }

    // --- ACTIONS ---

    @FXML
    private void onWarnUser() {
        UserRow selected = tvUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log("[Warn] Chọn người chơi cần cảnh báo.");
            return;
        }
        sendCallback(selected.username, cb -> cb.onWarning("ADMIN CẢNH BÁO: Vui lòng cư xử đúng mực!"));
        log("Đã gửi cảnh báo tới: " + selected.username);
    }

    @FXML
    private void onBanUser() {
        UserRow selected = tvUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log("[Ban] Chọn người chơi cần khóa.");
            return;
        }
        try {
            // 1. Cập nhật DB (Ban 30 phút)
            state.userDao.banUser(selected.username, "Vi phạm quy tắc cộng đồng", 30);
            
            // 2. Thông báo và kick
            sendCallback(selected.username, cb -> cb.onBanned("Bạn đã bị khóa tài khoản 30 phút bởi Admin."));
            state.online.remove(selected.username); // Kick khỏi online
            
            log("Đã BAN tài khoản: " + selected.username);
            refreshData(); // Cập nhật lại bảng
        } catch (SQLException e) {
            log("Lỗi BAN: " + e.getMessage());
        }
    }

    @FXML
    private void onBroadcast() {
        String msg = txtBroadcast.getText().trim();
        if (msg.isEmpty()) return;

        // Gửi tới tất cả user online
        for (String user : state.onlineUsers()) {
            sendCallback(user, cb -> cb.onAnnouncement("THÔNG BÁO TỪ ADMIN:\n" + msg));
        }
        
        log("[Broadcast] Đã gửi thông báo tới " + state.onlineUsers().size() + " người.");
        txtBroadcast.clear();
    }

    // --- MONITORING (Cập nhật dữ liệu bảng liên tục) ---
    private void startMonitoring() {
        monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(this::refreshData, 1, 2, TimeUnit.SECONDS);
    }

    private void refreshData() {
        if (state == null) return;

        Platform.runLater(() -> {
            // 1. Update Users
            var currentUsers = new ArrayList<UserRow>();
            for (String username : state.onlineUsers()) {
                try {
                    var u = state.userDao.find(username).orElse(null);
                    if (u != null) {
                        String status = "Online"; // Có thể check thêm nếu đang playing
                        currentUsers.add(new UserRow(username, String.valueOf(u.elo), String.valueOf(u.wins), status));
                    }
                } catch (Exception ignored) {}
            }
            tvUsers.getItems().setAll(currentUsers);
            lbUserCount.setText("Online: " + currentUsers.size());

            // 2. Update Rooms
            var currentRooms = new ArrayList<RoomRow>();
            for (Room r : state.rooms.values()) {
                currentRooms.add(new RoomRow(
                        r.id.substring(0, 8) + "...", // ID rút gọn
                        r.name,
                        r.owner,
                        r.players.toString(),
                        r.status.toString()
                ));
            }
            tvRooms.getItems().setAll(currentRooms);
            lbRoomCount.setText("Active Rooms: " + currentRooms.size());
        });
    }

    // --- Helpers ---
    private void sendCallback(String username, CallbackAction action) {
        var s = state.online.get(username);
        if (s != null && s.callback != null) {
            new Thread(() -> {
                try { action.run(s.callback); } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    @FunctionalInterface interface CallbackAction { void run(vn.edu.demo.caro.common.rmi.ClientCallback cb) throws Exception; }

    private void redirectConsole() {
        OutputStream out = new OutputStream() {
            @Override public void write(int b) { appendText(String.valueOf((char) b)); }
            @Override public void write(byte[] b, int off, int len) { appendText(new String(b, off, len)); }
        };
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }
    private void appendText(String s) { Platform.runLater(() -> txtLog.appendText(s)); }
    private void log(String s) { System.out.println(s); }

    // --- Inner Classes for TableView ---
    public static class UserRow {
        public String username, elo, wins, status;
        public UserRow(String u, String e, String w, String s) { username=u; elo=e; wins=w; status=s; }
    }
    public static class RoomRow {
        public String id, name, owner, players, status;
        public RoomRow(String i, String n, String o, String p, String s) { id=i; name=n; owner=o; players=p; status=s; }
    }
}