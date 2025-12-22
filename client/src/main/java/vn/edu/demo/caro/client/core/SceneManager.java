package vn.edu.demo.caro.client.core;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class SceneManager {
    private final AppContext ctx;

    public SceneManager(AppContext ctx) {
        this.ctx = ctx;
    }

    public void showLogin() {
        loadAndSet("/vn/edu/demo/caro/client/fxml/login.fxml", 980, 560);
    }

    public void showMain() {
        loadAndSet("/vn/edu/demo/caro/client/fxml/main.fxml", 1240, 800);
    }

    public void showGame() {
        loadAndSet("/vn/edu/demo/caro/client/fxml/game.fxml", 1240, 800);
    }

    private void loadAndSet(String fxml, int w, int h) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller instanceof WithContext) {
                ((WithContext) controller).init(ctx);
            }

            Scene scene = new Scene(root, w, h);

            // Base theme
            var themeUrl = getClass().getResource("/vn/edu/demo/caro/client/css/theme.css");
            if (themeUrl != null) {
                scene.getStylesheets().add(themeUrl.toExternalForm());
            }

            // Optional override for game screen (recommended)
            if (fxml != null && fxml.endsWith("/game.fxml")) {
                var gameCss = getClass().getResource("/vn/edu/demo/caro/client/css/game-override.css");
                if (gameCss != null) {
                    scene.getStylesheets().add(gameCss.toExternalForm());
                }
            }

            ctx.stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot load FXML " + fxml, e);
        }
    }
}
