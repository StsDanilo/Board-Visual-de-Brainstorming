package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSnapshotTest {

    @Test
    void identicalBoardsProduceEqualSnapshots() {
        Board board = new Board("teste");
        board.addCard(new Card(10, 20));
        assertEquals(board.snapshot(), board.snapshot());
    }

    @Test
    void restoreUndoesChangesKeepingCardInstances() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        a.setText("antes");
        board.addCard(a);
        BoardSnapshot before = board.snapshot();

        a.setX(300);
        a.setText("depois");
        a.changeShape(CardShape.DIAMOND);
        a.setColor("#CFE2FF");
        assertNotEquals(before, board.snapshot());

        board.restore(before);

        assertEquals(before, board.snapshot());
        assertSame(a, board.getCards().get(0), "card existente é atualizado no lugar");
        assertEquals(0, a.getX());
        assertEquals("antes", a.getText());
        assertEquals(CardShape.RECTANGLE, a.getShape());
    }

    @Test
    void restoreBringsBackFontSizeAndManualSize() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        board.addCard(a);
        BoardSnapshot before = board.snapshot();

        a.resize(320, 90);
        a.setHeight(150);
        a.setFontSize(20);
        assertNotEquals(before, board.snapshot());

        board.restore(before);

        assertEquals(Card.DEFAULT_WIDTH, a.getBaseWidth());
        assertEquals(Card.DEFAULT_HEIGHT, a.getHeight());
        assertTrue(a.isAutoFontSize());
    }

    @Test
    void restoreBringsBackDeletedCardWithItsConnections() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        board.addCard(a);
        board.addCard(b);
        board.connect(a, b);
        BoardSnapshot before = board.snapshot();

        board.removeCard(b);
        assertTrue(board.getConnections().isEmpty());

        board.restore(before);

        assertEquals(before, board.snapshot());
        assertEquals(b.getId(), board.getConnections().get(0).getTarget().getId());
    }

    @Test
    void restoreRemovesCardsAddedAfterSnapshot() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        board.addCard(a);
        BoardSnapshot before = board.snapshot();

        Card added = new Card(500, 500);
        board.addCard(added);
        board.connect(a, added);

        board.restore(before);

        assertEquals(1, board.getCards().size());
        assertTrue(board.getConnections().isEmpty());
        assertEquals(before, board.snapshot());
    }
}
