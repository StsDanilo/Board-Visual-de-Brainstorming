package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardStorageTest {

    private final BoardStorage storage = new BoardStorage();

    @Test
    void savesAndLoadsBoard(@TempDir Path dir) throws IOException {
        Board board = new Board("Ideias");
        Card a = new Card(10, 20);
        a.setText("Primeira ideia\ncom acentuação");
        a.setColor("#FFE8A3");
        Card b = new Card(400, 250);
        b.setWidth(260);
        board.addCard(a);
        board.addCard(b);
        board.connect(a, b);

        Path file = dir.resolve("board.json");
        storage.save(board, file);
        Board loaded = storage.load(file);

        assertEquals(board.getId(), loaded.getId());
        assertEquals("Ideias", loaded.getName());
        assertEquals(2, loaded.getCards().size());

        Card loadedA = loaded.findCard(a.getId()).orElseThrow();
        assertEquals(10, loadedA.getX());
        assertEquals(20, loadedA.getY());
        assertEquals("Primeira ideia\ncom acentuação", loadedA.getText());
        assertEquals("#FFE8A3", loadedA.getColor());
        assertEquals(260, loaded.findCard(b.getId()).orElseThrow().getWidth());

        Connection connection = loaded.getConnections().get(0);
        assertSame(loadedA, connection.getSource());
        assertEquals(b.getId(), connection.getTarget().getId());
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

        Board loaded = storage.load(file);

        Card card = loaded.findCard("c1").orElseThrow();
        assertEquals(Card.DEFAULT_WIDTH, card.getWidth());
        assertEquals(Card.DEFAULT_COLOR, card.getColor());
        assertEquals("", card.getText());
        assertEquals(0, loaded.getConnections().size());
    }

    @Test
    void rejectsInvalidJson(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("quebrado.json");
        Files.writeString(file, "{ isso não é json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.load(file));
    }
}
