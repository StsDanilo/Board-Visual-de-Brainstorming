package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salvar e abrir o card travado (campo {@code locked}). */
class LockStorageTest {

    @TempDir
    Path dir;

    private final BoardStorage storage = new BoardStorage();

    @Test
    void lockedCardSurvivesSaveAndLoadAlsoInsideNestedBoards() throws IOException {
        Board root = new Board("Travas");
        Card locked = new Card("travado", 0, 0);
        locked.setLocked(true);
        Card free = new Card("livre", 300, 0);
        Card holder = new Card("holder", 600, 0);
        holder.setChildBoard(new Board("filho", ""));
        Card inside = new Card("dentro", 0, 0);
        inside.setLocked(true);
        holder.getChildBoard().addCard(inside);
        root.addCard(locked);
        root.addCard(free);
        root.addCard(holder);

        storage.save(root, dir.resolve("save"));
        Board loaded = storage.load(dir.resolve("save")).board();

        assertTrue(loaded.findCard("travado").orElseThrow().isLocked());
        assertFalse(loaded.findCard("livre").orElseThrow().isLocked());
        assertTrue(loaded.findCard("holder").orElseThrow().getChildBoard().findCard("dentro").orElseThrow().isLocked());
    }

    @Test
    void unlockedCardsDoNotWriteTheField() throws IOException {
        Board root = new Board("Sem trava");
        root.addCard(new Card(0, 0));

        storage.save(root, dir.resolve("save"));

        String json = Files.readString(dir.resolve("save").resolve(BoardStorage.BOARD_FILE));
        assertFalse(json.contains("locked"), json);
    }

    @Test
    void lockedCardWritesTheFieldWithoutChangingTheVersion() throws IOException {
        Board root = new Board("Com trava");
        Card card = new Card(0, 0);
        card.setLocked(true);
        root.addCard(card);

        storage.save(root, dir.resolve("save"));

        String json = Files.readString(dir.resolve("save").resolve(BoardStorage.BOARD_FILE));
        assertTrue(json.contains("\"locked\": true"), json);
        assertTrue(json.contains("\"version\": 2"), json);
    }

    @Test
    void saveFromBeforeTheFieldOpensUnlocked() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("antigo"));
        Files.writeString(folder.resolve(BoardStorage.BOARD_FILE), """
                {
                  "version": 2,
                  "id": "raiz",
                  "name": "Antigo",
                  "cards": [
                    { "id": "c1", "x": 0, "y": 0, "width": 200, "height": 120,
                      "text": "card", "color": "#FFFFFF", "shape": "RECTANGLE" },
                    { "id": "c2", "x": 50, "y": 50, "width": 200, "height": 120,
                      "text": "por cima", "color": "#FFFFFF", "shape": "RECTANGLE" }
                  ],
                  "connections": []
                }
                """, StandardCharsets.UTF_8);

        Board board = storage.load(folder).board();

        assertFalse(board.findCard("c1").orElseThrow().isLocked());
        assertFalse(board.findCard("c2").orElseThrow().isLocked());
        assertEquals("c2", board.getCards().get(1).getId(), "a ordem do arquivo é a ordem de empilhamento");
    }
}
