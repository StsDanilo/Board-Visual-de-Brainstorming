package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Partes do BoardLinkController que não precisam de tela: mudar o link pelo desfazer, destino excluído. */
class BoardLinkControllerTest {

    private final Board root = new Board("raiz");
    private final EditHistory history = new EditHistory(() -> root);
    private final Card card = new Card(0, 0);
    private final Card holderA = holder("A");
    private final Card holderB = holder("B");

    BoardLinkControllerTest() {
        root.addCard(card);
    }

    private Card holder(String text) {
        Card holder = new Card(300, 0);
        holder.setText(text);
        holder.setChildBoard(new Board(""));
        root.addCard(holder);
        return holder;
    }

    private String idOf(Card holder) {
        return holder.getChildBoard().getId();
    }

    @Test
    void addChangeAndRemoveAreUndoableSteps() {
        BoardLinkController.setBoardLink(history, card, idOf(holderA));
        BoardLinkController.setBoardLink(history, card, idOf(holderB));
        BoardLinkController.removeBoardLink(history, card);
        assertFalse(card.hasBoardLink());

        history.undo();
        assertEquals(idOf(holderB), card.getBoardLink(), "desfazer a remoção");
        history.undo();
        assertEquals(idOf(holderA), card.getBoardLink(), "desfazer a troca");
        history.undo();
        assertNull(card.getBoardLink(), "desfazer a inclusão");
        assertFalse(history.canUndoProperty().get());

        history.redo();
        assertEquals(idOf(holderA), card.getBoardLink());
    }

    @Test
    void sameDestinationAgainIsNotAStep() {
        BoardLinkController.setBoardLink(history, card, idOf(holderA));
        BoardLinkController.setBoardLink(history, card, idOf(holderA));

        history.undo();

        assertFalse(card.hasBoardLink());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void deletingTheDestinationKeepsTheLinkAndUndoBringsTheDestinationBack() {
        BoardLinkController.setBoardLink(history, card, idOf(holderA));
        String target = card.getBoardLink();

        history.perform(() -> root.removeCard(holderA));

        assertEquals(target, card.getBoardLink(), "o link não é apagado sozinho");
        assertFalse(BoardTree.contains(root, target), "o marcador aparece quebrado");

        history.undo();

        assertTrue(BoardTree.contains(root, target), "o board voltou com o mesmo id: o link funciona de novo");
    }

    @Test
    void deletingTheCardWithTheLinkAndUndoingBringsTheLinkBack() {
        BoardLinkController.setBoardLink(history, card, idOf(holderB));
        history.perform(() -> root.removeCard(card));

        history.undo();

        assertEquals(idOf(holderB), root.findCard(card.getId()).orElseThrow().getBoardLink());
    }

    @Test
    void duplicateCopiesTheLink() {
        BoardLinkController.setBoardLink(history, card, idOf(holderA));

        Card copy = card.copy();

        assertEquals(idOf(holderA), copy.getBoardLink());
    }

    @Test
    void duplicatingTheDestinationKeepsLinksOnTheOriginal() {
        BoardLinkController.setBoardLink(history, card, idOf(holderA));

        Card copyOfA = holderA.copy();
        root.addCard(copyOfA);

        assertEquals(idOf(holderA), card.getBoardLink(), "o link continua no original");
        assertFalse(idOf(copyOfA).equals(idOf(holderA)), "a cópia tem board com id novo");
    }
}
