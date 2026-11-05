package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.persistence.BoardImages;
import com.danilo.boardvisual.persistence.ImageImporter;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardImageLoader;
import com.danilo.boardvisual.view.Tool;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Imagens nos cards: inserir (ferramenta Imagem, colar, arrastar arquivo) e
 * saber onde cada arquivo de imagem está.
 *
 * Onde as imagens moram (DOCUMENTACAO.md, 3.17):
 * - na pasta {@code imagens/} do save atual, as que já foram salvas;
 * - numa pasta de trabalho temporária, as inseridas desde o último salvamento.
 *   A pasta do save só muda ao salvar: inserir uma imagem não mexe nela. Ao
 *   salvar, o {@link MainController} passa a pasta de trabalho como origem das
 *   imagens e depois chama {@link #saved(Path)}, que a esvazia.
 *
 * Cada inserção (de uma ou várias imagens) é um passo no desfazer. Excluir um
 * card não apaga o arquivo: a limpeza só acontece ao salvar.
 */
public class ImageController implements ClipboardController.ImageSpace {

    /** Maior lado do card de imagem ao ser criado (a imagem guardada pode ser maior). */
    static final double INITIAL_MAX_SIDE = 320;
    /** Imagens muito pequenas são ampliadas até este maior lado, para dar para pegar o card. */
    static final double INITIAL_MIN_SIDE = 60;
    /** Deslocamento entre várias imagens inseridas de uma vez. */
    private static final double CASCADE_OFFSET = 24;

    private final Stage stage;
    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final EditHistory history;

    /** Pasta do save atual ({@code null} se o board ainda não foi salvo em pasta). */
    private Path saveFolder;
    /** Pasta temporária com as imagens inseridas desde o último salvamento (criada na primeira). */
    private Path workDir;
    /** Imagens já carregadas, por nome (o nome é único e o arquivo nunca muda). */
    private final Map<String, Image> cache = new HashMap<>();
    private File lastChooserDirectory;

    public ImageController(Stage stage, BoardView view, ObjectProperty<Tool> activeTool, EditHistory history) {
        this.stage = stage;
        this.view = view;
        this.activeTool = activeTool;
        this.history = history;

        view.setImageLoader(this::load);
        installDragAndDrop();
        // A ferramenta Imagem não fica ativa: abre o seletor e volta para Selecionar.
        activeTool.addListener((obs, old, tool) -> {
            if (tool == Tool.IMAGE) {
                Platform.runLater(() -> {
                    activeTool.set(Tool.SELECT);
                    chooseAndInsert();
                });
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteWorkDir, "limpa-imagens-temporarias"));
    }

    /** Quem desenha os cards fora da tela (PDF) usa as mesmas imagens. */
    public CardImageLoader loader() {
        return this::load;
    }

    // ------------------------------------------------------------ pastas

    /**
     * Outro board na tela (novo ou aberto): as imagens passam a vir da pasta
     * desse save, e as inseridas no board anterior sem salvar são descartadas.
     */
    public void reset(Path folder) {
        saveFolder = folder;
        cache.clear();
        clearWorkDir();
    }

    /** Pasta com as imagens inseridas desde o último salvamento, ou {@code null} se não há nenhuma. */
    public Path pendingImagesDir() {
        return workDir != null && Files.isDirectory(workDir) ? workDir : null;
    }

    /** O board acabou de ser salvo em {@code folder}: as imagens pendentes já estão lá. */
    public void saved(Path folder) {
        saveFolder = folder;
        clearWorkDir();
    }

    /**
     * Nomes das imagens que não podem ser apagadas ao salvar: as dos cards de
     * toda a árvore de boards (os aninhados compartilham a pasta imagens/) e as
     * citadas nas fotos do desfazer/refazer.
     */
    static Set<String> imagesInUse(Board root, EditHistory history) {
        Set<String> names = new HashSet<>();
        root.collectImageFiles(names);
        history.collectImageFiles(names);
        return names;
    }

    private List<Path> imageDirs() {
        List<Path> dirs = new ArrayList<>();
        if (workDir != null) {
            dirs.add(workDir);
        }
        if (saveFolder != null) {
            dirs.add(BoardImages.dirOf(saveFolder));
        }
        return dirs;
    }

    private Path workDir() throws IOException {
        if (workDir == null || !Files.isDirectory(workDir)) {
            workDir = Files.createTempDirectory("boardvisual-imagens-");
        }
        return workDir;
    }

    private void clearWorkDir() {
        if (workDir == null) {
            return;
        }
        try (Stream<Path> files = Files.list(workDir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException | UncheckedIOException e) {
            // Sobra na pasta temporária do sistema; não afeta o board.
        }
    }

    private void deleteWorkDir() {
        if (workDir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(workDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException | UncheckedIOException e) {
            // Idem.
        }
    }

    // ------------------------------------------------------------ carregar

    /**
     * Imagem de um card, carregada por completo (síncrono: o PDF tira o snapshot
     * logo em seguida). {@code null} se o arquivo não existe ou não abre — o
     * card mostra o aviso de imagem não encontrada. Só o sucesso fica no cache.
     */
    private Image load(String fileName) {
        Image cached = cache.get(fileName);
        if (cached != null) {
            return cached;
        }
        Path file = BoardImages.find(fileName, imageDirs()).orElse(null);
        if (file == null) {
            return null;
        }
        Image image = new Image(file.toUri().toString(), false);
        if (image.isError() || image.getWidth() <= 0) {
            return null;
        }
        cache.put(fileName, image);
        return image;
    }

    // ------------------------------------------------------------ inserir

    /** Ferramenta Imagem: escolhe arquivos e insere no centro da área visível. */
    public void chooseAndInsert() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Inserir imagem");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens (PNG, JPG, GIF, BMP)",
                ImageImporter.EXTENSIONS.stream().map(ext -> "*." + ext).toList()));
        if (lastChooserDirectory != null && lastChooserDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastChooserDirectory);
        }
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) {
            return;
        }
        lastChooserDirectory = files.get(0).getParentFile();
        insertFiles(files.stream().map(File::toPath).toList(), view.visibleCenter());
    }

    /**
     * Cola a imagem da área de transferência do sistema (ou arquivos de imagem
     * copiados no Explorer) no centro da área visível. Retorna false se não
     * havia imagem para colar. Quem decide entre isto e os cards copiados no
     * app é o {@link ClipboardController}.
     */
    public boolean paste() {
        return paste(null);
    }

    /** Como {@link #paste()}, centralizada em {@code center} (ponto do board; null = centro da área visível). */
    public boolean paste(Point2D center) {
        Point2D where = center != null ? center : view.visibleCenter();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            List<Path> files = clipboard.getFiles().stream().map(File::toPath)
                    .filter(ImageImporter::isSupportedFile).toList();
            if (!files.isEmpty()) {
                insertFiles(files, where);
                return true;
            }
        }
        if (clipboard.hasImage() && clipboard.getImage() != null) {
            insertImage(clipboard.getImage(), where);
            return true;
        }
        return false;
    }

    /** Se a área de transferência do sistema tem imagem, ou arquivos de imagem, para {@link #paste()}. */
    public boolean canPasteSystemImage() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()
                && clipboard.getFiles().stream().map(File::toPath).anyMatch(ImageImporter::isSupportedFile)) {
            return true;
        }
        return clipboard.hasImage();
    }

    // ------------------------------------------------------------ para o copiar/colar de cards

    /** Pastas onde as imagens do board atual são procuradas ({@link ClipboardController}). */
    @Override
    public List<Path> searchDirs() {
        return imageDirs();
    }

    /** A pasta de trabalho: onde entram as imagens que chegam de fora do save atual. */
    @Override
    public Path newImagesDir() throws IOException {
        return workDir();
    }

    /** Arrastar arquivos de imagem (ou uma imagem) para o canvas: entram onde forem soltos. */
    private void installDragAndDrop() {
        // Filtros: o arquivo pode ser solto em cima de um card (inclusive do texto dele).
        view.addEventFilter(DragEvent.DRAG_OVER, e -> {
            if (accepts(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.COPY);
                e.consume();
            }
        });
        view.addEventFilter(DragEvent.DRAG_DROPPED, e -> {
            Dragboard board = e.getDragboard();
            if (!accepts(board)) {
                return;
            }
            Point2D point = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            List<Path> files = board.hasFiles()
                    ? board.getFiles().stream().map(File::toPath).filter(ImageImporter::isSupportedFile).toList()
                    : List.of();
            Image image = files.isEmpty() && board.hasImage() ? board.getImage() : null;
            e.setDropCompleted(true);
            e.consume();
            // Fora do evento: libera o programa de origem (Explorer) antes de ler e copiar.
            Platform.runLater(() -> {
                if (!files.isEmpty()) {
                    insertFiles(files, point);
                } else if (image != null) {
                    insertImage(image, point);
                }
            });
        });
    }

    private static boolean accepts(Dragboard board) {
        if (board.hasFiles()) {
            return board.getFiles().stream().map(File::toPath).anyMatch(ImageImporter::isSupportedFile);
        }
        return board.hasImage();
    }

    private void insertFiles(List<Path> files, Point2D center) {
        List<ImageImporter.Imported> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path file : files) {
            try {
                imported.add(ImageImporter.importFile(file, workDir()));
            } catch (IOException e) {
                errors.add(e.getMessage());
            }
        }
        addCards(imported, center);
        if (!errors.isEmpty()) {
            showError(errors.size() == files.size() ? "Não foi possível inserir a imagem."
                    : "Algumas imagens não foram inseridas.", String.join("\n", errors));
        }
    }

    private void insertImage(Image image, Point2D center) {
        try {
            addCards(List.of(ImageImporter.importImage(toBufferedImage(image), workDir())), center);
        } catch (IOException | RuntimeException e) {
            showError("Não foi possível colar a imagem.", e.getMessage());
        }
    }

    /** Um card por imagem, em cascata a partir do centro; tudo num único passo do desfazer. */
    private void addCards(List<ImageImporter.Imported> images, Point2D center) {
        Board board = view.getBoard();
        if (board == null || images.isEmpty()) {
            return;
        }
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ImageImporter.Imported image = images.get(i);
            double[] size = initialSize(image.width(), image.height());
            cards.add(Card.createImage(image.fileName(), center.getX() + i * CASCADE_OFFSET,
                    center.getY() + i * CASCADE_OFFSET, size[0], size[1]));
        }
        history.perform(() -> cards.forEach(board::addCard));
        view.setSelection(cards);
        view.requestFocus();
    }

    /**
     * Tamanho do card ao nascer: a proporção da imagem, com o maior lado em no
     * máximo {@link #INITIAL_MAX_SIDE} (e no mínimo {@link #INITIAL_MIN_SIDE}).
     */
    static double[] initialSize(double width, double height) {
        double longest = Math.max(width, height);
        double scale = longest > INITIAL_MAX_SIDE ? INITIAL_MAX_SIDE / longest
                : longest < INITIAL_MIN_SIDE ? INITIAL_MIN_SIDE / longest : 1;
        return new double[] {width * scale, height * scale};
    }

    /**
     * Converte sem o módulo javafx.swing (como o BoardRenderer). Algumas imagens
     * copiadas no Windows chegam com o canal alfa todo zerado (ficariam
     * invisíveis): nesse caso a imagem é tratada como opaca.
     */
    static BufferedImage toBufferedImage(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width <= 0 || height <= 0 || image.getPixelReader() == null) {
            throw new IllegalArgumentException("A área de transferência não tem uma imagem válida.");
        }
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        boolean allTransparent = true;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                allTransparent = false;
                break;
            }
        }
        if (allTransparent) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] |= 0xFF000000;
            }
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("BoardVisual");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
