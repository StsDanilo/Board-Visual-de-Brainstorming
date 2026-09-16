package com.danilo.boardvisual;

import com.danilo.boardvisual.controller.MainController;
import com.danilo.boardvisual.view.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class BoardVisualApp extends Application {

    private static final String STYLESHEET = "/com/danilo/boardvisual/styles/app.css";

    @Override
    public void start(Stage stage) {
        MainWindow window = new MainWindow();
        new MainController(stage, window);

        Scene scene = new Scene(window, 1280, 720);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(STYLESHEET), STYLESHEET).toExternalForm());

        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        stage.show();
        // Foco inicial no canvas: os atalhos de teclado funcionam sem precisar clicar antes.
        window.getBoardView().requestFocus();
    }
}
