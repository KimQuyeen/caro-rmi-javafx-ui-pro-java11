package vn.edu.demo.caro.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.rmi.LobbyService;

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
// ... imports

    @FXML
    private void onRegister() {
        btnLogin.setDisable(true); // Tạm khóa nút để tránh bấm nhiều lần
        lbStatus.setText("Đang đăng ký...");
        
        try {
            String host = tfHost.getText().trim();
            int port = Integer.parseInt(tfPort.getText().trim());
            String name = tfName.getText().trim();

            // Kết nối RMI (chỉ để đăng ký)
            var registry = LocateRegistry.getRegistry(host, port);
            LobbyService lobby = (LobbyService) registry.lookup(name);
            
            // Không gán ctx.lobby ở đây vội, để lúc login gán sau cũng được
            // hoặc gán cũng không sao: ctx.lobby = lobby;

            String username = tfUsername.getText().trim();
            String password = tfPassword.getText();

            // 1. Gọi register (Server trả về boolean)
            boolean success = lobby.register(username, password);

            if (success) {
                // 2. Thông báo thành công
                lbStatus.setText("Đăng ký thành công! Vui lòng đăng nhập.");
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Thông báo");
                alert.setHeaderText(null);
                alert.setContentText("Đăng ký tài khoản [" + username + "] thành công.\nVui lòng nhấn Đăng nhập để vào game.");
                alert.showAndWait();

                // 3. Reset trạng thái để người dùng đăng nhập
                // (Giữ nguyên username để tiện cho user, có thể xóa password nếu muốn bảo mật hơn)
                // tfPassword.clear(); 
            }

        } catch (Exception e) {
            lbStatus.setText("Lỗi đăng ký: " + e.getMessage());
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Đăng ký thất bại");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        } finally {
            // Mở lại nút login để người dùng bấm
            btnLogin.setDisable(false);
        }
    }


}
