package vn.edu.demo.caro.server.state;

import vn.edu.demo.caro.server.dao.FriendDao;
import vn.edu.demo.caro.server.dao.MatchDao;
import vn.edu.demo.caro.server.dao.UserDao;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    public final Map<String, OnlineSession> online = new ConcurrentHashMap<>();
    public final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public final UserDao userDao = new UserDao();
    public final FriendDao friendDao = new FriendDao();
    public final MatchDao matchDao = new MatchDao();

    public Set<String> onlineUsers() { return online.keySet(); }
}
