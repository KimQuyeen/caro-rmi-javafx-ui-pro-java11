package vn.edu.demo.caro.server.service;

import vn.edu.demo.caro.common.model.RoomInfo;
import vn.edu.demo.caro.common.rmi.AdminService;
import vn.edu.demo.caro.server.state.Room;
import vn.edu.demo.caro.server.state.ServerState;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static vn.edu.demo.caro.common.model.Enums.RoomStatus;

public class AdminServiceImpl extends UnicastRemoteObject implements AdminService {
    private final ServerState state;

    public AdminServiceImpl(ServerState state) throws RemoteException {
        super(0);
        this.state = state;
    }

    @Override
    public List<String> listOnlineUsers() throws RemoteException {
        return state.online.keySet().stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    @Override
    public List<RoomInfo> listRooms() throws RemoteException {
        return state.rooms.values().stream()
                .map(this::toInfo)
                .sorted(Comparator.comparing(RoomInfo::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void broadcast(String message) throws RemoteException {
        for (var s : state.online.values()) {
            try { s.callback.onAnnouncement("[SERVER] " + message); } catch (Exception ignored) {}
        }
    }

    @Override
    public void warnUser(String username, String message) throws RemoteException {
        var s = state.online.get(username);
        if (s != null && s.callback != null) {
            try { s.callback.onWarning(message); } catch (Exception ignored) {}
        }
    }

    @Override
    public void banUser(String username, String reason, int minutes) throws RemoteException {
        try {
            Instant until = Instant.now().plus(minutes, ChronoUnit.MINUTES);
            state.userDao.setBan(username, until, reason);

            var s = state.online.remove(username);
            if (s != null && s.callback != null) {
                try { s.callback.onBanned("Bạn bị ban " + minutes + " phút. Lý do: " + reason); } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    private RoomInfo toInfo(Room r) {
    return new RoomInfo(
        r.id, r.name, r.owner,
        2, r.players.size(), r.status, r.createdAt,
        r.boardSize, r.blockTwoEnds,
        r.hasPassword, r.timed, r.timeLimitSeconds
    );
}

}
