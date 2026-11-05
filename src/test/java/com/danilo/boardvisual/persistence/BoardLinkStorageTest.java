package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salvar e abrir o link para outro board (campo {@code boardLink} do card). */
class BoardLinkStorageTest {

    @TempDir
    Path dir;

    private final BoardStorage storage = new BoardStorage();

    @Test
    void linkToABoardInAnotherBranchSurvivesSaveAndLoad() throws IOException {
        Board root = new Board("Principal");
        Card projectB = new Card("b", 0, 0);
        projectB.setText("Projeto B");
        projectB.setChildBoard(new Board("board-b", ""));
        Card costs = new Card("custos", 0, 0);
        costs.setText("Custos");
        costs.setChildBoard(new Board("board-custos", ""));
        projectB.getChildBoard().addCard(costs);
        Card projectA = new Card("a", 300, 0);
        projectA.setChildBoard(new Board("board-a", ""));
        Card budget = new Card("orcamento", 0, 0);
        budget.setBoardLink("board-custos");
        projectA.getChildBoard().addCard(budget);
        root.addCard(projectB);
        root.addCard(projectA);

        storage.save(root, dir.resolve("save"));
        Board loaded = storage.load(dir.resolve("save")).board();

        Card loadedBudget = loaded.findCard("a").orElseThrow().getChildBoard().findCard("orcamento").orElseThrow();
        assertEquals("board-custos", loadedBudget.getBoardLink());
        assertEquals("Principal › Projeto B › Custos",
                BoardTree.label(BoardTree.pathTo(loaded, loadedBudget.getBoardLink())));
    }

    @Test
    void cardsWithoutBoardLinkDoNotWriteTheField() throws IOException {
        Board root = new Board("Sem atalho");
        root.addCard(new Card(0, 0));

        storage.save(root, dir.resolve("save"));

        String json = Files.readString(dir.resolve("save").resolve(BoardStorage.BOARD_FILE));
        assertFalse(json.contains("boardLink"), json);
    }

    @Test
    void saveFromBeforeTheFieldOpensWithoutLinks() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("antigo"));
        Files.writeString(folder.resolve(BoardStorage.BOARD_FILE), """
                {
                  "version": 2,
                  "id": "raiz",
                  "name": "Antigo",
                  "cards": [
                    { "id": "c1", "x": 0, "y": 0, "width": 200, "height": 120,
                      "text": "card", "color": "#FFFFFF", "shape": "RECTANGLE", "link": "https://exemplo.com" }
                  ],
                  "connections": []
                }
                """, StandardCharsets.UTF_8);

        Card card = storage.load(folder).board().findCard("c1").orElseThrow();

        assertFalse(card.hasBoardLink());
        assertNull(card.getBoardLink());
        assertEquals("https://exemplo.com", card.getLink(), "o resto do card continua igual");
    }

    @Test
    void linkToABoardThatNoLongerExistsIsKept() throws IOException {
        Board root = new Board("Quebrado");
        Card card = new Card("c", 0, 0);
        card.setBoardLink("board-apagado");
        root.addCard(card);

        storage.save(root, dir.resolve("save"));
        Card loaded = storage.load(dir.resolve("save")).board().findCard("c").orElseThrow();

        assertEquals("board-apagado", loaded.getBoardLink(), "o id fica; o marcador aparece quebrado");
        assertFalse(BoardTree.contains(storage.load(dir.resolve("save")).board(), "board-apagado"));
    }

    @Test
    void repeatedBoardIdsFromOlderSavesGetNewIdsOnLoad() throws IOException {
        // Antes desta versão, duplicar um card-board com filhos repetia o id dos filhos.
        Path folder = Files.createDirectories(dir.resolve("repetido"));
        Files.writeString(folder.resolve(BoardStorage.BOARD_FILE), """
                {
                  "version": 2, "id": "raiz", "name": "Repetido",
                  "cards": [
                    { "id": "c1", "x": 0, "y": 0, "width": 200, "height": 120, "text": "Original",
                      "childBoard": { "version": 2, "id": "pai-1", "name": "", "connections": [],
                        "cards": [ { "id": "n", "x": 0, "y": 0, "width": 200, "height": 120, "text": "Neto",
                          "childBoard": { "version": 2, "id": "neto", "name": "", "cards": [], "connections": [] } } ] } },
                    { "id": "c2", "x": 300, "y": 0, "width": 200, "height": 120, "text": "Cópia",
                      "childBoard": { "version": 2, "id": "pai-2", "name": "", "connections": [],
                        "cards": [ { "id": "n", "x": 0, "y": 0, "width": 200, "height": 120, "text": "Neto",
                          "childBoard": { "version": 2, "id": "neto", "name": "", "cards": [], "connections": [] } } ] } }
                  ],
                  "connections": []
                }
                """, StandardCharsets.UTF_8);

        Board root = storage.load(folder).board();

        assertEquals(5, BoardTree.ids(root).size(), "nenhum id repetido na árvore");
        Board first = root.findCard("c1").orElseThrow().getChildBoard().getCards().get(0).getChildBoard();
        Board second = root.findCard("c2").orElseThrow().getChildBoard().getCards().get(0).getChildBoard();
        assertEquals("neto", first.getId(), "o primeiro (na ordem do arquivo) mantém o id");
        assertNotEquals("neto", second.getId());
        assertTrue(BoardTree.pathTo(root, "neto").get(1).card().getText().equals("Original"));
    }
}
