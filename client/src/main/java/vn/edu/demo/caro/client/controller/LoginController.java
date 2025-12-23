package vn.edu.demo.caro.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.rmi.LobbyService;
import vn.edu.demo.caro.client.core.RmiConfig;

import java.rmi.registry.LocateRegistry;

public class LoginController implements WithContext {

    private AppContext ctx;

    @FXML private TextField tfHost;
    @FXML private TextField tfPort;
    @FXML private TextField tfName;

    @FXML private TextField tfUsername;
    @FXML private PasswordField tfPassword;

    @FXML private Button btnLogin;
    @FXML private Label lbStatus;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;
        tfHost.setText(RmiConfig.host());
        tfPort.setText(String.valueOf(RmiConfig.port()));
        tfName.setText(RmiConfig.name());
        tfUsername.setText("user" + (System.currentTimeMillis() % 1000));
        tfPassword.setText("123");
        lbStatus.setText("Nhập thông tin và đăng nhập.");
    }

    @FXML
    private void onLogin() {
        btnLogin.setDisable(true);
        lbStatus.setText("Đang kết nối server...");
        try {
            String host = tfHost.getText().trim();
            int port = Integer.parseInt(tfPort.getText().trim());
            String name = tfName.getText().trim();

            var registry = LocateRegistry.getRegistry(host, port);
            LobbyService lobby = (LobbyService) registry.lookup(name);
            ctx.lobby = lobby;

            String username = tfUsername.getText().trim();
            String password = tfPassword.getText();

            ClientCallbackImpl callback = new ClientCallbackImpl(ctx);
            ctx.stage.getProperties().put("callback", callback);

            ctx.username = username;
            ctx.me = lobby.login(username, password, callback);

            lbStatus.setText("Đăng nhập thành công. Đang vào sảnh...");
            ctx.sceneManager.showMain();

        } catch (Exception e) {
            lbStatus.setText("Lỗi: " + e.getMessage());
            btnLogin.setDisable(false);
        }
    }
@FXML
private void onRegister() {
    btnLogin.setDisable(true);
    lbStatus.setText("Đang đăng ký...");
    try {
        String host = tfHost.getText().trim();
        int port = Integer.parseInt(tfPort.getText().trim());
        String name = tfName.getText().trim();

        var registry = LocateRegistry.getRegistry(host, port);
        LobbyService lobby = (LobbyService) registry.lookup(name);
        ctx.lobby = lobby;

        String username = tfUsername.getText().trim();
        String password = tfPassword.getText();

        // gọi register
        lobby.register(username, password);

        // sau khi register thì login để server lưu callback + online session
        ClientCallbackImpl callback = new ClientCallbackImpl(ctx);
        ctx.stage.getProperties().put("callback", callback);

        ctx.username = username;
        ctx.me = lobby.login(username, password, callback);

        lbStatus.setText("Đăng ký thành công. Đang vào sảnh...");
        ctx.sceneManager.showMain();

    } catch (Exception e) {
        lbStatus.setText("Lỗi đăng ký: " + e.getMessage());
        btnLogin.setDisable(false);
    }
}



}
