package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.export.PdfExporter;
import com.danilo.boardvisual.export.PngExporter;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.persistence.BoardImages;
import com.danilo.boardvisual.persistence.BoardStorage;
import com.danilo.boardvisual.view.BoardRenderer;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.MainWindow;
import com.danilo.boardvisual.view.Tool;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private final ImageController images;
    private final CardContextMenuController contextMenu;
    private final LinkController links;
    private final BoardLinkController boardLinks;
    private final StackOrderController stackOrder;
    private final LockController locks;
    private final ConnectionEditController connectionEdits;
    private final ClipboardController clipboard;
    private final ViewFitController viewFit;
    private final SearchController search;

    /** Pasta do save atual; nula se o board nunca foi salvo ou veio de um .json antigo. */
    private Path currentFolder;
    /** .json antigo aberto e ainda não salvo no formato novo (pasta); nulo nos demais casos. */
    private Path legacyFile;

    /**
     * @param linkOpener abre os links dos cards no navegador (na aplicação,
     *                   {@code HostServices::showDocument}; nos roteiros de teste, um interceptador)
     */
    public MainController(Stage stage, MainWindow window, LinkController.Opener linkOpener) {
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
        ConnectionController connectionController = new ConnectionController(
                boardView, activeTool, window.getToolBar().connectionStyleProperty(), history);
        TextEditController textEditController = new TextEditController(history);
        selectionActionsController = new SelectionActionsController(
                boardView, window.getToolBar(), window.getSelectionToolbar(), history);
        PanelController panelController = new PanelController(
                boardView, window.getSelectionToolbar(), history, textEditController);
        navigation = new NavigationController(boardView, window.getBreadcrumb(), history);
        images = new ImageController(stage, boardView, activeTool, history);
        contextMenu = new CardContextMenuController(boardView);
        // Antes dos outros: Copiar / Recortar / Colar abrem o menu do card, como nos editores.
        clipboard = new ClipboardController(boardView, history, selectionActionsController, images,
                ClipboardController.systemOf(images), contextMenu);
        links = new LinkController(boardView, window.getSelectionToolbar(), history, linkOpener, contextMenu);
        boardLinks = new BoardLinkController(boardView, window.getSelectionToolbar(), history, navigation, contextMenu);
        stackOrder = new StackOrderController(boardView, window.getSelectionToolbar(), history, contextMenu);
        locks = new LockController(boardView, window.getSelectionToolbar(), history, contextMenu);
        selectionActionsController.setNotifier(window::showStatusMessage);
        clipboard.setNotifier(window::showStatusMessage);
        viewFit = new ViewFitController(boardView);
        viewFit.setNotifier(window::showStatusMessage);
        search = new SearchController(boardView, window.getSearchBar(), navigation);
        search.setNotifier(window::showStatusMessage);
        search.installOn(stage);
        connectionEdits = new ConnectionEditController(boardView, history);
        keyboardController = new KeyboardController(stage, boardView, activeTool, selectionActionsController,
                canvasController, panelController, navigation, stackOrder, locks, connectionEdits, search, this);
        CardTextFitController textFitController = new CardTextFitController();
        boardView.setCardViewInitializer(cardView -> {
            textFitController.attach(cardView);
            cardDragController.attach(cardView);
            connectionController.attach(cardView);
            textEditController.attach(cardView);
            panelController.attach(cardView);
            navigation.attach(cardView);
            links.attach(cardView);
            boardLinks.attach(cardView);
        });
        boardView.setConnectionViewInitializer(connectionEdits::attach);
        // Seta selecionada: os botões de estilo da barra mudam a seta.
        window.getToolBar().connectionSelectedProperty().bind(boardView.selectedConnectionProperty().isNotNull());
        window.getToolBar().setOnConnectionStylePicked(connectionEdits::applyStyleToSelected);

        wireMenu();
        newBoard();
    }

    private void wireMenu() {
        window.getNewItem().setOnAction(e -> newBoard());
        window.getOpenItem().setOnAction(e -> open());
        window.getSaveItem().setOnAction(e -> save());
        window.getSaveAsItem().setOnAction(e -> saveAs());
        window.getExportPdfItem().setOnAction(e -> exportPdf());
        window.getExportPngItem().setOnAction(e -> exportPng(PngExporter.Background.WHITE));
        window.getExportTransparentPngItem().setOnAction(e -> exportPng(PngExporter.Background.TRANSPARENT));
        window.getExitItem().setOnAction(e -> Platform.exit());
        window.getResetViewItem().setOnAction(e -> boardView.resetView());
        window.getFitAllItem().setOnAction(e -> viewFit.fitAll());
        window.getFitSelectionItem().setOnAction(e -> viewFit.fitSelection());
        window.getUndoItem().setOnAction(e -> undo());
        window.getRedoItem().setOnAction(e -> redo());
        window.getSelectAllItem().setOnAction(e -> boardView.selectAll());
        window.getSearchItem().setOnAction(e -> search.open());
        window.getCutItem().setOnAction(e -> clipboard.menuCut());
        window.getCopyItem().setOnAction(e -> clipboard.menuCopy());
        window.getPasteItem().setOnAction(e -> clipboard.menuPaste());
        window.getUndoItem().disableProperty().bind(history.canUndoProperty().not());
        window.getRedoItem().disableProperty().bind(history.canRedoProperty().not());
    }

    /** Menu de clique direito dos cards (as próximas funcionalidades acrescentam itens nele). */
    public CardContextMenuController getContextMenu() {
        return contextMenu;
    }

    /** Link externo dos cards (usado pelos roteiros de teste de interface). */
    public LinkController getLinks() {
        return links;
    }

    /** Link para outro board (usado pelos roteiros de teste de interface). */
    public BoardLinkController getBoardLinks() {
        return boardLinks;
    }

    /** Ordem de empilhamento (usado pelos roteiros de teste de interface). */
    public StackOrderController getStackOrder() {
        return stackOrder;
    }

    /** Travar/destravar cards (usado pelos roteiros de teste de interface). */
    public LockController getLocks() {
        return locks;
    }

    /** Seleção, exclusão e texto das setas (usado pelos roteiros de teste de interface). */
    public ConnectionEditController getConnectionEdits() {
        return connectionEdits;
    }

    /** Copiar, recortar e colar cards (usado pelos roteiros de teste de interface). */
    public ClipboardController getClipboard() {
        return clipboard;
    }

    /** Zoom para caber tudo / a seleção (usado pelos roteiros de teste de interface). */
    public ViewFitController getViewFit() {
        return viewFit;
    }

    /** Busca de texto (usado pelos roteiros de teste de interface). */
    public SearchController getSearch() {
        return search;
    }

    /** Navegação entre boards (usado pelos roteiros de teste de interface). */
    public NavigationController getNavigation() {
        return navigation;
    }

    /**
     * Desenha boards fora da tela (PDF e PNG) como a tela: mesmo CSS, mesmas
     * imagens e o mesmo marcador quebrado nos links para boards que não existem mais.
     */
    private BoardRenderer renderer() {
        return new BoardRenderer(stage.getScene().getStylesheets(), images.loader(),
                BoardTree.ids(navigation.getRoot())::contains);
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
        showBoard(new Board(DEFAULT_BOARD_NAME), null, null);
    }

    /**
     * Abre um save. O seletor mostra arquivos: o usuário escolhe o board.json
     * dentro da pasta do board, ou um .json antigo (decisão no DOCUMENTACAO.md, 3.9).
     */
    public void open() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir board (escolha o board.json da pasta do board)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Board (*.json)", "*.json"));
        startInCurrentLocation(chooser);
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            BoardStorage.LoadedBoard loaded = storage.load(file.toPath());
            Path folder = loaded.folder() == null ? null : loaded.folder().toAbsolutePath().normalize();
            if (folder != null) {
                // O nome do board principal é o da pasta: renomeá-la no Explorer vale ao reabrir.
                loaded.board().setName(folderName(folder));
            }
            showBoard(loaded.board(), folder, loaded.isLegacy() ? file.toPath() : null);
        } catch (IOException e) {
            showError("Não foi possível abrir o board.", e);
        }
    }

    public void save() {
        // Um board vindo de .json antigo também cai aqui: sem pasta, pede uma nova
        // e nunca sobrescreve o arquivo antigo.
        if (currentFolder == null) {
            saveAs();
        } else {
            saveTo(currentFolder);
        }
    }

    /** Pede o nome da pasta do novo save (o seletor de arquivo serve só para escolher onde e como chamá-la). */
    public void saveAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar board (será criada uma pasta com este nome)");
        startInCurrentLocation(chooser);
        // Com o mesmo nome da pasta atual o seletor "entraria" nela em vez de escolher um nome novo.
        chooser.setInitialFileName(currentFolder != null ? displayName() + " - cópia" : displayName());
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path folder = folderFrom(file.toPath());
        if (folder.equals(currentFolder)) {
            saveTo(folder);
            return;
        }
        try {
            switch (BoardStorage.inspectTarget(folder)) {
                case OCCUPIED -> {
                    showMessage(Alert.AlertType.WARNING,
                            "A pasta \"" + folderName(folder) + "\" já existe e tem outros arquivos.",
                            "Escolha um nome novo para a pasta do board.");
                    return;
                }
                case EXISTING_SAVE -> {
                    if (!confirmReplace(folder)) {
                        return;
                    }
                }
                case FREE -> {
                }
            }
        } catch (IOException e) {
            showError("Não foi possível salvar o board.", e);
            return;
        }
        saveTo(folder);
    }

    private void saveTo(Path folder) {
        // Sempre o board principal: os boards aninhados são salvos dentro dele.
        Board board = navigation.getRoot();
        String previousName = board.getName();
        boolean sameFolder = folder.equals(currentFolder);
        if (!sameFolder) {
            board.setName(folderName(folder));
        }
        // Imagens copiadas antes do JSON: as inseridas desde o último salvamento e,
        // no "Salvar como", as do save atual.
        List<Path> imageSources = new ArrayList<>();
        if (currentFolder != null && !sameFolder) {
            imageSources.add(BoardImages.dirOf(currentFolder));
        }
        if (images.pendingImagesDir() != null) {
            imageSources.add(images.pendingImagesDir());
        }
        try {
            storage.save(board, folder, imageSources);
        } catch (IOException e) {
            board.setName(previousName);
            showError("Não foi possível salvar o board.", e);
            return;
        }
        currentFolder = folder;
        legacyFile = null;
        images.saved(folder);
        updateTitle();
        removeUnusedImages(folder, board);
    }

    /**
     * Depois do JSON gravado: apaga de imagens/ o que nenhum card usa. "Usa" =
     * algum card da árvore inteira de boards, ou alguma foto do desfazer/refazer
     * (senão o Ctrl+Z traria de volta um card sem a imagem). Falhar aqui não
     * desfaz o salvamento: no pior caso sobra um arquivo, limpo no próximo.
     */
    private void removeUnusedImages(Path folder, Board root) {
        try {
            BoardImages.removeUnused(folder, ImageController.imagesInUse(root, history));
        } catch (IOException e) {
            System.err.println("Não foi possível limpar as imagens não usadas: " + e.getMessage());
        }
    }

    /** Exporta o board principal e todos os aninhados para um PDF navegável. */
    public void exportPdf() {
        boardView.requestFocus(); // conclui uma edição de texto em andamento
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
        startInCurrentLocation(chooser);
        chooser.setInitialFileName(displayName() + ".pdf");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".pdf")) {
            path = path.resolveSibling(path.getFileName() + ".pdf");
        }
        BoardRenderer renderer = renderer();
        try {
            new PdfExporter(renderer::render).export(navigation.getRoot(), path);
        } catch (IOException | RuntimeException e) {
            showError("Não foi possível exportar o PDF.", e);
        }
    }

    /**
     * Exporta para PNG só o board que está na tela (o principal ou um aninhado).
     * Não mexe no board nem na tela: seleção, posição, zoom e desfazer ficam como estão.
     */
    public void exportPng(PngExporter.Background background) {
        boardView.requestFocus(); // conclui uma edição de texto em andamento
        Board board = navigation.getCurrent();
        if (PngExporter.isEmpty(board)) {
            showMessage(Alert.AlertType.INFORMATION, "Este board está vazio.",
                    "Não há nada para exportar. Crie um card antes de exportar a imagem.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(background == PngExporter.Background.TRANSPARENT
                ? "Exportar PNG com fundo transparente" : "Exportar PNG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagem PNG (*.png)", "*.png"));
        startInCurrentLocation(chooser);
        List<String> path = new ArrayList<>(navigation.pathNames());
        path.set(0, displayName()); // o nome do principal é o da pasta do save, como no PDF
        chooser.setInitialFileName(PngExporter.suggestedFileName(path));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        BoardRenderer renderer = renderer();
        PngExporter exporter = new PngExporter((b, bounds, scale, bg) ->
                renderer.render(b, bounds, scale, bg == PngExporter.Background.TRANSPARENT));
        try {
            exporter.export(board, PngExporter.withPngExtension(file.toPath()), background);
        } catch (IOException | RuntimeException e) {
            showError("Não foi possível exportar o PNG.", e);
        }
    }

    private void showBoard(Board board, Path folder, Path legacy) {
        history.clear();
        images.reset(folder); // antes de criar os cards: eles carregam as imagens desta pasta
        navigation.openRoot(board);
        activeTool.set(Tool.SELECT);
        currentFolder = folder;
        legacyFile = legacy;
        updateTitle();
    }

    private void updateTitle() {
        String name = legacyFile != null ? legacyFile.getFileName().toString() : displayName();
        stage.setTitle(name + " — " + APP_NAME);
    }

    /** Nome do board para o usuário: o da pasta do save, o do .json antigo (sem extensão) ou o do board. */
    private String displayName() {
        if (currentFolder != null) {
            return folderName(currentFolder);
        }
        if (legacyFile != null) {
            return stripExtension(legacyFile.getFileName().toString());
        }
        return navigation.getRoot().getName();
    }

    /** Abre os seletores ao lado do save atual (a pasta que contém a pasta do board, ou o .json antigo). */
    private void startInCurrentLocation(FileChooser chooser) {
        Path base = currentFolder != null ? currentFolder : legacyFile != null ? legacyFile.toAbsolutePath() : null;
        Path directory = base == null ? null : base.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            chooser.setInitialDirectory(directory.toFile());
        }
    }

    private boolean confirmReplace(Path folder) {
        ButtonType replace = new ButtonType("Substituir", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "O board que está lá será substituído por este.", replace, cancel);
        alert.initOwner(stage);
        alert.setTitle(APP_NAME);
        alert.setHeaderText("Já existe um board salvo em \"" + folderName(folder) + "\".");
        return alert.showAndWait().filter(button -> button == replace).isPresent();
    }

    private void showError(String header, Exception e) {
        showMessage(Alert.AlertType.ERROR, header, e.getMessage());
    }

    private void showMessage(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /** A pasta do save a partir do que o usuário digitou: um ".json" no fim não faz parte do nome. */
    private static Path folderFrom(Path chosen) {
        String name = chosen.getFileName().toString();
        if (name.length() > ".json".length() && name.toLowerCase().endsWith(".json")) {
            chosen = chosen.resolveSibling(name.substring(0, name.length() - ".json".length()));
        }
        return chosen.toAbsolutePath().normalize();
    }

    private static String folderName(Path folder) {
        return folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
