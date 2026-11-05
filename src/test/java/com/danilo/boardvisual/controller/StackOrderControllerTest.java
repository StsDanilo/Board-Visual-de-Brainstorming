package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.StackMove;
import com.danilo.boardvisual.persistence.BoardStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partes da ordem de empilhamento que não precisam de tela: passos do
 * desfazer, duplicar, excluir, salvar/abrir.
 */
class StackOrderControllerTest {

    private final Board board = new Board("pilha");
    private final EditHistory history = new EditHistory(() -> board);
    private final Card a = add("a");
    private final Card b = add("b");
    private final Card c = add("c");

    private Card add(String text) {
        Card card = new Card(0, 0);
        card.setText(text);
        board.addCard(card);
        return card;
    }

    private String order() {
        StringBuilder text = new StringBuilder();
        board.getCards().forEach(card -> text.append(card.getText()));
        return text.toString();
    }

    @Test
    void eachMoveIsOneUndoableStep() {
        assertTrue(StackOrderController.reorder(history, board, List.of(a), StackMove.TO_FRONT));
        assertTrue(StackOrderController.reorder(history, board, List.of(c), StackMove.BACKWARD));
        assertEquals("cba", order());

        history.undo();
        assertEquals("bca", order(), "desfazer o enviar para trás");
        history.undo();
        assertEquals("abc", order(), "desfazer o trazer para o topo");
        assertFalse(history.canUndoProperty().get());
        history.redo();
        assertEquals("bca", order());
    }

    @Test
    void moveWithoutChangeCreatesNoStep() {
        assertFalse(StackOrderController.reorder(history, board, List.of(c), StackMove.FORWARD), "já no topo");
        assertFalse(StackOrderController.reorder(history, board, List.of(a), StackMove.TO_BACK), "já no fundo");
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void duplicatePutsTheCopiesOnTopInTheOriginalOrder() {
        // Selecionados "de cima para baixo": as cópias ainda saem na ordem da pilha.
        List<Card> copies = SelectionActionsController.duplicate(history, board, List.of(c, a));

        assertEquals("abcac", order());
        assertEquals(List.of(board.getCards().get(3), board.getCards().get(4)), copies);
        history.undo();
        assertEquals("abc", order());
    }

    @Test
    void deletingAndUndoingKeepsTheOrder() {
        StackOrderController.reorder(history, board, List.of(a), StackMove.TO_FRONT);
        history.perform(() -> board.removeCard(c));
        assertEquals("ba", order());

        history.undo();
        assertEquals("bca", order(), "o card excluído volta ao seu lugar");
    }

    @Test
    void movingACardDoesNotChangeTheOrder() {
        history.perform(() -> {
            a.setX(500);
            a.setY(500);
        });
        history.undo();
        assertEquals("abc", order());
        history.redo();
        assertEquals("abc", order());
    }

    @Test
    void saveAndOpenKeepTheOrder(@TempDir Path dir) throws IOException {
        StackOrderController.reorder(history, board, List.of(a), StackMove.TO_FRONT);
        Card holder = add("filho");
        Board child = new Board("");
        Card x = new Card(0, 0);
        x.setText("x");
        Card y = new Card(0, 0);
        y.setText("y");
        child.addCard(x);
        child.addCard(y);
        child.reorder(List.of(y), StackMove.TO_BACK);
        holder.setChildBoard(child);
        BoardStorage storage = new BoardStorage();
        Path folder = dir.resolve("ordem");

        storage.save(board, folder);
        Board loaded = storage.load(folder).board();

        assertEquals(List.of("b", "c", "a", "filho"), loaded.getCards().stream().map(Card::getText).toList());
        Board loadedChild = loaded.getCards().get(3).getChildBoard();
        assertEquals(List.of("y", "x"), loadedChild.getCards().stream().map(Card::getText).toList());
    }
}
