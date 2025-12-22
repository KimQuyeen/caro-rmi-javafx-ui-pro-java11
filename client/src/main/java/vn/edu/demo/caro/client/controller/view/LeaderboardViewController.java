package vn.edu.demo.caro.client.controller.view;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.model.UserProfile;

public class LeaderboardViewController implements WithContext {

    private AppContext ctx;

    @FXML private TableView<UserProfile> table;
    @FXML private TableColumn<UserProfile, String> colUser;
    @FXML private TableColumn<UserProfile, Integer> colElo;
    @FXML private TableColumn<UserProfile, Integer> colW;
    @FXML private TableColumn<UserProfile, Integer> colL;
    @FXML private TableColumn<UserProfile, Integer> colD;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) ((ClientCallbackImpl) cb).bindLeaderboard(this);

        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colElo.setCellValueFactory(new PropertyValueFactory<>("elo"));
        colW.setCellValueFactory(new PropertyValueFactory<>("wins"));
        colL.setCellValueFactory(new PropertyValueFactory<>("losses"));
        colD.setCellValueFactory(new PropertyValueFactory<>("draws"));
        refresh();
    }

    public void refresh() { if (table != null) table.setItems(FXCollections.observableArrayList(ctx.leaderboard)); }
}
