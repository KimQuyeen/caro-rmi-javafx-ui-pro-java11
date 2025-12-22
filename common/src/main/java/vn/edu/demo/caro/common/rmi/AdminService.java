package vn.edu.demo.caro.common.rmi;

import vn.edu.demo.caro.common.model.RoomInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface AdminService extends Remote {
    List<String> listOnlineUsers() throws RemoteException;
    List<RoomInfo> listRooms() throws RemoteException;

    void broadcast(String message) throws RemoteException;
    void warnUser(String username, String message) throws RemoteException;
    void banUser(String username, String reason, int minutes) throws RemoteException;
}
