package vn.edu.demo.caro.server.dao;

import vn.edu.demo.caro.server.db.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MatchDao {
    public void insertMatch(String roomId, String playerX, String playerO, String winner, String reason) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO matches(room_id, player_x, player_o, winner, reason) VALUES(?,?,?,?,?)")) {
            ps.setString(1, roomId);
            ps.setString(2, playerX);
            ps.setString(3, playerO);
            ps.setString(4, winner);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }
}
