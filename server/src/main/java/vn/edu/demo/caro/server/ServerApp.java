package vn.edu.demo.caro.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ServerApp extends Application {

    @Override
public void start(Stage stage) throws Exception {
    // [SỬA DÒNG NÀY]
    // Cũ: getClass().getResource("/vn/edu/demo/caro/server/ui/server.fxml")
    // Mới:
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/server.fxml"));
    
    Scene scene = new Scene(loader.load());
    stage.setTitle("Caro Server Manager");
    stage.setScene(scene);
    stage.setOnCloseRequest(e -> System.exit(0));
    stage.show();
}

    public static void main(String[] args) {
        launch(args);
    }
}