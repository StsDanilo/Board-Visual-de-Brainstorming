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
    private final SelectionActionsController selectionActionsController;
    private final KeyboardController keyboardController;
    private final EditHistory history;
    private final NavigationController navigation;

    private Path currentFile;

    public MainController(Stage stage, MainWindow window) {
        this.stage = stage;
        this.window = window;
        this.boardView = window.getBoardView();
        this.activeTool = window.getToolBar().activeToolProperty();

        boardView.setActiveTool(activeTool.get());
        activeTool.addListener((obs, old, tool) -> boardView.setActiveTool(tool));

        history = new EditHistory(boardView::getBoard);
        canvasController = new CanvasController(
                boardView, activeTool, window.getToolBar().currentColorProperty(), history);
        CardDragController cardDragController = new CardDragController(boardView, activeTool, history);
        new CardResizeController(boardView, activeTool, history);
        ConnectionController connectionController = new ConnectionController(boardView, activeTool, history);
        TextEditController textEditController = new TextEditController(history);
        selectionActionsController = new SelectionActionsController(
                boardView, window.getToolBar(), window.getSelectionToolbar(), history);
        PanelController panelController = new PanelController(
                boardView, window.getSelectionToolbar(), history, textEditController);
        navigation = new NavigationController(boardView, window.getBreadcrumb(), history);
        keyboardController = new KeyboardController(stage, boardView, activeTool, selectionActionsController,
                canvasController, panelController, navigation, this);
        CardTextFitController textFitController = new CardTextFitController();
        boardView.setCardViewInitializer(cardView -> {
            textFitController.attach(cardView);
            cardDragController.attach(cardView);
            connectionController.attach(cardView);
            textEditController.attach(cardView);
            panelController.attach(cardView);
            navigation.attach(cardView);
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
        window.getUndoItem().setOnAction(e -> undo());
        window.getRedoItem().setOnAction(e -> redo());
        window.getSelectAllItem().setOnAction(e -> boardView.selectAll());
        window.getUndoItem().disableProperty().bind(history.canUndoProperty().not());
        window.getRedoItem().disableProperty().bind(history.canRedoProperty().not());
    }

    public void undo() {
        boardView.requestFocus(); // conclui uma edição de texto em andamento antes de desfazer
        history.undo();
    }

    public void redo() {
        boardView.requestFocus();
        history.redo();
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
                : navigation.getRoot().getName() + ".json");
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
        // Sempre o board principal: os boards aninhados são salvos dentro dele.
        Board board = navigation.getRoot();
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
        history.clear();
        navigation.openRoot(board);
        activeTool.set(Tool.SELECT);
        currentFile = file;
        updateTitle();
    }

    private void updateTitle() {
        String boardName = currentFile != null
                ? currentFile.getFileName().toString()
                : navigation.getRoot().getName();
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
