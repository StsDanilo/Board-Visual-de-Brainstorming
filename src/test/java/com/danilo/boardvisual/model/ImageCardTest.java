package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCardTest {

    @Test
    void imageCardIsARectangleWithTheImageSizeCenteredOnThePoint() {
        Card card = Card.createImage("a.png", 500, 300, 320, 180);

        assertTrue(card.hasImage());
        assertEquals(CardShape.RECTANGLE, card.getShape());
        assertEquals(340, card.getX());
        assertEquals(210, card.getY());
        assertEquals(320, card.getBaseWidth());
        assertEquals(180, card.getBaseHeight());
        assertEquals("", card.getText(), "nasce sem legenda");
    }

    @Test
    void imageHeightFollowsTheImageProportion() {
        Card card = Card.createImage("a.png", 0, 0, 320, 180);
        card.setHeight(240); // legenda embaixo

        assertEquals(180, card.imageHeight(card.getWidth()));
        assertEquals(360, card.imageHeight(640));
        assertEquals(0, new Card(0, 0).imageHeight(200), "card sem imagem");
    }

    @Test
    void blankImageNameMeansNoImage() {
        Card card = new Card(0, 0);
        card.setImageFile("  ");
        assertFalse(card.hasImage());
        assertNull(card.getImageFile());
    }

    @Test
    void duplicateReusesTheSameFile() {
        Card original = Card.createImage("foto.jpg", 0, 0, 200, 100);

        Card copy = original.copy();

        assertEquals("foto.jpg", copy.getImageFile());
    }

    @Test
    void undoingTheInsertionRemovesTheCardAndRedoBringsTheImageBack() {
        Board board = new Board("b");
        BoardSnapshot before = board.snapshot();
        Card card = Card.createImage("foto.png", 0, 0, 200, 100);
        board.addCard(card);
        BoardSnapshot after = board.snapshot();

        board.restore(before);
        assertTrue(board.getCards().isEmpty());

        board.restore(after);
        assertEquals("foto.png", board.findCard(card.getId()).orElseThrow().getImageFile());
    }

    @Test
    void collectsImagesOfTheWholeTreeAndOfSnapshots() {
        Board root = new Board("raiz");
        root.addCard(Card.createImage("raiz.png", 0, 0, 10, 10));
        Card nested = new Card(0, 0);
        Board child = new Board("filho");
        Board grandchild = new Board("neto");
        grandchild.addCard(Card.createImage("neto.png", 0, 0, 10, 10));
        Card holder = new Card(0, 0);
        holder.setChildBoard(grandchild);
        child.addCard(holder);
        nested.setChildBoard(child);
        root.addCard(nested);
        root.addCard(new Card(5, 5)); // sem imagem

        Set<String> fromBoard = new HashSet<>();
        root.collectImageFiles(fromBoard);
        Set<String> fromSnapshot = new HashSet<>();
        root.snapshot().collectImageFiles(fromSnapshot);

        assertEquals(Set.of("raiz.png", "neto.png"), fromBoard);
        assertEquals(fromBoard, fromSnapshot);
    }
}
