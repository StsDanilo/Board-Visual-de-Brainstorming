package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.PanelMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Partes do LinkController que não precisam de tela: mudar o link pelo desfazer. */
class LinkControllerTest {

    private final Board board = new Board("raiz");
    private final EditHistory history = new EditHistory(() -> board);
    private final Card card = new Card(0, 0);

    LinkControllerTest() {
        board.addCard(card);
    }

    @Test
    void addEditAndRemoveAreUndoableSteps() {
        assertNull(LinkController.setLink(history, card, "exemplo.com"));
        assertEquals("https://exemplo.com", card.getLink());

        assertNull(LinkController.setLink(history, card, "https://outro.com/x"));
        assertEquals("https://outro.com/x", card.getLink());

        LinkController.removeLink(history, card);
        assertFalse(card.hasLink());

        history.undo();
        assertEquals("https://outro.com/x", card.getLink(), "desfazer a remoção");
        history.undo();
        assertEquals("https://exemplo.com", card.getLink(), "desfazer a edição");
        history.undo();
        assertFalse(card.hasLink(), "desfazer a inclusão");
        assertFalse(history.canUndoProperty().get());

        history.redo();
        assertEquals("https://exemplo.com", card.getLink());
    }

    @Test
    void emptyTextRemovesTheLink() {
        LinkController.setLink(history, card, "exemplo.com");

        assertNull(LinkController.setLink(history, card, "   "));

        assertFalse(card.hasLink());
        history.undo();
        assertTrue(card.hasLink());
    }

    @Test
    void invalidAddressIsRefusedWithAMessageAndChangesNothing() {
        LinkController.setLink(history, card, "exemplo.com");

        String error = LinkController.setLink(history, card, "file:///C:/Windows/notepad.exe");

        assertNotNull(error);
        assertEquals("https://exemplo.com", card.getLink(), "o link anterior fica");
        history.undo();
        assertFalse(card.hasLink(), "só a inclusão virou passo no desfazer");
    }

    @Test
    void sameLinkAgainIsNotAStep() {
        LinkController.setLink(history, card, "https://exemplo.com");
        LinkController.setLink(history, card, "exemplo.com"); // normaliza para o mesmo

        history.undo();

        assertFalse(card.hasLink());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void duplicateCopiesTheLinkOfEveryKindOfCard() {
        Card panel = new Card(0, 0);
        panel.setPanelMode(PanelMode.TEXT);
        panel.setLink("exemplo.com/painel");
        Card photo = Card.createImage("foto.png", 0, 0, 100, 50);
        photo.setLink("exemplo.com/foto");

        assertEquals("https://exemplo.com/painel", panel.copy().getLink());
        assertEquals("https://exemplo.com/foto", photo.copy().getLink());
    }

    @Test
    void deletingACardWithLinkAndUndoingBringsTheLinkBack() {
        LinkController.setLink(history, card, "exemplo.com");
        history.perform(() -> board.removeCard(card));

        history.undo();

        assertEquals("https://exemplo.com", board.findCard(card.getId()).orElseThrow().getLink());
    }
}
