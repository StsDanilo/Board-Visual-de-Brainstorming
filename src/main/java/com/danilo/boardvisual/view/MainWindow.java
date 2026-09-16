package com.danilo.boardvisual.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Layout da janela: menu no topo, canvas no centro com a barra de ferramentas
 * flutuando à esquerda (e a barra da seleção sobre o card selecionado), e
 * barra de status embaixo.
 */
public class MainWindow extends BorderPane {

    private final BoardView boardView = new BoardView();
    private final ToolBarView toolBar = new ToolBarView();
    private final SelectionToolbarView selectionToolbar = new SelectionToolbarView();

    private final MenuItem newItem = new MenuItem("Novo board");
    private final MenuItem openItem = new MenuItem("Abrir...");
    private final MenuItem saveItem = new MenuItem("Salvar");
    private final MenuItem saveAsItem = new MenuItem("Salvar como...");
    private final MenuItem exitItem = new MenuItem("Sair");
    private final MenuItem resetViewItem = new MenuItem("Redefinir visualização");

    public MainWindow() {
        getStyleClass().add("main-window");

        newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        resetViewItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));

        Menu fileMenu = new Menu("Arquivo", null,
                newItem, openItem, new SeparatorMenuItem(), saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);
        Menu viewMenu = new Menu("Exibir", null, resetViewItem);
        setTop(new MenuBar(fileMenu, viewMenu));

        boardView.setSelectionOverlay(selectionToolbar);
        StackPane canvasArea = new StackPane(boardView, toolBar);
        StackPane.setAlignment(toolBar, Pos.CENTER_LEFT);
        StackPane.setMargin(toolBar, new Insets(12));
        setCenter(canvasArea);
        setBottom(createStatusBar());
    }

    private HBox createStatusBar() {
        Label hint = new Label();
        hint.getStyleClass().add("status-hint");
        hint.textProperty().bind(Bindings.createStringBinding(
                () -> toolBar.getActiveTool().getHint(), toolBar.activeToolProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label zoomLabel = new Label();
        zoomLabel.getStyleClass().add("status-zoom");
        zoomLabel.textProperty().bind(Bindings.createStringBinding(
                () -> Math.round(boardView.zoomProperty().get() * 100) + "%", boardView.zoomProperty()));

        HBox statusBar = new HBox(hint, spacer, zoomLabel);
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    public BoardView getBoardView() { return boardView; }
    public ToolBarView getToolBar() { return toolBar; }
    public SelectionToolbarView getSelectionToolbar() { return selectionToolbar; }
    public MenuItem getNewItem() { return newItem; }
    public MenuItem getOpenItem() { return openItem; }
    public MenuItem getSaveItem() { return saveItem; }
    public MenuItem getSaveAsItem() { return saveAsItem; }
    public MenuItem getExitItem() { return exitItem; }
    public MenuItem getResetViewItem() { return resetViewItem; }
}
