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
    void newActionClearsRedo() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));
        history.undo();
        assertTrue(history.canRedoProperty().get());

        history.perform(() -> board.addCard(new Card(100, 100)));
        assertFalse(history.canRedoProperty().get());
    }
}
