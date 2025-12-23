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

    @FXML 
private void onStartAi() {
    // 1. Lấy dữ liệu từ giao diện
    Integer depth = cbDepth.getValue(); // Độ khó
    boolean aiFirst = cbFirst.getSelectionModel().getSelectedIndex() == 1; // 0: Bạn, 1: AI

    // 2. Lưu vào properties để GameController đọc
    ctx.stage.getProperties().put("ai.enabled", true);
    ctx.stage.getProperties().put("ai.depth", depth);
    ctx.stage.getProperties().put("ai.first", aiFirst);

    // 3. Đặt ID giả để không bị null pointer khi chat/vẽ bàn cờ
    ctx.currentRoomId = "OFFLINE_AI"; 

    // 4. Chuyển cảnh
    ctx.sceneManager.showGame();
}
}
