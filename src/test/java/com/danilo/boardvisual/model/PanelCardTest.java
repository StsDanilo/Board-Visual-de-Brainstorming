package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelCardTest {

    @Test
    void newCardIsNotAPanel() {
        Card card = new Card(0, 0);
        assertEquals(PanelMode.NONE, card.getPanelMode());
        assertFalse(card.isPanel());
        assertFalse(card.hasPanelContent());
    }

    @Test
    void changingModeClearsPreviousContent() {
        Card card = new Card(0, 0);
        card.setPanelMode(PanelMode.TEXT);
        card.setDetailText("detalhe");
        assertTrue(card.hasPanelContent());

        card.changePanelMode(PanelMode.LIST);
        assertEquals("", card.getDetailText());
        card.addListItem("item 1");
        assertTrue(card.hasPanelContent());

        card.changePanelMode(PanelMode.TEXT);
        assertTrue(card.getListItems().isEmpty());
        assertFalse(card.hasPanelContent());
    }

    @Test
    void blankItemsDoNotCountAsContent() {
        Card card = new Card(0, 0);
        card.setPanelMode(PanelMode.LIST);
        card.addListItem("   ");
        assertFalse(card.hasPanelContent());
    }

    @Test
    void setListItemTextsUpdatesInPlaceWhenSizeMatches() {
        Card card = new Card(0, 0);
        ListItem first = card.addListItem("a");
        card.addListItem("b");

        card.setListItemTexts(List.of("x", "y"));
        assertSame(first, card.getListItems().get(0));
        assertEquals(List.of("x", "y"), card.getListItemTexts());

        card.setListItemTexts(List.of("só um"));
        assertEquals(List.of("só um"), card.getListItemTexts());
    }

    @Test
    void copyDuplicatesPanelContentIndependently() {
        Card card = new Card(0, 0);
        card.setPanelMode(PanelMode.LIST);
        card.addListItem("a");

        Card copy = card.copy();
        copy.getListItems().get(0).setText("mudou");

        assertEquals(PanelMode.LIST, copy.getPanelMode());
        assertEquals("a", card.getListItems().get(0).getText());
        assertNotSame(card.getListItems().get(0), copy.getListItems().get(0));
    }

    @Test
    void snapshotRestoresPanelModeAndContent() {
        Board board = new Board("teste");
        Card card = new Card(0, 0);
        card.setPanelMode(PanelMode.TEXT);
        card.setDetailText("original");
        board.addCard(card);
        BoardSnapshot before = board.snapshot();

        card.changePanelMode(PanelMode.LIST);
        card.addListItem("novo");
        board.restore(before);

        assertEquals(PanelMode.TEXT, card.getPanelMode());
        assertEquals("original", card.getDetailText());
        assertTrue(card.getListItems().isEmpty());
    }

    @Test
    void unknownModeNameMeansNormalCard() {
        assertEquals(PanelMode.LIST, PanelMode.fromName("list"));
        assertEquals(PanelMode.NONE, PanelMode.fromName(null));
        assertEquals(PanelMode.NONE, PanelMode.fromName("OUTRO"));
    }
}
