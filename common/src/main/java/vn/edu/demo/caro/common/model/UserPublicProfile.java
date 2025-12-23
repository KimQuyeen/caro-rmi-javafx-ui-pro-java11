package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public class UserPublicProfile implements Serializable {
    private String username;
    private int gamesPlayed;
    private int wins;
    private int losses;
    private int draws;
    private double winRate;
    private int elo;
    private int rank;
    private FriendStatus friendStatus = FriendStatus.NOT_FRIEND;

    public enum FriendStatus {
        FRIEND,
        OUTGOING_PENDING,
        INCOMING_PENDING,
        NOT_FRIEND
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public int getElo() { return elo; }
    public void setElo(int elo) { this.elo = elo; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public FriendStatus getFriendStatus() { return friendStatus; }
    public void setFriendStatus(FriendStatus friendStatus) { this.friendStatus = friendStatus; }
}
