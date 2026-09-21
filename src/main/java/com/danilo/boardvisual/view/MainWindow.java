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
 * Layout da janela: menu no topo, canvas no centro com o caminho do board no
 * canto superior esquerdo, a barra de ferramentas flutuando à esquerda (e a
 * barra da seleção sobre o card selecionado), e barra de status embaixo.
 */
public class MainWindow extends BorderPane {

    private final BoardView boardView = new BoardView();
    private final ToolBarView toolBar = new ToolBarView();
    private final SelectionToolbarView selectionToolbar = new SelectionToolbarView();
    private final BreadcrumbView breadcrumb = new BreadcrumbView();

    private final MenuItem newItem = new MenuItem("Novo board");
    private final MenuItem openItem = new MenuItem("Abrir...");
    private final MenuItem saveItem = new MenuItem("Salvar");
    private final MenuItem saveAsItem = new MenuItem("Salvar como...");
    private final MenuItem exitItem = new MenuItem("Sair");
    private final MenuItem resetViewItem = new MenuItem("Redefinir visualização");
    private final MenuItem undoItem = new MenuItem("Desfazer");
    private final MenuItem redoItem = new MenuItem("Refazer");
    private final MenuItem selectAllItem = new MenuItem("Selecionar tudo");

    public MainWindow() {
        getStyleClass().add("main-window");

        newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        resetViewItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
        // Atalhos de menu só disparam se o componente com foco não usar a tecla:
        // editando texto, Ctrl+Z e Ctrl+A continuam agindo no próprio texto.
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        selectAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN));

        Menu fileMenu = new Menu("Arquivo", null,
                newItem, openItem, new SeparatorMenuItem(), saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);
        Menu editMenu = new Menu("Editar", null, undoItem, redoItem, new SeparatorMenuItem(), selectAllItem);
        Menu viewMenu = new Menu("Exibir", null, resetViewItem);
        setTop(new MenuBar(fileMenu, editMenu, viewMenu));

        boardView.setSelectionOverlay(selectionToolbar);
        StackPane canvasArea = new StackPane(boardView, toolBar, breadcrumb);
        StackPane.setAlignment(toolBar, Pos.CENTER_LEFT);
        // Margem de cima maior: deixa espaço para o caminho no canto superior esquerdo.
        StackPane.setMargin(toolBar, new Insets(64, 12, 12, 12));
        StackPane.setAlignment(breadcrumb, Pos.TOP_LEFT);
        StackPane.setMargin(breadcrumb, new Insets(12));
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
    public BreadcrumbView getBreadcrumb() { return breadcrumb; }
    public MenuItem getNewItem() { return newItem; }
    public MenuItem getOpenItem() { return openItem; }
    public MenuItem getSaveItem() { return saveItem; }
    public MenuItem getSaveAsItem() { return saveAsItem; }
    public MenuItem getExitItem() { return exitItem; }
    public MenuItem getResetViewItem() { return resetViewItem; }
    public MenuItem getUndoItem() { return undoItem; }
    public MenuItem getRedoItem() { return redoItem; }
    public MenuItem getSelectAllItem() { return selectAllItem; }
}
