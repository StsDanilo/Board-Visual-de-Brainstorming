package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.StackMove;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Card travado, sem tela: travar/destravar no desfazer, e o que o travado
 * bloqueia (mover em grupo, excluir, formato) e permite (duplicar, cor, ordem).
 */
class LockControllerTest {

    private final Board board = new Board("travas");
    private final EditHistory history = new EditHistory(() -> board);
    private final Card a = add(0);
    private final Card b = add(300);

    private Card add(double x) {
        Card card = new Card(x, 0);
        board.addCard(card);
        return card;
    }

    @Test
    void lockAndUnlockAreUndoableSteps() {
        LockController.setLocked(history, List.of(a), true);
        assertTrue(a.isLocked());
        LockController.setLocked(history, List.of(a), false);
        assertFalse(a.isLocked());

        history.undo();
        assertTrue(a.isLocked(), "desfazer o destravar");
        history.undo();
        assertFalse(a.isLocked(), "desfazer o travar");
        history.redo();
        assertTrue(a.isLocked());
    }

    @Test
    void lockingWhatIsAlreadyLockedCreatesNoStep() {
        a.setLocked(true);
        LockController.setLocked(history, List.of(a), true);
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void toggleLocksAllUnlessAllAreLocked() {
        assertTrue(LockController.shouldLock(List.of(a, b)), "nenhum travado: trava");
        a.setLocked(true);
        assertTrue(LockController.shouldLock(List.of(a, b)), "misto: trava todos");
        b.setLocked(true);
        assertFalse(LockController.shouldLock(List.of(a, b)), "todos travados: destrava");
        assertFalse(LockController.shouldLock(List.of()), "nada selecionado");
    }

    @Test
    void snapshotSeesTheLock() {
        BoardSnapshot before = board.snapshot();
        a.setLocked(true);
        assertNotEquals(before, board.snapshot());
        board.restore(before);
        assertFalse(a.isLocked());
    }

    @Test
    void groupDragMovesOnlyTheUnlockedCards() {
        a.setLocked(true);
        assertEquals(List.of(b), CardDragController.movableCards(List.of(a, b)));
        assertEquals(List.of(), CardDragController.movableCards(List.of(a)), "só travados: nada se move");
    }

    @Test
    void deleteKeepsLockedCards() {
        a.setLocked(true);

        int kept = SelectionActionsController.delete(history, board, List.of(a, b));

        assertEquals(1, kept);
        assertEquals(List.of(a), List.copyOf(board.getCards()));
        history.undo();
        // O desfazer recria o card excluído (mesmo id, outro objeto), no mesmo lugar da pilha.
        assertEquals(List.of(a.getId(), b.getId()), board.getCards().stream().map(Card::getId).toList());
    }

    @Test
    void deletingOnlyLockedCardsChangesNothingAndCreatesNoStep() {
        a.setLocked(true);

        int kept = SelectionActionsController.delete(history, board, List.of(a));

        assertEquals(1, kept);
        assertEquals(List.of(a, b), List.copyOf(board.getCards()));
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void shapeDoesNotChangeOnLockedCardsButColorDoes() {
        a.setLocked(true);

        SelectionActionsController.applyShape(history, List.of(a, b), CardShape.ELLIPSE);
        history.perform(() -> List.of(a, b).forEach(card -> card.setColor("#FFD8A8")));

        assertEquals(CardShape.RECTANGLE, a.getShape(), "travado: o formato mudaria o tamanho");
        assertEquals(CardShape.ELLIPSE, b.getShape());
        assertEquals("#FFD8A8", a.getColor(), "cor continua livre");
    }

    @Test
    void duplicateOfALockedCardIsUnlocked() {
        a.setLocked(true);
        a.setText("travado");

        List<Card> copies = SelectionActionsController.duplicate(history, board, List.of(a));

        assertEquals(1, copies.size());
        assertFalse(copies.get(0).isLocked(), "a cópia nasce destravada, para poder ser posicionada");
        assertEquals("travado", copies.get(0).getText());
        assertTrue(a.isLocked(), "o original continua travado");
    }

    @Test
    void lockedCardStillChangesStackOrder() {
        a.setLocked(true);
        assertTrue(StackOrderController.reorder(history, board, List.of(a), StackMove.TO_FRONT));
        assertEquals(List.of(b, a), List.copyOf(board.getCards()));
    }
}
