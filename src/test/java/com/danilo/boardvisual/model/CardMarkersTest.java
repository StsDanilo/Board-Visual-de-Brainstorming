package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Onde ficam os marcadores (clipe do link) em cada formato de card. */
class CardMarkersTest {

    private static final List<CardMarkers.Kind> ONE = List.of(CardMarkers.Kind.LINK);
    /** Metade da área clicável das alças de redimensionar (17 px). */
    private static final double HANDLE_HALF = 8.5;

    @Test
    void cardWithoutLinkHasNoMarker() {
        Card card = new Card(0, 0);
        assertEquals(List.of(), CardMarkers.of(card));
        assertEquals(List.of(), CardMarkers.layout(card));
        assertEquals(0, CardMarkers.reservedTop(CardShape.RECTANGLE, 200, 120, 0));

        card.setLink("exemplo.com");
        assertEquals(ONE, CardMarkers.of(card));
    }

    @Test
    void boardLinkAddsTheCurvedArrowAfterTheClip() {
        Card card = new Card(0, 0);
        card.setBoardLink("algum-board");
        assertEquals(List.of(CardMarkers.Kind.BOARD_LINK), CardMarkers.of(card), "sozinho");

        card.setLink("exemplo.com");
        List<CardMarkers.Spot> spots = CardMarkers.layout(card);
        assertEquals(List.of(CardMarkers.Kind.LINK, CardMarkers.Kind.BOARD_LINK), CardMarkers.of(card),
                "os dois lado a lado, clipe primeiro");
        assertEquals(spots.get(0).y(), spots.get(1).y());
        assertTrue(spots.get(1).x() > spots.get(0).x() + CardMarkers.SIZE);
    }

    @Test
    void rectangleMarkerSitsInTheTopRightCornerAwayFromTheResizeHandles() {
        CardMarkers.Spot spot = CardMarkers.layout(CardShape.RECTANGLE, 200, 120, ONE).get(0);

        assertEquals(CardMarkers.SIZE, spot.size());
        assertTrue(spot.x() + spot.size() <= 200 - HANDLE_HALF, "fora da alça do canto (NE)");
        assertTrue(spot.y() >= CardMarkers.TOP);
        // A alça do meio da borda de cima fica em x = 100 ± 8,5.
        assertTrue(spot.x() > 100 + HANDLE_HALF, "fora da alça do meio da borda de cima");
        // O botão de conexão fica fora do card, à direita; o do painel, embaixo.
        assertTrue(spot.y() + spot.size() < 120 - 30);
    }

    @Test
    void ellipseAndDiamondMarkersAreCenteredAndInsideTheSilhouette() {
        for (CardShape shape : List.of(CardShape.ELLIPSE, CardShape.DIAMOND)) {
            double w = shape.getDefaultWidth();
            double h = shape.getDefaultHeight();
            CardMarkers.Spot spot = CardMarkers.layout(shape, w, h, ONE).get(0);

            assertEquals(w / 2, spot.centerX(), 1e-9, shape + ": no meio");
            // Os dois cantos de cima do marcador ficam dentro da silhueta.
            assertTrue(inside(shape, w, h, spot.x(), spot.y()), shape + ": canto esquerdo");
            assertTrue(inside(shape, w, h, spot.x() + spot.size(), spot.y()), shape + ": canto direito");
        }
    }

    @Test
    void narrowDiamondPushesTheMarkerDownToFitInside() {
        CardMarkers.Spot wide = CardMarkers.layout(CardShape.DIAMOND, 260, 170, ONE).get(0);
        CardMarkers.Spot narrow = CardMarkers.layout(CardShape.DIAMOND, 100, 170, ONE).get(0);

        assertTrue(narrow.y() > wide.y());
        assertTrue(inside(CardShape.DIAMOND, 100, 170, narrow.x(), narrow.y()));
    }

    @Test
    void severalMarkersStayInARowWithoutOverlapping() {
        List<CardMarkers.Kind> two = List.of(CardMarkers.Kind.LINK, CardMarkers.Kind.LINK);
        List<CardMarkers.Spot> spots = CardMarkers.layout(CardShape.RECTANGLE, 200, 120, two);

        assertEquals(2, spots.size());
        assertEquals(spots.get(0).y(), spots.get(1).y());
        assertEquals(CardMarkers.SIZE + CardMarkers.SPACING, spots.get(1).x() - spots.get(0).x(), 1e-9);
        assertTrue(spots.get(1).x() + CardMarkers.SIZE <= 200 - HANDLE_HALF);
    }

    @Test
    void textStartsBelowTheMarkers() {
        CardMarkers.Spot spot = CardMarkers.layout(CardShape.RECTANGLE, 200, 120, ONE).get(0);
        double reserved = CardMarkers.reservedTop(CardShape.RECTANGLE, 200, 120, 1);

        assertTrue(reserved > spot.y() + spot.size());
    }

    @Test
    void tinyCardKeepsTheMarkerAboveTheMiddle() {
        CardMarkers.Spot spot = CardMarkers.layout(CardShape.DIAMOND, 40, 30, ONE).get(0);

        assertTrue(spot.y() + spot.size() / 2 <= 15 + 1e-9);
        assertTrue(spot.x() >= 0);
    }

    private static boolean inside(CardShape shape, double w, double h, double x, double y) {
        double dx = Math.abs(x - w / 2) / (w / 2);
        double dy = Math.abs(y - h / 2) / (h / 2);
        return switch (shape) {
            case RECTANGLE -> dx <= 1 && dy <= 1;
            case ELLIPSE -> dx * dx + dy * dy <= 1;
            case DIAMOND -> dx + dy <= 1;
        };
    }
}
