package vn.edu.demo.caro.client.core;

import javafx.stage.Stage;
import vn.edu.demo.caro.client.state.GlobalChatStore;
import vn.edu.demo.caro.common.model.FriendInfo;
import vn.edu.demo.caro.common.model.RoomInfo;
import vn.edu.demo.caro.common.model.UserProfile;
import vn.edu.demo.caro.common.rmi.LobbyService;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AppContext {
    public final Stage stage;
    public SceneManager sceneManager;

    public LobbyService lobby;
    public String username;
    public UserProfile me;

    public final CopyOnWriteArrayList<String> onlineUsers = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<RoomInfo> rooms = new CopyOnWriteArrayList<>();
public final CopyOnWriteArrayList<FriendInfo> friends = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<UserProfile> leaderboard = new CopyOnWriteArrayList<>();

    public volatile String currentRoomId;

    private final GlobalChatStore globalChatStore = GlobalChatStore.get();
    public GlobalChatStore getGlobalChatStore() { return globalChatStore; }

    /**
     * Executor cho RMI/network I/O.
     * Dùng pool >= 2 threads để tránh nghẽn/đơ khi 1 remote call bị chậm.
     */
    private final ExecutorService ioExecutor;

    public ExecutorService io() { return ioExecutor; }

    public AppContext(Stage stage) {
        this.stage = stage;

        int nThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        AtomicInteger seq = new AtomicInteger(1);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "client-io-" + seq.getAndIncrement());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) -> ex.printStackTrace());
            return t;
        };

        // Queue có giới hạn để tránh leak nếu server spam / call treo
        this.ioExecutor = new ThreadPoolExecutor(
                nThreads, nThreads,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                tf,
                new ThreadPoolExecutor.DiscardPolicy()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { ioExecutor.shutdownNow(); } catch (Exception ignored) {}
        }));
    }
}
