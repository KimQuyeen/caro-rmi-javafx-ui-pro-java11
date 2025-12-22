package vn.edu.demo.caro.client;

import javafx.application.Application;
import javafx.stage.Stage;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.SceneManager;

public class ClientApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Caro (Server-Authoritative) - RMI JavaFX");
        AppContext ctx = new AppContext(stage);
        SceneManager sm = new SceneManager(ctx);
        ctx.sceneManager = sm;

        sm.showLogin();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
