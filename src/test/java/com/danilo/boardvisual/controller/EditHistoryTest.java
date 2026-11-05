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

    // ------------------------------------------------------- congelado (apresentação)

    @Test
    void frozenHistoryDoesNotRunPerformedChangesAndRecordsNothing() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));
        history.setFrozen(true);

        history.perform(() -> card.setColor("#FFF1A8"));
        history.perform(() -> board.addCard(new Card(100, 100)));

        assertTrue(history.isFrozen());
        assertEquals(Card.DEFAULT_COLOR, card.getColor(), "a alteração nem é executada");
        assertEquals(1, board.getCards().size());

        BoardSnapshot before = history.capture();
        card.setX(50);
        history.record(before);
        history.undo();
        assertEquals(50, card.getX(), "congelado, nem registrar nem desfazer agem (o x mexido por fora fica)");

        history.setFrozen(false);
        history.undo();
        assertTrue(board.getCards().isEmpty(), "o passo de antes (criar o card) continua lá, e agora desfaz");
    }

    @Test
    void frozenHistoryIgnoresUndoAndRedoAndKeepsTheStepsOfBefore() {
        Card card = new Card(0, 0);
        history.perform(() -> board.addCard(card));
        history.perform(() -> card.setColor("#FFF1A8"));
        history.undo();
        history.setFrozen(true);

        history.undo();
        history.redo();
        assertEquals(1, board.getCards().size());
        assertEquals(Card.DEFAULT_COLOR, card.getColor(), "o refazer também não age");
        assertTrue(history.canRedoProperty().get(), "o refazer de antes continua à espera");

        history.setFrozen(false);
        history.redo();
        assertEquals("#FFF1A8", card.getColor());
    }

    @Test
    void freezingClosesAnEditSessionInProgressAsOneStep() {
        Card card = new Card(0, 0);
        board.addCard(card);
        history.beginEdit();
        card.setText("digitado antes de apresentar");

        history.setFrozen(true);
        history.endEdit(); // o foco saindo do campo, já congelado: nada a fazer

        history.setFrozen(false);
        assertTrue(history.canUndoProperty().get(), "o que foi digitado virou um passo ao congelar");
        history.undo();
        assertEquals("", card.getText());
    }

    @Test
    void frozenHistoryDoesNotStartEditSessions() {
        Card card = new Card(0, 0);
        board.addCard(card);
        history.setFrozen(true);
        history.beginEdit();
        card.setText("não deveria virar passo");
        history.endEdit();
        history.setFrozen(false);

        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void changingBoardStillWorksWhileFrozen() {
        Board[] current = {board};
        EditHistory nested = new EditHistory(() -> current[0]);
        nested.perform(() -> board.addCard(new Card(0, 0)));
        nested.setFrozen(true);

        current[0] = new Board("outro");
        nested.boardChanged();
        assertFalse(nested.canUndoProperty().get(), "o outro board começa sem histórico");
        current[0] = board;
        nested.boardChanged();
        assertTrue(nested.canUndoProperty().get(), "a navegação não perde o histórico");
    }
}
