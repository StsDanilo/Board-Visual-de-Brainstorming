package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.StackMove;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seções (frames) nas ações, sem tela: o que um arrasto move (com o conteúdo,
 * sem mover em dobro, travados), alinhamento, redimensionar, duplicar,
 * excluir, formato, ordem e desfazer.
 */
class FrameControllerTest {

    private final Board board = new Board("seções");
    private final EditHistory history = new EditHistory(() -> board);

    /** Seção 400 × 300 com o canto em (x, y). */
    private Card frame(String text, double x, double y) {
        Card frame = new Card(x, y);
        frame.setShape(CardShape.FRAME);
        frame.resize(400, 300);
        frame.setText(text);
        board.addCard(frame);
        return frame;
    }

    /** Card 100 × 60 com o centro no ponto. */
    private Card card(String text, double centerX, double centerY) {
        Card card = new Card(centerX - 50, centerY - 30);
        card.resize(100, 60);
        card.setText(text);
        board.addCard(card);
        return card;
    }

    /** O mesmo que o arrasto faz: um passo no desfazer movendo o que {@code draggedCards} devolve. */
    private List<Card> drag(List<Card> selection, double dx, double dy) {
        List<Card> moving = CardDragController.draggedCards(selection, board.getCards());
        BoardSnapshot before = history.capture();
        moving.forEach(c -> {
            c.setX(c.getX() + dx);
            c.setY(c.getY() + dy);
        });
        history.record(before);
        return moving;
    }

    @Test
    void draggingAFrameMovesTheCardsInsideItAndNothingElse() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 100, 100);
        Card outside = card("fora", 600, 100);

        List<Card> moved = drag(List.of(f), 50, 20);

        assertEquals(Set.of(f, inside), Set.copyOf(moved));
        assertEquals(50, f.getX());
        assertEquals(100, inside.getX(), "50 + 50");
        assertEquals(90, inside.getY(), "70 + 20");
        assertEquals(550, outside.getX(), "fora da seção: parado");
    }

    @Test
    void frameAndASelectedCardInsideItMoveOnlyOnce() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 100, 100);

        List<Card> moved = drag(List.of(f, inside), 30, 0);

        assertEquals(2, moved.size(), "cada card uma vez só");
        assertEquals(80, inside.getX(), "andou 30, não 60");
    }

    @Test
    void cardInsideTwoDraggedFramesMovesOnce() {
        Card left = frame("E", 0, 0);
        Card right = frame("D", 200, 0);
        Card shared = card("as duas", 300, 150);

        drag(List.of(left, right), 10, 10);

        assertEquals(260, shared.getX());
        assertEquals(130, shared.getY());
    }

    @Test
    void lockedFrameDoesNotMoveNorCarryItsContents() {
        Card f = frame("F", 0, 0);
        f.setLocked(true);
        Card inside = card("dentro", 100, 100);

        List<Card> moved = drag(List.of(f), 40, 40);

        assertTrue(moved.isEmpty());
        assertEquals(0, f.getX());
        assertEquals(50, inside.getX());
        assertFalse(history.canUndoProperty().get(), "nada se moveu: sem passo");
    }

    @Test
    void lockedCardInsideAMovedFrameGoesAlong() {
        Card f = frame("F", 0, 0);
        Card locked = card("travado", 100, 100);
        locked.setLocked(true);

        drag(List.of(f), 40, 0);

        assertEquals(90, locked.getX(), "o travado acompanha a seção (decisão 4b)");
        // Sozinho, continua parado.
        drag(List.of(locked), 40, 0);
        assertEquals(90, locked.getX());
    }

    @Test
    void cardDroppedOverAFrameBelongsToItOnTheNextDrag() {
        Card f = frame("F", 0, 0);
        Card loose = card("solto", 700, 100);

        drag(List.of(loose), -500, 0); // solta em cima da seção: centro em (200, 100)
        drag(List.of(f), 0, 50);

        assertEquals(120, loose.getY(), "70 + 50: veio junto com a seção");
    }

    @Test
    void contentsAreTakenAtTheStartOfTheDrag() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 350, 150);

        // O conjunto é calculado uma vez: andar com a seção não "pega" quem fica por cima no caminho.
        Card later = card("depois", 520, 150);
        List<Card> moving = CardDragController.draggedCards(List.of(f), board.getCards());
        assertEquals(List.of(f, inside), moving);
        assertFalse(moving.contains(later));
    }

    @Test
    void wholeDragIsOneUndoStep() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 100, 100);

        drag(List.of(f), 50, 50);
        history.undo();

        assertEquals(0, f.getX());
        assertEquals(50, inside.getX());
        assertEquals(70, inside.getY());
        history.redo();
        assertEquals(100, inside.getX());
    }

    @Test
    void framesAreAlignmentReferencesOnlyWhenAFrameIsDragged() {
        Card f = frame("F", 0, 0);
        Card a = card("a", 600, 100);
        Card b = card("b", 900, 100);

        List<Box> forCards = CardDragController.alignmentReferences(board.getCards(), List.of(a), false);
        List<Box> forFrames = CardDragController.alignmentReferences(board.getCards(), List.of(a), true);

        assertEquals(List.of(CardDragController.boxOf(b)), forCards, "arrastando cards: seções não puxam");
        assertEquals(List.of(CardDragController.boxOf(f), CardDragController.boxOf(b)), forFrames);
    }

    @Test
    void resizingAFrameDoesNotTouchTheCardsInside() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 100, 100);

        history.perform(() -> f.resize(80, 80)); // o centro do card (100, 100) fica fora da área nova

        assertEquals(50, inside.getX());
        assertEquals(70, inside.getY());
        assertEquals(100, inside.getWidth());
        assertFalse(com.danilo.boardvisual.model.Frames.contains(f, inside), "não pertence mais (geometria)");
    }

    @Test
    void duplicatingAFrameCopiesOnlyTheFrameBelowTheCards() {
        Card f = frame("F", 0, 0);
        f.setColor("#CFE2FF");
        f.setLocked(true);
        card("dentro", 100, 100);

        List<Card> copies = SelectionActionsController.duplicate(history, board, List.of(f));

        assertEquals(1, copies.size(), "os cards de dentro não são copiados");
        Card copy = copies.get(0);
        assertTrue(copy.isFrame());
        assertEquals("F", copy.getText());
        assertEquals("#CFE2FF", copy.getColor());
        assertFalse(copy.isLocked(), "cópia nasce destravada");
        assertEquals(3, board.getCards().size());
        assertEquals(List.of(f, copy), board.getCards().subList(0, 2), "cópia no topo das seções, atrás dos cards");
        history.undo();
        assertEquals(2, board.getCards().size());
    }

    @Test
    void deletingAFrameDeletesOnlyTheFrame() {
        Card f = frame("F", 0, 0);
        Card inside = card("dentro", 100, 100);

        assertEquals(0, SelectionActionsController.delete(history, board, List.of(f)));

        assertEquals(List.of(inside), List.copyOf(board.getCards()));
        history.undo();
        assertEquals(List.of("F", "dentro"), board.getCards().stream().map(Card::getText).toList(),
                "volta atrás dos cards");
    }

    @Test
    void lockedFrameIsNotDeleted() {
        Card f = frame("F", 0, 0);
        f.setLocked(true);

        assertEquals(1, SelectionActionsController.delete(history, board, List.of(f)));
        assertTrue(board.getCards().contains(f));
    }

    @Test
    void shapePickerNeverChangesAFrame() {
        Card f = frame("F", 0, 0);
        Card a = card("a", 600, 100);

        SelectionActionsController.applyShape(history, List.of(f, a), CardShape.ELLIPSE);

        assertTrue(f.isFrame());
        assertEquals(400, f.getWidth());
        assertEquals(CardShape.ELLIPSE, a.getShape());
    }

    @Test
    void stackOrderKeepsFramesBelowAndIsUndoable() {
        Card f = frame("F", 0, 0);
        Card g = frame("G", 0, 0);
        Card a = card("a", 100, 100);

        assertFalse(StackOrderController.reorder(history, board, List.of(g), StackMove.TO_FRONT),
                "G já está no topo das seções: nada muda, sem passo");
        assertTrue(StackOrderController.reorder(history, board, List.of(f), StackMove.TO_FRONT));
        assertEquals(List.of(g, f, a), List.copyOf(board.getCards()));
        assertFalse(StackOrderController.reorder(history, board, List.of(a), StackMove.TO_BACK),
                "único card comum: já está no fundo dos cards");

        history.undo();
        assertEquals(List.of(f, g, a), List.copyOf(board.getCards()));
    }
}
