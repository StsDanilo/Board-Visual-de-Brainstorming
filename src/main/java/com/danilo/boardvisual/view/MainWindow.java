package com.danilo.boardvisual.view;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
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
import javafx.util.Duration;

/**
 * Layout da janela: menu no topo, canvas no centro com o caminho do board no
 * canto superior esquerdo, a barra de ferramentas flutuando à esquerda (e a
 * barra da seleção sobre o card selecionado), e barra de status embaixo.
 *
 * <b>Apresentação (F5):</b> a propriedade {@link #presentingProperty()} liga o modo
 * apresentação: o menu, a barra de ferramentas, a barra de status e a mini barra de estilos de
 * seta somem (visíveis e "managed" ligados por binding), o canvas passa a ser só de leitura
 * ({@link BoardView#setPresenting}) e a janela ganha a pseudo-classe {@code :presenting} (o CSS
 * deixa o caminho do board discreto, entre outras coisas). O caminho no topo e o botão de voltar
 * continuam. Quem liga e desliga a propriedade é o {@code PresentationController}.
 */
public class MainWindow extends BorderPane {

    private final BoardView boardView = new BoardView();
    private final ToolBarView toolBar = new ToolBarView();
    private final SelectionToolbarView selectionToolbar = new SelectionToolbarView();
    private final BreadcrumbView breadcrumb = new BreadcrumbView();
    /** Caixa de busca (Ctrl+F), no canto superior direito do canvas; fechada, fica invisível. */
    private final SearchBarView searchBar = new SearchBarView();
    /** Aviso curto e passageiro na barra de status ({@link #showStatusMessage}). */
    private final Label statusMessage = new Label();
    private final PauseTransition statusMessageTimer = new PauseTransition(Duration.seconds(5));
    private static final PseudoClass PRESENTING = PseudoClass.getPseudoClass("presenting");
    /** Apresentação (tela cheia) ligada: some tudo que é de edição (ver a descrição da classe). */
    private final BooleanProperty presenting = new SimpleBooleanProperty(this, "presenting", false);
    private final MenuBar menuBar;
    private HBox statusBar;
    /** Aviso discreto da apresentação (embaixo, no meio): "Último board", dica das teclas... */
    private final Label presentationNotice = new Label();
    private final PauseTransition presentationNoticeTimer = new PauseTransition(Duration.seconds(3));

    private final MenuItem newItem = new MenuItem("Novo board");
    private final MenuItem openItem = new MenuItem("Abrir...");
    private final MenuItem saveItem = new MenuItem("Salvar");
    private final MenuItem saveAsItem = new MenuItem("Salvar como...");
    private final MenuItem exportPdfItem = new MenuItem("Exportar PDF...");
    private final MenuItem exportPngItem = new MenuItem("Exportar PNG...");
    private final MenuItem exportTransparentPngItem = new MenuItem("Exportar PNG com fundo transparente...");
    private final MenuItem exportSvgItem = new MenuItem("Exportar SVG...");
    private final MenuItem exportTransparentSvgItem = new MenuItem("Exportar SVG com fundo transparente...");
    private final MenuItem exitItem = new MenuItem("Sair");
    private final MenuItem resetViewItem = new MenuItem("Redefinir visualização");
    private final MenuItem fitAllItem = new MenuItem("Ajustar tudo à tela");
    private final MenuItem fitSelectionItem = new MenuItem("Ajustar à seleção");
    private final MenuItem undoItem = new MenuItem("Desfazer");
    private final MenuItem redoItem = new MenuItem("Refazer");
    private final MenuItem cutItem = new MenuItem("Recortar");
    private final MenuItem copyItem = new MenuItem("Copiar");
    private final MenuItem pasteItem = new MenuItem("Colar");
    private final MenuItem selectAllItem = new MenuItem("Selecionar tudo");
    private final MenuItem searchItem = new MenuItem("Buscar...");
    private final MenuItem presentItem = new MenuItem("Apresentar");

    public MainWindow() {
        getStyleClass().add("main-window");

        newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        exportPdfItem.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN));
        exportPngItem.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        resetViewItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
        // Shift+1 / Shift+2 pela posição física da tecla (DIGIT1/DIGIT2): no teclado ABNT2 é a mesma
        // tecla "1" / "2" (que com Shift escreve "!" / "@"); o código da tecla não muda com o Shift.
        fitAllItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHIFT_DOWN));
        fitSelectionItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHIFT_DOWN));        // Atalhos de menu só disparam se o componente com foco não usar a tecla:
        // editando texto, Ctrl+Z e Ctrl+A continuam agindo no próprio texto.
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        selectAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN));
        // Idem Ctrl+X / Ctrl+C / Ctrl+V: editando texto, as teclas são do texto; fora dele, dos cards (3.25).
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
        // Ctrl+F: a busca de texto (3.26). Com texto em edição o SearchController barra o atalho (o item do menu, não).
        searchItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        // F5: apresentação em tela cheia (3.27). Dentro da apresentação o PresentationController barra o F5.
        presentItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));

        Menu fileMenu = new Menu("Arquivo", null,
                newItem, openItem, new SeparatorMenuItem(), saveItem, saveAsItem, new SeparatorMenuItem(), exportPdfItem,
                exportPngItem, exportTransparentPngItem, exportSvgItem, exportTransparentSvgItem,
                new SeparatorMenuItem(), exitItem);
        Menu editMenu = new Menu("Editar", null, undoItem, redoItem, new SeparatorMenuItem(),
                cutItem, copyItem, pasteItem, new SeparatorMenuItem(), selectAllItem, searchItem);
        Menu viewMenu = new Menu("Exibir", null, resetViewItem, fitAllItem, fitSelectionItem,
                new SeparatorMenuItem(), presentItem);
        menuBar = new MenuBar(fileMenu, editMenu, viewMenu);
        setTop(menuBar);

        boardView.setSelectionOverlay(selectionToolbar);
        // A mini barra dos estilos de seta fica no mesmo pai da barra de ferramentas, por cima de tudo;
        // ela não entra no layout da StackPane e se posiciona sozinha à direita da barra.
        presentationNotice.getStyleClass().add("presentation-notice");
        presentationNotice.setMouseTransparent(true);
        presentationNotice.setVisible(false);
        presentationNoticeTimer.setOnFinished(e -> presentationNotice.setVisible(false));
        StackPane canvasArea = new StackPane(boardView, toolBar, breadcrumb, toolBar.getConnectionStyleBar(), searchBar,
                presentationNotice);
        StackPane.setAlignment(presentationNotice, Pos.BOTTOM_CENTER);
        StackPane.setMargin(presentationNotice, new Insets(12, 12, 28, 12));
        StackPane.setAlignment(toolBar, Pos.CENTER_LEFT);
        // Margem de cima maior: deixa espaço para o caminho no canto superior esquerdo.
        StackPane.setMargin(toolBar, new Insets(64, 12, 12, 12));
        StackPane.setAlignment(breadcrumb, Pos.TOP_LEFT);
        StackPane.setMargin(breadcrumb, new Insets(12));
        StackPane.setAlignment(searchBar, Pos.TOP_RIGHT);
        StackPane.setMargin(searchBar, new Insets(12));
        setCenter(canvasArea);
        statusBar = createStatusBar();
        setBottom(statusBar);
        bindPresentation();
    }

    /** O que some na apresentação: menu, barra de ferramentas, barra de status (a mini barra de estilos junto). */
    private void bindPresentation() {
        for (Node bar : new Node[]{menuBar, toolBar, statusBar}) {
            bar.visibleProperty().bind(presenting.not());
            bar.managedProperty().bind(presenting.not());
        }
        presenting.addListener((obs, was, now) -> {
            pseudoClassStateChanged(PRESENTING, now);
            boardView.setPresenting(now);
            if (now) {
                toolBar.getConnectionStyleBar().setVisible(false);
            }
        });
    }

    /** Apresentação ligada? (F5; ver a descrição da classe.) */
    public BooleanProperty presentingProperty() {
        return presenting;
    }

    /** Aviso curto e discreto da apresentação, embaixo no meio; some sozinho depois de uns segundos. */
    public void showPresentationNotice(String text) {
        presentationNotice.setText(text);
        presentationNotice.setVisible(true);
        presentationNoticeTimer.playFromStart();
    }

    /** O aviso da apresentação, se aparece agora ("" quando não). */
    public String getPresentationNotice() {
        return presentationNotice.isVisible() ? presentationNotice.getText() : "";
    }

    /** Some com o aviso da apresentação (ao sair dela). */
    public void hidePresentationNotice() {
        presentationNoticeTimer.stop();
        presentationNotice.setVisible(false);
    }

    /**
     * Mostra um aviso curto na barra de status, que some sozinho depois de uns
     * segundos (ex.: "card travado não foi excluído"). Não interrompe o usuário
     * como uma caixa de diálogo.
     */
    public void showStatusMessage(String text) {
        statusMessage.setText(text);
        statusMessageTimer.playFromStart();
    }

    /** O aviso da barra de status ("" quando não há). */
    public String getStatusMessage() {
        return statusMessage.getText();
    }

    private HBox createStatusBar() {
        Label hint = new Label();
        hint.getStyleClass().add("status-hint");
        hint.textProperty().bind(Bindings.createStringBinding(
                () -> toolBar.getActiveTool().getHint(), toolBar.activeToolProperty()));

        statusMessage.getStyleClass().add("status-message");
        // O aviso aparece inteiro; quem encolhe (com "...") é a dica da ferramenta.
        statusMessage.setMinWidth(Region.USE_PREF_SIZE);
        hint.setMinWidth(0);
        statusMessageTimer.setOnFinished(e -> statusMessage.setText(""));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label zoomLabel = new Label();
        zoomLabel.getStyleClass().add("status-zoom");
        zoomLabel.setMinWidth(Region.USE_PREF_SIZE);
        zoomLabel.textProperty().bind(Bindings.createStringBinding(
                () -> Math.round(boardView.zoomProperty().get() * 100) + "%", boardView.zoomProperty()));

        HBox statusBar = new HBox(hint, statusMessage, spacer, zoomLabel);
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    public BoardView getBoardView() { return boardView; }
    public ToolBarView getToolBar() { return toolBar; }
    public SelectionToolbarView getSelectionToolbar() { return selectionToolbar; }
    public BreadcrumbView getBreadcrumb() { return breadcrumb; }
    public MenuBar getMenuBar() { return menuBar; }
    public HBox getStatusBar() { return statusBar; }
    public MenuItem getPresentItem() { return presentItem; }
    public SearchBarView getSearchBar() { return searchBar; }
    public MenuItem getNewItem() { return newItem; }
    public MenuItem getOpenItem() { return openItem; }
    public MenuItem getSaveItem() { return saveItem; }
    public MenuItem getSaveAsItem() { return saveAsItem; }
    public MenuItem getExportPdfItem() { return exportPdfItem; }
    public MenuItem getExportPngItem() { return exportPngItem; }
    public MenuItem getExportTransparentPngItem() { return exportTransparentPngItem; }
    public MenuItem getExportSvgItem() { return exportSvgItem; }
    public MenuItem getExportTransparentSvgItem() { return exportTransparentSvgItem; }
    public MenuItem getExitItem() { return exitItem; }
    public MenuItem getResetViewItem() { return resetViewItem; }
    public MenuItem getFitAllItem() { return fitAllItem; }
    public MenuItem getFitSelectionItem() { return fitSelectionItem; }
    public MenuItem getUndoItem() { return undoItem; }
    public MenuItem getRedoItem() { return redoItem; }
    public MenuItem getCutItem() { return cutItem; }
    public MenuItem getCopyItem() { return copyItem; }
    public MenuItem getPasteItem() { return pasteItem; }
    public MenuItem getSelectAllItem() { return selectAllItem; }
    public MenuItem getSearchItem() { return searchItem; }
}
