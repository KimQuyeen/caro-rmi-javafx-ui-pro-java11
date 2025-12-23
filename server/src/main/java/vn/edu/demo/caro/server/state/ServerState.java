package vn.edu.demo.caro.server.state;

import vn.edu.demo.caro.server.dao.FriendDao;
import vn.edu.demo.caro.server.dao.MatchDao;
import vn.edu.demo.caro.server.dao.UserDao;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    // RAM Storage
    public final Map<String, OnlineSession> online = new ConcurrentHashMap<>();
    public final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // DAO (Database Access Objects)
    public final UserDao userDao;
    public final FriendDao friendDao;
    public final MatchDao matchDao;

    // Constructor: Bắt buộc truyền DAO đã khởi tạo (có kết nối DB) vào đây
    public ServerState(UserDao userDao, MatchDao matchDao, FriendDao friendDao) {
        this.userDao = userDao;
        this.matchDao = matchDao;
        this.friendDao = friendDao;
    }

    public Set<String> onlineUsers() { return online.keySet(); }
}