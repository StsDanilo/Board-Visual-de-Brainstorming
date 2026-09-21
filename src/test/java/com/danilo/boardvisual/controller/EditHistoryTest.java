package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditHistoryTest {

    private final Board board = new Board("teste");
    private final EditHistory history = new EditHistory(() -> board);

    @Test
    void undoAndRedoSequenceOfActions() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));
        history.perform(() -> card.setColor("#FFF1A8"));

        history.undo();
        assertEquals(Card.DEFAULT_COLOR, card.getColor());
        history.undo();
        assertTrue(board.getCards().isEmpty());
        assertFalse(history.canUndoProperty().get());

        history.redo();
        assertEquals(1, board.getCards().size());
        history.redo();
        assertEquals("#FFF1A8", board.getCards().get(0).getColor());
        assertFalse(history.canRedoProperty().get());
    }

    @Test
    void actionsWithoutChangesAreNotRecorded() {
        history.perform(() -> { });
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void longActionRecordedOnceWithCaptureAndRecord() {
        Card card = new Card(0, 0);
        board.addCard(card);

        BoardSnapshot before = history.capture();
        for (int x = 1; x <= 50; x++) {
            card.setX(x); // um arrasto gera muitos eventos
        }
        history.record(before);

        history.undo();
        assertEquals(0, card.getX());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void editSessionBecomesOneStep() {
        Card card = new Card(0, 0);
        board.addCard(card);

        history.beginEdit();
        card.setText("a");
        card.setText("ab");
        card.setText("abc");
        history.endEdit();

        history.undo();
        assertEquals("", card.getText());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void actionDuringEditSessionSplitsStepsInOrder() {
        Card card = new Card(0, 0);
        board.addCard(card);

        history.beginEdit();
        card.setText("digitado");                          // passo 1: texto
        history.perform(() -> card.setColor("#FFF1A8"));   // passo 2: cor
        card.setText("digitado depois");                   // passo 3: mais texto
        history.endEdit();

        history.undo();
        assertEquals("digitado", card.getText());
        assertEquals("#FFF1A8", card.getColor());
        history.undo();
        assertEquals(Card.DEFAULT_COLOR, card.getColor());
        assertEquals("digitado", card.getText());
        history.undo();
        assertEquals("", card.getText());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void undoDuringEditSessionCommitsTypingFirst() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));

        history.beginEdit();
        card.setText("em andamento");
        history.undo();                                    // desfaz só a digitação
        assertEquals("", card.getText());
        assertEquals(1, board.getCards().size());
        history.endEdit();
    }

    @Test
    void eachBoardHasItsOwnHistory() {
        Board[] current = {board};
        EditHistory nested = new EditHistory(() -> current[0]);
        Board child = new Board("filho");

        nested.perform(() -> board.addCard(new Card(0, 0)));   // no pai
        current[0] = child;
        nested.boardChanged();
        assertFalse(nested.canUndoProperty().get(), "o filho começa sem histórico");

        nested.perform(() -> child.addCard(new Card(0, 0)));   // no filho
        nested.undo();
        assertTrue(child.getCards().isEmpty());
        assertEquals(1, board.getCards().size(), "desfazer no filho não mexe no pai");

        current[0] = board;
        nested.boardChanged();
        assertTrue(nested.canUndoProperty().get(), "o histórico do pai continua lá");
        nested.undo();
        assertTrue(board.getCards().isEmpty());
    }

    @Test
    void newActionClearsRedo() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));
        history.undo();
        assertTrue(history.canRedoProperty().get());

        history.perform(() -> board.addCard(new Card(100, 100)));
        assertFalse(history.canRedoProperty().get());
    }
}
