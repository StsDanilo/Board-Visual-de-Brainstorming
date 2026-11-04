package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.persistence.BoardStorage.LoadedBoard;
import com.danilo.boardvisual.persistence.BoardStorage.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardStorageTest {

    /** Save antigo (versão 1): um .json solto, no formato de antes do salvamento em pasta. */
    private static final String OLD_FORMAT_JSON = """
            {
              "version": 1,
              "id": "b-antigo",
              "name": "Antigo",
              "cards": [
                { "id": "c1", "x": 5, "y": 6, "text": "primeiro", "shape": "ELLIPSE" },
                { "id": "c2", "x": 400, "y": 6, "text": "segundo",
                  "childBoard": { "version": 1, "id": "b-filho", "name": "",
                                  "cards": [ { "id": "d1", "x": 1, "y": 2, "text": "dentro" } ] } }
              ],
              "connections": [ { "id": "k1", "sourceId": "c1", "targetId": "c2" } ]
            }
            """;

    private final BoardStorage storage = new BoardStorage();

    private Board loadBoard(Path source) throws IOException {
        return storage.load(source).board();
    }

    @Test
    void savesAndLoadsBoard(@TempDir Path dir) throws IOException {
        Board board = new Board("Ideias");
        Card a = new Card(10, 20);
        a.setText("Primeira ideia\ncom acentuação");
        a.setColor("#FFE8A3");
        Card b = new Card(400, 250);
        b.setWidth(260);
        b.setShape(CardShape.ELLIPSE);
        board.addCard(a);
        board.addCard(b);
        board.connect(a, b);

        Path folder = dir.resolve("Ideias");
        storage.save(board, folder);
        Board loaded = loadBoard(folder);

        assertEquals(board.getId(), loaded.getId());
        assertEquals("Ideias", loaded.getName());
        assertEquals(2, loaded.getCards().size());

        Card loadedA = loaded.findCard(a.getId()).orElseThrow();
        assertEquals(10, loadedA.getX());
        assertEquals(20, loadedA.getY());
        assertEquals("Primeira ideia\ncom acentuação", loadedA.getText());
        assertEquals("#FFE8A3", loadedA.getColor());
        assertEquals(260, loaded.findCard(b.getId()).orElseThrow().getWidth());
        assertEquals(CardShape.ELLIPSE, loaded.findCard(b.getId()).orElseThrow().getShape());

        Connection connection = loaded.getConnections().get(0);
        assertSame(loadedA, connection.getSource());
        assertEquals(b.getId(), connection.getTarget().getId());
    }

    @Test
    void saveIsAFolderWithBoardJsonAndNoImagesFolderYet(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("MeuBoard");
        storage.save(new Board("MeuBoard"), folder);

        assertTrue(Files.isDirectory(folder));
        assertTrue(Files.isRegularFile(folder.resolve("board.json")));
        assertTrue(Files.readString(folder.resolve("board.json"), StandardCharsets.UTF_8).contains("\"version\": 2"));
        assertFalse(Files.exists(folder.resolve("imagens")), "a pasta de imagens só nasce com a primeira imagem");
        try (Stream<Path> contents = Files.list(folder)) {
            assertEquals(List.of("board.json"), contents.map(p -> p.getFileName().toString()).toList(),
                    "sem sobra de arquivo temporário");
        }
    }

    @Test
    void createsMissingParentFolders(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("a").resolve("b").resolve("MeuBoard");
        storage.save(new Board("MeuBoard"), folder);
        assertTrue(Files.isRegularFile(folder.resolve("board.json")));
    }

    @Test
    void opensFromTheFolderOrFromItsBoardJson(@TempDir Path dir) throws IOException {
        Board board = new Board("Ideias");
        board.addCard(new Card(1, 2));
        Path folder = dir.resolve("Ideias");
        storage.save(board, folder);

        LoadedBoard fromFolder = storage.load(folder);
        LoadedBoard fromFile = storage.load(folder.resolve("board.json"));

        assertEquals(board.snapshot(), fromFolder.board().snapshot());
        assertEquals(board.snapshot(), fromFile.board().snapshot());
        assertFalse(fromFolder.isLegacy());
        assertEquals(folder.toAbsolutePath(), fromFolder.folder());
        assertEquals(folder.toAbsolutePath(), fromFile.folder());
    }

    @Test
    void savingAgainReplacesBoardJsonWithoutLeftovers(@TempDir Path dir) throws IOException {
        Board board = new Board("Ideias");
        Path folder = dir.resolve("Ideias");
        storage.save(board, folder);

        board.addCard(new Card(1, 2));
        storage.save(board, folder);

        assertEquals(1, loadBoard(folder).getCards().size());
        try (Stream<Path> contents = Files.list(folder)) {
            assertEquals(1, contents.count());
        }
    }

    @Test
    void refusesToSaveOverARegularFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ocupado");
        Files.writeString(file, "qualquer coisa");
        assertThrows(IOException.class, () -> storage.save(new Board("x"), file));
        assertEquals("qualquer coisa", Files.readString(file));
    }

    @Test
    void ignoresMissingFieldsAndDanglingConnections(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("antigo.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "name": "Antigo",
                  "cards": [ { "id": "c1", "x": 5, "y": 6 } ],
                  "connections": [ { "sourceId": "c1", "targetId": "nao-existe" } ]
                }
                """, StandardCharsets.UTF_8);

        Board loaded = loadBoard(file);

        Card card = loaded.findCard("c1").orElseThrow();
        assertEquals(Card.DEFAULT_WIDTH, card.getWidth());
        assertEquals(Card.DEFAULT_COLOR, card.getColor());
        assertEquals("", card.getText());
        assertEquals(PanelMode.NONE, card.getPanelMode());
        assertEquals(0, loaded.getConnections().size());
        // Sem tamanho definido à mão nem fonte: usa o próprio tamanho e fonte automática.
        assertEquals(Card.DEFAULT_WIDTH, card.getBaseWidth());
        assertEquals(Card.DEFAULT_HEIGHT, card.getBaseHeight());
        assertEquals(true, card.isAutoFontSize());
    }

    @Test
    void savesAndLoadsFontSizeAndManualSize(@TempDir Path dir) throws IOException {
        Board board = new Board("Fontes");
        Card grown = new Card(0, 0);
        grown.resize(300, 100);
        grown.setHeight(180); // cresceu por causa do texto
        grown.setFontSize(18);
        Card auto = new Card(400, 0);
        board.addCard(grown);
        board.addCard(auto);

        Path folder = dir.resolve("fontes");
        storage.save(board, folder);
        Board loaded = loadBoard(folder);

        Card loadedGrown = loaded.findCard(grown.getId()).orElseThrow();
        assertEquals(300, loadedGrown.getBaseWidth());
        assertEquals(100, loadedGrown.getBaseHeight());
        assertEquals(180, loadedGrown.getHeight());
        assertEquals(18, loadedGrown.getFontSize());
        assertEquals(true, loaded.findCard(auto.getId()).orElseThrow().isAutoFontSize());
    }

    @Test
    void savesAndLoadsPanelContent(@TempDir Path dir) throws IOException {
        Board board = new Board("Painéis");
        Card text = new Card(0, 0);
        text.setPanelMode(PanelMode.TEXT);
        text.setDetailText("Detalhe longo\ncom várias linhas");
        Card list = new Card(300, 0);
        list.setPanelMode(PanelMode.LIST);
        list.addListItem("primeiro");
        list.addListItem("segundo");
        board.addCard(text);
        board.addCard(list);

        Path folder = dir.resolve("paineis");
        storage.save(board, folder);
        Board loaded = loadBoard(folder);

        Card loadedText = loaded.findCard(text.getId()).orElseThrow();
        assertEquals(PanelMode.TEXT, loadedText.getPanelMode());
        assertEquals("Detalhe longo\ncom várias linhas", loadedText.getDetailText());
        Card loadedList = loaded.findCard(list.getId()).orElseThrow();
        assertEquals(PanelMode.LIST, loadedList.getPanelMode());
        assertEquals(List.of("primeiro", "segundo"), loadedList.getListItemTexts());
    }

    @Test
    void savesAndLoadsNestedBoards(@TempDir Path dir) throws IOException {
        Board root = new Board("Principal");
        Card level2 = new Card(0, 0);
        level2.setText("Nível 2");
        level2.setChildBoard(new Board(""));
        root.addCard(level2);
        Card level3 = new Card(50, 50);
        level3.setText("Nível 3");
        level3.setChildBoard(new Board(""));
        level2.getChildBoard().addCard(level3);
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        level3.getChildBoard().addCard(a);
        level3.getChildBoard().addCard(b);
        level3.getChildBoard().connect(a, b);

        Path folder = dir.resolve("aninhado");
        storage.save(root, folder);
        Board loaded = loadBoard(folder);

        Card loaded2 = loaded.getCards().get(0);
        Card loaded3 = loaded2.getChildBoard().getCards().get(0);
        assertEquals("Nível 3", loaded3.getText());
        assertEquals(level3.getChildBoard().getId(), loaded3.getChildBoard().getId());
        assertEquals(2, loaded3.getChildBoard().getCards().size());
        assertEquals(1, loaded3.getChildBoard().getConnections().size());
        assertEquals(root.snapshot(), loaded.snapshot());
    }

    // ------------------------------------------------------------ saves antigos

    @Test
    void opensOldLooseJsonAsLegacy(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("antigo.json");
        Files.writeString(file, OLD_FORMAT_JSON, StandardCharsets.UTF_8);

        LoadedBoard loaded = storage.load(file);

        assertTrue(loaded.isLegacy());
        assertNull(loaded.folder());
        assertEquals("Antigo", loaded.board().getName());
        assertEquals(2, loaded.board().getCards().size());
        assertEquals(CardShape.ELLIPSE, loaded.board().findCard("c1").orElseThrow().getShape());
        assertEquals("dentro", loaded.board().findCard("c2").orElseThrow().getChildBoard().getCards().get(0).getText());
        assertEquals(1, loaded.board().getConnections().size());
    }

    @Test
    void savesAnOldBoardInTheNewFormatWithoutTouchingTheOldFile(@TempDir Path dir) throws IOException {
        Path oldFile = dir.resolve("antigo.json");
        Files.writeString(oldFile, OLD_FORMAT_JSON, StandardCharsets.UTF_8);
        byte[] oldBytes = Files.readAllBytes(oldFile);
        Board board = loadBoard(oldFile);

        Path folder = dir.resolve("Novo");
        storage.save(board, folder);

        assertArrayEquals(oldBytes, Files.readAllBytes(oldFile), "o .json antigo não é alterado");
        assertTrue(Files.readString(folder.resolve("board.json"), StandardCharsets.UTF_8).contains("\"version\": 2"));
        LoadedBoard reopened = storage.load(folder);
        assertFalse(reopened.isLegacy());
        assertEquals(board.snapshot(), reopened.board().snapshot());
        assertEquals(board.getId(), reopened.board().getId());
    }

    @Test
    void versionOneFileIsOldEvenWhenNamedBoardJson(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("board.json");
        Files.writeString(file, OLD_FORMAT_JSON, StandardCharsets.UTF_8);

        LoadedBoard loaded = storage.load(file);

        assertTrue(loaded.isLegacy(), "quem decide é a versão gravada, não o nome do arquivo");
        assertEquals(2, loaded.board().getCards().size());
    }

    // ------------------------------------------------------------ arquivos inválidos

    @Test
    void rejectsInvalidJson(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("quebrado.json");
        Files.writeString(file, "{ isso não é json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.load(file));
    }

    @Test
    void rejectsInvalidJsonInsideAFolder(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectory(dir.resolve("quebrado"));
        Files.writeString(folder.resolve("board.json"), "{ isso não é json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.load(folder));
        assertThrows(IOException.class, () -> storage.load(folder.resolve("board.json")));
    }

    @Test
    void rejectsEmptyBoardJson(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectory(dir.resolve("vazio"));
        Files.writeString(folder.resolve("board.json"), "", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> storage.load(folder));
        assertEquals("O arquivo está vazio.", error.getMessage());
    }

    @Test
    void rejectsIncompleteFolderWithoutBoardJson(@TempDir Path dir) throws IOException {
        Path empty = Files.createDirectory(dir.resolve("vazia"));
        assertThrows(IOException.class, () -> storage.load(empty));

        Path onlyImages = Files.createDirectories(dir.resolve("soImagens").resolve("imagens"));
        Files.write(onlyImages.resolve("a.png"), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> storage.load(onlyImages.getParent()));
    }

    @Test
    void rejectsMissingPath(@TempDir Path dir) {
        assertThrows(IOException.class, () -> storage.load(dir.resolve("nao-existe")));
    }

    @Test
    void rejectsFolderFormatFileThatIsNotNamedBoardJson(@TempDir Path dir) throws IOException {
        Path saved = dir.resolve("Ideias");
        storage.save(new Board("Ideias"), saved);
        Path moved = Files.copy(saved.resolve("board.json"), dir.resolve("copia.json"));

        assertThrows(IOException.class, () -> storage.load(moved));
    }

    @Test
    void rejectsFileFromANewerVersion(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectory(dir.resolve("futuro"));
        Files.writeString(folder.resolve("board.json"), "{ \"version\": 99, \"name\": \"x\" }", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.load(folder));
    }

    // ------------------------------------------------------------ imagens e segurança

    @Test
    void saveAsCopiesTheWholeImagesFolder(@TempDir Path dir) throws IOException {
        Path current = dir.resolve("Atual");
        storage.save(new Board("Atual"), current);
        Path images = Files.createDirectories(BoardImages.dirOf(current));
        Files.write(images.resolve("a.png"), new byte[]{1, 2, 3});
        Files.write(images.resolve("b.png"), new byte[]{4, 5});

        Path copy = dir.resolve("Copia");
        storage.save(new Board("Copia"), copy, images);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(copy.resolve("imagens").resolve("a.png")));
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(copy.resolve("imagens").resolve("b.png")));
        assertTrue(Files.isRegularFile(copy.resolve("board.json")));
        assertTrue(Files.isRegularFile(images.resolve("a.png")), "o save original continua com as imagens");
    }

    @Test
    void imagesAreCopiedBeforeTheJsonIsWritten(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("origem"));
        Files.write(source.resolve("a.png"), new byte[]{1, 2, 3});
        Path folder = dir.resolve("Destino");
        Files.createDirectories(folder.resolve("board.json.tmp")); // faz a gravação do JSON falhar

        assertThrows(IOException.class, () -> storage.save(new Board("Destino"), folder, source));

        assertTrue(Files.isRegularFile(folder.resolve("imagens").resolve("a.png")), "a imagem já tinha sido copiada");
        assertFalse(Files.exists(folder.resolve("board.json")), "e não ficou um JSON apontando para ela");
    }

    @Test
    void failedWriteKeepsThePreviousBoardJson(@TempDir Path dir) throws IOException {
        Board board = new Board("Ideias");
        board.addCard(new Card(1, 2));
        Path folder = dir.resolve("Ideias");
        storage.save(board, folder);
        byte[] before = Files.readAllBytes(folder.resolve("board.json"));

        board.addCard(new Card(3, 4));
        Files.createDirectories(folder.resolve("board.json.tmp")); // faz a gravação falhar
        assertThrows(IOException.class, () -> storage.save(board, folder));

        assertArrayEquals(before, Files.readAllBytes(folder.resolve("board.json")));
        assertEquals(1, loadBoard(folder).getCards().size());
    }

    @Test
    void savingInTheSameFolderKeepsItsImages(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("Ideias");
        storage.save(new Board("Ideias"), folder);
        Path images = Files.createDirectories(BoardImages.dirOf(folder));
        Files.write(images.resolve("a.png"), new byte[]{1, 2, 3});

        storage.save(new Board("Ideias"), folder, images);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(images.resolve("a.png")));
        try (Stream<Path> contents = Files.list(images)) {
            assertEquals(1, contents.count());
        }
    }

    // ------------------------------------------------------------ cards de imagem

    @Test
    void savesAndLoadsAnImageCardWithItsFile(@TempDir Path dir) throws IOException {
        Path pending = Files.createDirectories(dir.resolve("trabalho"));
        Files.write(pending.resolve("abc.png"), new byte[]{9, 8, 7});
        Board board = new Board("Fotos");
        Card photo = Card.createImage("abc.png", 100, 100, 320, 180);
        photo.setText("legenda");
        board.addCard(photo);
        Path folder = dir.resolve("Fotos");

        storage.save(board, folder, List.of(pending));
        Card loaded = loadBoard(folder).findCard(photo.getId()).orElseThrow();

        assertEquals("abc.png", loaded.getImageFile());
        assertEquals("legenda", loaded.getText());
        assertEquals(320, loaded.getBaseWidth());
        assertEquals(180, loaded.getBaseHeight());
        assertArrayEquals(new byte[]{9, 8, 7}, Files.readAllBytes(folder.resolve("imagens").resolve("abc.png")));
        String json = Files.readString(folder.resolve("board.json"));
        assertTrue(json.contains("\"imageFile\": \"abc.png\""), "o JSON guarda só o nome do arquivo");
    }

    @Test
    void cardsWithoutImageDoNotWriteTheField(@TempDir Path dir) throws IOException {
        Board board = new Board("Sem");
        board.addCard(new Card(1, 2));
        Path folder = dir.resolve("Sem");

        storage.save(board, folder);

        assertFalse(Files.readString(folder.resolve("board.json")).contains("imageFile"));
    }

    @Test
    void previousSaveWithoutTheImageFieldOpens(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectories(dir.resolve("Anterior"));
        Files.writeString(folder.resolve("board.json"), """
                { "version": 2, "id": "b1", "name": "Anterior",
                  "cards": [ { "id": "c1", "x": 5, "y": 6, "width": 200, "height": 120, "text": "oi" } ],
                  "connections": [] }
                """, StandardCharsets.UTF_8);

        Card card = loadBoard(folder).findCard("c1").orElseThrow();

        assertFalse(card.hasImage());
        assertNull(card.getImageFile());
        assertEquals("oi", card.getText());
    }

    @Test
    void missingImageFileDoesNotBreakAndKeepsTheReference(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectories(dir.resolve("Faltando"));
        Files.writeString(folder.resolve("board.json"), """
                { "version": 2, "id": "b1", "name": "Faltando",
                  "cards": [ { "id": "c1", "x": 0, "y": 0, "width": 300, "height": 200,
                               "baseWidth": 300, "baseHeight": 200, "imageFile": "sumiu.png" },
                             { "id": "c2", "x": 0, "y": 0, "imageFile": "../fora-da-pasta.png" } ],
                  "connections": [] }
                """, StandardCharsets.UTF_8);

        Board board = loadBoard(folder);

        assertEquals("sumiu.png", board.findCard("c1").orElseThrow().getImageFile());
        assertEquals("../fora-da-pasta.png", board.findCard("c2").orElseThrow().getImageFile());
        assertTrue(BoardImages.find("sumiu.png", List.of(BoardImages.dirOf(folder))).isEmpty());
        // Salvar de novo não perde a referência (a imagem pode voltar para a pasta).
        storage.save(board, folder);
        assertEquals("sumiu.png", loadBoard(folder).findCard("c1").orElseThrow().getImageFile());
    }

    @Test
    void imagesComeFromSeveralSources(@TempDir Path dir) throws IOException {
        Path current = Files.createDirectories(dir.resolve("Atual").resolve("imagens"));
        Files.write(current.resolve("antiga.png"), new byte[]{1});
        Path pending = Files.createDirectories(dir.resolve("trabalho"));
        Files.write(pending.resolve("nova.png"), new byte[]{2});
        Path copy = dir.resolve("Copia");

        storage.save(new Board("Copia"), copy, List.of(current, pending));

        assertArrayEquals(new byte[]{1}, Files.readAllBytes(copy.resolve("imagens").resolve("antiga.png")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(copy.resolve("imagens").resolve("nova.png")));
    }

    @Test
    void inspectsTheTargetOfASaveAs(@TempDir Path dir) throws IOException {
        assertEquals(Target.FREE, BoardStorage.inspectTarget(dir.resolve("nao-existe")));
        assertEquals(Target.FREE, BoardStorage.inspectTarget(Files.createDirectory(dir.resolve("vazia"))));

        Path saved = dir.resolve("Ideias");
        storage.save(new Board("Ideias"), saved);
        assertEquals(Target.EXISTING_SAVE, BoardStorage.inspectTarget(saved));

        Path other = Files.createDirectory(dir.resolve("documentos"));
        Files.writeString(other.resolve("tese.docx"), "x");
        assertEquals(Target.OCCUPIED, BoardStorage.inspectTarget(other));

        Path file = Files.writeString(dir.resolve("arquivo.txt"), "x");
        assertEquals(Target.OCCUPIED, BoardStorage.inspectTarget(file));
    }
}
