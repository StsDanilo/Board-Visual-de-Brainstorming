package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardShapeTest {

    @Test
    void createUsesShapeDefaultSizeCenteredOnPoint() {
        Card card = Card.create(CardShape.DIAMOND, 500, 300);

        assertEquals(CardShape.DIAMOND, card.getShape());
        assertEquals(CardShape.DIAMOND.getDefaultWidth(), card.getWidth());
        assertEquals(500, card.getCenterX());
        assertEquals(300, card.getCenterY());
    }

    @Test
    void changeShapeAdoptsNewDefaultSizeKeepingCenter() {
        Card card = Card.create(CardShape.RECTANGLE, 400, 200);

        card.changeShape(CardShape.ELLIPSE);

        assertEquals(CardShape.ELLIPSE, card.getShape());
        assertEquals(CardShape.ELLIPSE.getDefaultWidth(), card.getWidth());
        assertEquals(CardShape.ELLIPSE.getDefaultHeight(), card.getHeight());
        assertEquals(400, card.getCenterX());
        assertEquals(200, card.getCenterY());
    }

    @Test
    void changeShapeKeepsCustomSize() {
        Card card = Card.create(CardShape.RECTANGLE, 400, 200);
        card.setWidth(333);

        card.changeShape(CardShape.DIAMOND);

        assertEquals(CardShape.DIAMOND, card.getShape());
        assertEquals(333, card.getWidth());
    }

    @Test
    void unknownShapeNameFallsBackToRectangle() {
        assertEquals(CardShape.ELLIPSE, CardShape.fromName("ellipse"));
        assertEquals(CardShape.RECTANGLE, CardShape.fromName("HEXAGON"));
        assertEquals(CardShape.RECTANGLE, CardShape.fromName(null));
    }
}
