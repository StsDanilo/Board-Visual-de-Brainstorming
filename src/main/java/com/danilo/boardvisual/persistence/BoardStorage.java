package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.persistence.BoardFileFormat.BoardData;
import com.danilo.boardvisual.persistence.BoardFileFormat.CardData;
import com.danilo.boardvisual.persistence.BoardFileFormat.ConnectionData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Salva e carrega boards. Um save é uma <b>pasta</b>:
 *
 * <pre>
 * MeuBoard/
 *   board.json     o board principal, com a árvore inteira de boards aninhados
 *   imagens/       um arquivo por imagem (só existe depois da primeira)
 * </pre>
 *
 * Também abre os saves antigos (versão 1): um único .json solto.
 */
public class BoardStorage {

    /** Nome do arquivo JSON dentro da pasta do save. */
    public static final String BOARD_FILE = "board.json";

    /**
     * Resultado de {@link #load}. {@code folder} é a pasta do save, ou
     * {@code null} quando o board veio de um .json antigo (que ainda não é um
     * save em pasta: o primeiro "salvar" precisa de uma pasta nova).
     */
    public record LoadedBoard(Board board, Path folder) {
        public boolean isLegacy() {
            return folder == null;
        }
    }

    /** O que existe no destino de um "salvar como". */
    public enum Target {
        /** Não existe ou é uma pasta vazia: pode gravar. */
        FREE,
        /** Já é um save (tem board.json): gravar substitui o board que está lá. */
        EXISTING_SAVE,
        /** Existe e tem outras coisas (ou é um arquivo): não é lugar para um save. */
        OCCUPIED
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void save(Board board, Path folder) throws IOException {
        save(board, folder, List.of());
    }

    /**
     * Grava o board na pasta do save, criando-a se preciso.
     *
     * @param imagesSource pasta de onde copiar as imagens para {@code folder/imagens}
     *                     (o "salvar como" passa a {@code imagens} do save atual);
     *                     {@code null} se não há o que copiar
     */
    public void save(Board board, Path folder, Path imagesSource) throws IOException {
        save(board, folder, imagesSource == null ? List.of() : List.of(imagesSource));
    }

    /**
     * Como {@link #save(Board, Path, Path)}, trazendo imagens de várias pastas
     * (ex.: a {@code imagens} do save atual e a pasta de trabalho com as
     * imagens inseridas desde o último salvamento).
     */
    public void save(Board board, Path folder, List<Path> imageSources) throws IOException {
        BoardData data = toData(board);
        Path absolute = folder.toAbsolutePath();
        if (Files.exists(absolute) && !Files.isDirectory(absolute)) {
            throw new IOException("Já existe um arquivo com o nome \"" + absolute.getFileName()
                    + "\"; escolha outro nome para a pasta do board.");
        }
        Files.createDirectories(absolute);

        // As imagens vão antes do JSON: se algo falhar no meio, nunca existe um
        // board.json apontando para uma imagem que não chegou.
        for (Path source : imageSources) {
            BoardImages.copyInto(source, absolute);
        }

        // Escreve num arquivo temporário e troca no fim: se algo falhar no
        // meio, o board.json anterior continua intacto.
        Path file = absolute.resolve(BOARD_FILE);
        Path temp = absolute.resolve(BOARD_FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Abre um board a partir de uma pasta de save, do {@code board.json} dentro
     * dela ou de um .json antigo (versão 1). Quem diferencia os dois últimos é a
     * {@code version} gravada no JSON, não o nome do arquivo.
     */
    public LoadedBoard load(Path source) throws IOException {
        boolean isFolder = Files.isDirectory(source);
        Path file = isFolder ? source.resolve(BOARD_FILE) : source;
        if (isFolder && !Files.isRegularFile(file)) {
            throw new IOException("A pasta não tem um " + BOARD_FILE
                    + "; ela não parece um board salvo pelo BoardVisual.");
        }

        BoardData data;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data = gson.fromJson(reader, BoardData.class);
        } catch (JsonParseException e) {
            throw new IOException("O arquivo não é um board válido.", e);
        }
        if (data == null) {
            throw new IOException("O arquivo está vazio.");
        }
        if (data.version() > BoardFileFormat.CURRENT_VERSION) {
            throw new IOException("O arquivo foi salvo por uma versão mais nova do BoardVisual.");
        }

        Path folder = null;
        if (data.version() >= BoardFileFormat.FIRST_FOLDER_VERSION) {
            // O nome fixo evita tratar como "pasta do save" uma pasta qualquer
            // (e depois limpar uma pasta imagens/ que não é nossa).
            if (!file.getFileName().toString().equalsIgnoreCase(BOARD_FILE)) {
                throw new IOException("Este arquivo é de um save em pasta e precisa se chamar " + BOARD_FILE
                        + ". Abra o " + BOARD_FILE + " dentro da pasta do board.");
            }
            folder = file.toAbsolutePath().getParent();
        }
        return new LoadedBoard(fromData(data, new HashSet<>()), folder);
    }

    /** Diz se {@code folder} serve de destino para um save (ver {@link Target}). */
    public static Target inspectTarget(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return Target.FREE;
        }
        if (!Files.isDirectory(folder)) {
            return Target.OCCUPIED;
        }
        if (Files.isRegularFile(folder.resolve(BOARD_FILE))) {
            return Target.EXISTING_SAVE;
        }
        try (Stream<Path> contents = Files.list(folder)) {
            return contents.findAny().isEmpty() ? Target.FREE : Target.OCCUPIED;
        }
    }

    // ------------------------------------------------------------ mapeamento

    private static BoardData toData(Board board) {
        List<CardData> cards = board.getCards().stream()
                .map(c -> new CardData(c.getId(), c.getX(), c.getY(), c.getWidth(), c.getHeight(),
                        c.getBaseWidth(), c.getBaseHeight(), c.isAutoFontSize() ? null : c.getFontSize(),
                        c.getText(), c.getColor(), c.getShape().name(),
                        c.getPanelMode().name(), c.getDetailText(), c.getListItemTexts(), c.getImageFile(),
                        c.hasLink() ? c.getLink() : null,
                        c.getBoardLink(),
                        c.isLocked() ? Boolean.TRUE : null, // destravado não grava o campo
                        c.hasChildBoard() ? toData(c.getChildBoard()) : null))
                .toList();
        List<ConnectionData> connections = board.getConnections().stream()
                .map(c -> new ConnectionData(c.getId(), c.getSource().getId(), c.getTarget().getId()))
                .toList();
        return new BoardData(BoardFileFormat.CURRENT_VERSION, board.getId(), board.getName(), cards, connections);
    }

    /**
     * @param seenBoardIds ids de board já usados na árvore. O id de um board é
     *                     único na árvore inteira (o link para outro board acha o
     *                     destino por ele); saves feitos antes disso podem ter ids
     *                     repetidos (duplicar um card-board com filhos reaproveitava
     *                     os ids dos filhos). O repetido ganha id novo.
     */
    private static Board fromData(BoardData data, Set<String> seenBoardIds) {
        boolean usableId = data.id() != null && !data.id().isBlank() && !seenBoardIds.contains(data.id());
        Board board = usableId ? new Board(data.id(), data.name()) : new Board(data.name());
        seenBoardIds.add(board.getId());

        if (data.cards() != null) {
            for (CardData cd : data.cards()) {
                if (cd == null || cd.id() == null) {
                    continue;
                }
                Card card = new Card(cd.id(), cd.x(), cd.y());
                CardShape shape = CardShape.fromName(cd.shape());
                card.setShape(shape);
                card.setWidth(cd.width() > 0 ? cd.width() : shape.getDefaultWidth());
                card.setHeight(cd.height() > 0 ? cd.height() : shape.getDefaultHeight());
                card.setBaseWidth(cd.baseWidth() != null && cd.baseWidth() > 0 ? cd.baseWidth() : card.getWidth());
                card.setBaseHeight(cd.baseHeight() != null && cd.baseHeight() > 0 ? cd.baseHeight() : card.getHeight());
                card.setFontSize(cd.fontSize() == null ? Card.AUTO_FONT_SIZE : cd.fontSize());
                card.setText(cd.text());
                card.setColor(cd.color());
                card.setPanelMode(PanelMode.fromName(cd.panelMode()));
                card.setDetailText(cd.detailText());
                if (cd.listItems() != null) {
                    card.setListItemTexts(cd.listItems().stream().map(t -> t == null ? "" : t).toList());
                }
                // Mantido mesmo se o arquivo não existir ou o nome for estranho: a tela
                // mostra o aviso de imagem não encontrada, e o dado não se perde.
                card.setImageFile(cd.imageFile());
                // Arquivo adulterado ou editado à mão: o que não for http(s) válido
                // (file:, javascript:, caminho do computador...) é descartado aqui.
                card.setLink(cd.link());
                if (cd.link() != null && !cd.link().isBlank() && !card.hasLink()) {
                    System.err.println("Link inválido descartado ao abrir o card " + cd.id());
                }
                // Mantido mesmo se o board de destino não existir mais: o marcador
                // aparece "quebrado", e o dado não se perde.
                card.setBoardLink(cd.boardLink());
                // Saves anteriores ao lock não têm o campo: card destravado.
                card.setLocked(Boolean.TRUE.equals(cd.locked()));
                if (cd.childBoard() != null) {
                    card.setChildBoard(fromData(cd.childBoard(), seenBoardIds));
                }
                board.addCard(card);
            }
        }

        if (data.connections() != null) {
            for (ConnectionData cd : data.connections()) {
                if (cd == null) {
                    continue;
                }
                var source = board.findCard(cd.sourceId());
                var target = board.findCard(cd.targetId());
                // Conexões apontando para cards inexistentes são ignoradas.
                if (source.isPresent() && target.isPresent()) {
                    Connection connection = cd.id() == null
                            ? new Connection(source.get(), target.get())
                            : new Connection(cd.id(), source.get(), target.get());
                    board.addConnection(connection);
                }
            }
        }
        return board;
    }
}
