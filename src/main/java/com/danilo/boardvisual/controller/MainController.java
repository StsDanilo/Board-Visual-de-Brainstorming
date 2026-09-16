package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.persistence.BoardStorage;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.MainWindow;
import com.danilo.boardvisual.view.Tool;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Liga a janela aos controllers de interação e cuida das ações de arquivo
 * (novo, abrir, salvar).
 */
public class MainController {

    private static final String APP_NAME = "BoardVisual";
    private static final String DEFAULT_BOARD_NAME = "Sem título";

    private final Stage stage;
    private final MainWindow window;
    private final BoardView boardView;
    private final ObjectProperty<Tool> activeTool;
    private final BoardStorage storage = new BoardStorage();

    private final CanvasController canvasController;
    private final CardDragController cardDragController;
    private final ConnectionController connectionController;
    private final SelectionActionsController selectionActionsController;
    private final KeyboardController keyboardController;

    private Path currentFile;

    public MainController(Stage stage, MainWindow window) {
        this.stage = stage;
        this.window = window;
        this.boardView = window.getBoardView();
        this.activeTool = window.getToolBar().activeToolProperty();

        boardView.setActiveTool(activeTool.get());
        activeTool.addListener((obs, old, tool) -> boardView.setActiveTool(tool));

        canvasController = new CanvasController(boardView, activeTool, window.getToolBar().currentColorProperty());
        cardDragController = new CardDragController(boardView, activeTool);
        connectionController = new ConnectionController(boardView, activeTool);
        selectionActionsController = new SelectionActionsController(
                boardView, window.getToolBar(), window.getSelectionToolbar());
        keyboardController = new KeyboardController(stage, boardView, activeTool, selectionActionsController);
        boardView.setCardViewInitializer(cardView -> {
            cardDragController.attach(cardView);
            connectionController.attach(cardView);
        });

        wireMenu();
        newBoard();
    }

    private void wireMenu() {
        window.getNewItem().setOnAction(e -> newBoard());
        window.getOpenItem().setOnAction(e -> open());
        window.getSaveItem().setOnAction(e -> save());
        window.getSaveAsItem().setOnAction(e -> saveAs());
        window.getExitItem().setOnAction(e -> Platform.exit());
        window.getResetViewItem().setOnAction(e -> boardView.resetView());
    }

    public void newBoard() {
        showBoard(new Board(DEFAULT_BOARD_NAME), null);
    }

    public void open() {
        File file = createFileChooser("Abrir board").showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            showBoard(storage.load(file.toPath()), file.toPath());
        } catch (IOException e) {
            showError("Não foi possível abrir o arquivo.", e);
        }
    }

    public void save() {
        if (currentFile == null) {
            saveAs();
        } else {
            saveTo(currentFile);
        }
    }

    public void saveAs() {
        FileChooser chooser = createFileChooser("Salvar board");
        chooser.setInitialFileName(currentFile != null
                ? currentFile.getFileName().toString()
                : boardView.getBoard().getName() + ".json");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".json");
        }
        saveTo(path);
    }

    private void saveTo(Path path) {
        Board board = boardView.getBoard();
        if (currentFile == null || !currentFile.equals(path)) {
            board.setName(stripExtension(path.getFileName().toString()));
        }
        try {
            storage.save(board, path);
            currentFile = path;
            updateTitle();
        } catch (IOException e) {
            showError("Não foi possível salvar o arquivo.", e);
        }
    }

    private void showBoard(Board board, Path file) {
        boardView.setBoard(board);
        boardView.resetView();
        activeTool.set(Tool.SELECT);
        currentFile = file;
        updateTitle();
    }

    private void updateTitle() {
        String boardName = currentFile != null
                ? currentFile.getFileName().toString()
                : boardView.getBoard().getName();
        stage.setTitle(boardName + " — " + APP_NAME);
    }

    private FileChooser createFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Board (*.json)", "*.json"));
        if (currentFile != null && currentFile.toAbsolutePath().getParent() != null) {
            chooser.setInitialDirectory(currentFile.toAbsolutePath().getParent().toFile());
        }
        return chooser;
    }

    private void showError(String header, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
