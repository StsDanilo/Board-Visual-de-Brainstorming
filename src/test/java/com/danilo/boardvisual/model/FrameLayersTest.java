package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O invariante das camadas no {@link Board}: seções (frames) sempre abaixo dos
 * cards comuns, ao criar, reordenar, desfazer; e seções sem setas.
 */
class FrameLayersTest {

    private final Board board = new Board("seções");

    private Card card(String text) {
        Card card = new Card(0, 0);
        card.setText(text);
        board.addCard(card);
        return card;
    }

    private Card frame(String text) {
        Card frame = Card.create(CardShape.FRAME, 0, 0);
        frame.setText(text);
        board.addCard(frame);
        return frame;
    }

    private String order() {
        StringBuilder text = new StringBuilder();
        board.getCards().forEach(c -> text.append(c.getText()));
        return text.toString();
    }

    @Test
    void newFrameGoesBelowAllCommonCardsAndAboveOtherFrames() {
        card("a");
        card("b");
        frame("F");
        frame("G");
        card("c");

        assertEquals("FGabc", order(), "seção nova: topo das seções, abaixo de todos os cards");
    }

    @Test
    void frameShapeHasItsOwnDefaultSize() {
        Card frame = Card.create(CardShape.FRAME, 500, 400);
        assertTrue(frame.isFrame());
        assertEquals(480, frame.getWidth());
        assertEquals(320, frame.getHeight());
        assertEquals(500, frame.getCenterX());
        assertFalse(new Card(0, 0).isFrame());
    }

    @Test
    void bringingAFrameToTheFrontStopsBelowTheCommonCards() {
        Card f = frame("F");
        frame("G");
        card("a");
        card("b");

        assertTrue(board.reorder(List.of(f), StackMove.TO_FRONT));
        assertEquals("GFab", order());
        assertFalse(board.canReorder(List.of(f), StackMove.TO_FRONT), "já no topo das seções");
        assertFalse(board.canReorder(List.of(f), StackMove.FORWARD), "não passa por cima de um card");
    }

    @Test
    void sendingACardToTheBackStopsAboveTheFrames() {
        frame("F");
        card("a");
        Card b = card("b");

        assertTrue(board.reorder(List.of(b), StackMove.TO_BACK));
        assertEquals("Fba", order());
        assertFalse(board.canReorder(List.of(b), StackMove.BACKWARD), "card no fundo dos cards: não desce mais");
    }

    @Test
    void framesMoveOneLevelAmongThemselves() {
        Card f = frame("F");
        frame("G");
        frame("H");
        card("a");

        board.reorder(List.of(f), StackMove.FORWARD);
        assertEquals("GFHa", order());
        board.reorder(List.of(f), StackMove.BACKWARD);
        assertEquals("FGHa", order());
    }

    @Test
    void mixedSelectionMovesEachGroupInsideItsLayer() {
        Card f = frame("F");
        frame("G");
        Card a = card("a");
        card("b");

        board.reorder(List.of(f, a), StackMove.TO_FRONT);
        assertEquals("GFba", order());
        board.reorder(List.of(f, a), StackMove.TO_BACK);
        assertEquals("FGab", order());
    }

    @Test
    void undoRestoresTheLayersAndTheOrderAmongFrames() {
        Card f = frame("F");
        frame("G");
        Card a = card("a");
        BoardSnapshot before = board.snapshot();

        board.reorder(List.of(f), StackMove.TO_FRONT);
        board.removeCard(a);
        board.restore(before);

        assertEquals("FGa", order());
    }

    @Test
    void restoringAFrameDeletionPutsItBackBelowTheCards() {
        Card f = frame("F");
        card("a");
        card("b");
        BoardSnapshot before = board.snapshot();

        board.removeCard(f);
        card("c");
        board.restore(before);

        assertEquals("Fab", order());
    }

    @Test
    void restoreFixesASnapshotWithAFrameAboveACard() {
        Card a = new Card("a", 0, 0);
        a.setText("a");
        Card f = new Card("f", 0, 0);
        f.setShape(CardShape.FRAME);
        f.setText("F");
        BoardSnapshot wrong = new BoardSnapshot(
                List.of(BoardSnapshot.CardState.of(a), BoardSnapshot.CardState.of(f)), List.of());

        board.restore(wrong);

        assertEquals("Fa", order());
    }

    @Test
    void framesHaveNoArrows() {
        Card f = frame("F");
        Card a = card("a");
        Card b = card("b");

        assertTrue(board.connect(f, a).isEmpty(), "da seção para um card");
        assertTrue(board.connect(a, f).isEmpty(), "de um card para a seção");
        assertFalse(board.addConnection(new Connection(a, f)));
        assertTrue(board.connect(a, b).isPresent(), "cards comuns continuam ligando");
        assertEquals(1, board.getConnections().size());
    }

    @Test
    void deletingAFrameKeepsTheCardsInsideIt() {
        Card f = frame("F");
        f.setX(0);
        f.setY(0);
        Card inside = card("a");
        inside.setX(50);
        inside.setY(50);

        board.removeCard(f);

        assertEquals(List.of(inside), List.copyOf(board.getCards()));
        assertEquals(50, inside.getX());
    }
}
