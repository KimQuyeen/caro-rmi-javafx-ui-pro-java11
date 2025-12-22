package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public class UserProfile implements Serializable {
    private final String username;
    private final int wins;
    private final int losses;
    private final int draws;
    private final int elo;

    public UserProfile(String username, int wins, int losses, int draws, int elo) {
        this.username = username;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.elo = elo;
    }

    public String getUsername() { return username; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public int getElo() { return elo; }

    @Override public String toString() {
        return username + " | ELO=" + elo + " (W" + wins + " L" + losses + " D" + draws + ")";
    }
}
