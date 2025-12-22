package vn.edu.demo.caro.client.controller.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.core.*;

public class AiViewController implements WithContext {

    private AppContext ctx;

    @FXML private ComboBox<Integer> cbDepth;
    @FXML private ComboBox<String> cbFirst;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;
        cbDepth.getItems().setAll(1,2,3,4,5);
        cbDepth.getSelectionModel().select(Integer.valueOf(3));
        cbFirst.getItems().setAll("Bạn đi trước (X)", "AI đi trước (X)");
        cbFirst.getSelectionModel().select(0);
    }

    @FXML private void onStartAi() {
        int depth = cbDepth.getSelectionModel().getSelectedItem();
        boolean aiFirst = cbFirst.getSelectionModel().getSelectedIndex() == 1;

        ctx.stage.getProperties().put("ai.enabled", true);
        ctx.stage.getProperties().put("ai.depth", depth);
        ctx.stage.getProperties().put("ai.aiFirst", aiFirst);

        ctx.currentRoomId = "AI-OFFLINE";
        ctx.sceneManager.showGame();
    }
}
