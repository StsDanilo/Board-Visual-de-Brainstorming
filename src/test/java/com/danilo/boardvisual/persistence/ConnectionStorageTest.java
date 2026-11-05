package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salvar e abrir o texto e o estilo das setas (campos {@code label} e {@code style}). */
class ConnectionStorageTest {

    @TempDir
    Path dir;

    private final BoardStorage storage = new BoardStorage();

    @Test
    void everyStyleAndLabelSurviveSaveAndLoadAlsoInsideNestedBoards() throws IOException {
        Board root = new Board("Setas");
        Card[] cards = new Card[5];
        for (int i = 0; i < cards.length; i++) {
            cards[i] = new Card("c" + i, i * 300, 0);
            root.addCard(cards[i]);
        }
        ConnectionStyle[] styles = ConnectionStyle.values();
        for (int i = 0; i < styles.length; i++) {
            Connection connection = root.connect(cards[i], cards[i + 1], styles[i]).orElseThrow();
            connection.setLabel("texto " + i + " ação");
        }
        Board child = new Board("filho", "");
        Card x = new Card("x", 0, 0);
        Card y = new Card("y", 300, 0);
        child.addCard(x);
        child.addCard(y);
        child.connect(x, y, ConnectionStyle.LINE).orElseThrow().setLabel("dentro");
        Card holder = new Card("holder", 0, 300);
        holder.setChildBoard(child);
        root.addCard(holder);

        storage.save(root, dir.resolve("save"));
        Board loaded = storage.load(dir.resolve("save")).board();

        assertEquals(styles.length, loaded.getConnections().size());
        for (int i = 0; i < styles.length; i++) {
            Connection connection = loaded.getConnections().get(i);
            assertEquals("c" + i, connection.getSource().getId());
            assertEquals(styles[i], connection.getStyle());
            assertEquals("texto " + i + " ação", connection.getLabel());
        }
        Connection inside = loaded.findCard("holder").orElseThrow().getChildBoard().getConnections().get(0);
        assertEquals(ConnectionStyle.LINE, inside.getStyle());
        assertEquals("dentro", inside.getLabel());
    }

    @Test
    void defaultConnectionsDoNotWriteTheFieldsAndTheVersionStays() throws IOException {
        Board root = new Board("Padrão");
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        root.addCard(a);
        root.addCard(b);
        root.connect(a, b);

        storage.save(root, dir.resolve("save"));

        String json = Files.readString(dir.resolve("save").resolve(BoardStorage.BOARD_FILE));
        assertFalse(json.contains("\"label\""), json);
        assertFalse(json.contains("\"style\""), json);
        assertTrue(json.contains("\"version\": 2"), json);
    }

    @Test
    void nonDefaultConnectionWritesTheFields() throws IOException {
        Board root = new Board("Fora do padrão");
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        root.addCard(a);
        root.addCard(b);
        root.connect(a, b, ConnectionStyle.DOUBLE).orElseThrow().setLabel("leva a");

        storage.save(root, dir.resolve("save"));

        String json = Files.readString(dir.resolve("save").resolve(BoardStorage.BOARD_FILE));
        assertTrue(json.contains("\"label\": \"leva a\""), json);
        assertTrue(json.contains("\"style\": \"DOUBLE\""), json);
        assertTrue(json.contains("\"version\": 2"), json);
    }

    @Test
    void saveFromBeforeTheFieldsOpensWithPlainUnlabelledArrows() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("antigo"));
        Files.writeString(folder.resolve(BoardStorage.BOARD_FILE), """
                {
                  "version": 2,
                  "id": "raiz",
                  "name": "antigo",
                  "cards": [
                    {"id": "a", "x": 0, "y": 0, "width": 200, "height": 100, "text": "A", "color": "#FFFFFF", "shape": "RECTANGLE"},
                    {"id": "b", "x": 400, "y": 0, "width": 200, "height": 100, "text": "B", "color": "#FFFFFF", "shape": "RECTANGLE"},
                    {"id": "c", "x": 800, "y": 0, "width": 200, "height": 100, "text": "C", "color": "#FFFFFF", "shape": "RECTANGLE"}
                  ],
                  "connections": [
                    {"id": "ab", "sourceId": "a", "targetId": "b"},
                    {"id": "bc", "sourceId": "b", "targetId": "c", "label": "linha 1\\nlinha 2", "style": "CURVA"}
                  ]
                }
                """);

        Board board = storage.load(folder).board();

        Connection ab = board.findConnection("ab").orElseThrow();
        assertEquals("", ab.getLabel());
        assertEquals(ConnectionStyle.ARROW, ab.getStyle());
        Connection bc = board.findConnection("bc").orElseThrow();
        assertEquals("linha 1 linha 2", bc.getLabel(), "texto de uma linha só");
        assertEquals(ConnectionStyle.ARROW, bc.getStyle(), "estilo desconhecido vira seta simples");
    }

    @Test
    void legacySingleJsonFileOpensWithPlainArrows() throws IOException {
        Path file = dir.resolve("antigo.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "name": "bem antigo",
                  "cards": [
                    {"id": "a", "x": 0, "y": 0, "width": 200, "height": 100, "text": "A", "color": "#FFFFFF", "shape": "RECTANGLE"},
                    {"id": "b", "x": 400, "y": 0, "width": 200, "height": 100, "text": "B", "color": "#FFFFFF", "shape": "RECTANGLE"}
                  ],
                  "connections": [ {"id": "ab", "sourceId": "a", "targetId": "b"} ]
                }
                """);

        Connection ab = storage.load(file).board().findConnection("ab").orElseThrow();
        assertEquals("", ab.getLabel());
        assertEquals(ConnectionStyle.ARROW, ab.getStyle());
    }
}
