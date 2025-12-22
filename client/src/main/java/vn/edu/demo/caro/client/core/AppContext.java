package vn.edu.demo.caro.client.core;

import javafx.stage.Stage;
import vn.edu.demo.caro.common.model.RoomInfo;
import vn.edu.demo.caro.common.model.UserProfile;
import vn.edu.demo.caro.common.rmi.LobbyService;

import java.util.concurrent.CopyOnWriteArrayList;

public class AppContext {
    public final Stage stage;
    public SceneManager sceneManager;

    public LobbyService lobby;
    public String username;
    public UserProfile me;

    public final CopyOnWriteArrayList<String> onlineUsers = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<RoomInfo> rooms = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> friends = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<UserProfile> leaderboard = new CopyOnWriteArrayList<>();

    // current game
    public volatile String currentRoomId;

    public AppContext(Stage stage) {
        this.stage = stage;
    }
}
