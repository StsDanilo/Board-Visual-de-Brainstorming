package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @Test
    void connectsTwoCardsOnlyOnce() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        board.addCard(a);
        board.addCard(b);

        assertTrue(board.connect(a, b).isPresent());
        assertFalse(board.connect(a, b).isPresent(), "conexão repetida");
        assertTrue(board.connect(b, a).isPresent(), "sentido oposto é outra seta");
        assertEquals(2, board.getConnections().size());
    }

    @Test
    void rejectsInvalidConnections() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        Card outside = new Card(0, 0);
        board.addCard(a);

        assertFalse(board.connect(a, a).isPresent(), "card ligado a ele mesmo");
        assertFalse(board.connect(a, outside).isPresent(), "card fora do board");
        assertTrue(board.getConnections().isEmpty());
    }

    @Test
    void removingCardRemovesItsConnections() {
        Board board = new Board("teste");
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        Card c = new Card(600, 0);
        board.addCard(a);
        board.addCard(b);
        board.addCard(c);
        board.connect(a, b);
        board.connect(b, c);

        board.removeCard(b);

        assertEquals(2, board.getCards().size());
        assertTrue(board.getConnections().isEmpty());
    }

    @Test
    void copyKeepsContentWithNewId() {
        Card original = new Card(10, 20);
        original.setText("ideia");
        original.setColor("#FFF1A8");
        original.setWidth(260);

        Card copy = original.copy();

        assertNotEquals(original.getId(), copy.getId());
        assertEquals("ideia", copy.getText());
        assertEquals("#FFF1A8", copy.getColor());
        assertEquals(260, copy.getWidth());
        assertEquals(original.getShape(), copy.getShape());
    }

    @Test
    void newCardUsesDefaultAppearance() {
        Card card = new Card(10, 20);
        assertEquals(Card.DEFAULT_COLOR, card.getColor());
        assertEquals(CardShape.RECTANGLE, card.getShape());
        assertEquals(Card.DEFAULT_WIDTH, card.getWidth());
    }
}
