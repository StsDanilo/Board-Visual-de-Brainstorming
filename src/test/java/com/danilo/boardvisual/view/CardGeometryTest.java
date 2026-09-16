package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardGeometryTest {

    private static final double EPS = 1e-9;

    /** Card 200x100 centralizado em (0, 0): semi-eixos a = 100, b = 50. */
    private static Card card(CardShape shape) {
        Card card = new Card(-100, -50);
        card.setWidth(200);
        card.setHeight(100);
        card.setShape(shape);
        return card;
    }

    @Test
    void horizontalDirectionHitsRightEdgeForEveryShape() {
        for (CardShape shape : CardShape.values()) {
            Point2D p = CardGeometry.borderPoint(card(shape), 1000, 0);
            assertEquals(100, p.getX(), EPS, shape.name());
            assertEquals(0, p.getY(), EPS, shape.name());
        }
    }

    @Test
    void diagonalDependsOnShape() {
        // Direção (1, 1) a partir do centro.
        Point2D rect = CardGeometry.borderPoint(card(CardShape.RECTANGLE), 1000, 1000);
        assertEquals(50, rect.getX(), EPS);   // bate na borda de baixo (y = 50)
        assertEquals(50, rect.getY(), EPS);

        Point2D ellipse = CardGeometry.borderPoint(card(CardShape.ELLIPSE), 1000, 1000);
        double onEllipse = Math.pow(ellipse.getX() / 100, 2) + Math.pow(ellipse.getY() / 50, 2);
        assertEquals(1, onEllipse, EPS);

        Point2D diamond = CardGeometry.borderPoint(card(CardShape.DIAMOND), 1000, 1000);
        assertEquals(1, Math.abs(diamond.getX()) / 100 + Math.abs(diamond.getY()) / 50, EPS);
    }

    @Test
    void sameCenterReturnsCenter() {
        Point2D p = CardGeometry.borderPoint(card(CardShape.DIAMOND), 0, 0);
        assertEquals(0, p.getX(), EPS);
        assertEquals(0, p.getY(), EPS);
    }
}
