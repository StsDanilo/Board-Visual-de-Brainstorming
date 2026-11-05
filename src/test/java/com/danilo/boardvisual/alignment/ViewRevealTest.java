package com.danilo.boardvisual.alignment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A conta de "levar a visão até o resultado" da busca, sem tela. */
class ViewRevealTest {

    private static final double EPS = 1e-9;

    @Test
    void centerOnKeepsTheZoomAndPutsThePointInTheMiddle() {
        ViewFit.Result r = ViewReveal.centerOn(1000, 400, 800, 600, 1.5, 3.0);
        assertEquals(1.5, r.zoom(), EPS, "o zoom da pessoa não muda");
        assertEquals(400, r.panX() + 1000 * r.zoom(), EPS, "ponto no centro horizontal (800/2)");
        assertEquals(300, r.panY() + 400 * r.zoom(), EPS, "ponto no centro vertical (600/2)");
    }

    @Test
    void centerOnRaisesAnIllegibleZoomToTheReadableMinimum() {
        ViewFit.Result r = ViewReveal.centerOn(-500, 700, 800, 600, 0.2, 3.0);
        assertEquals(ViewReveal.MIN_READABLE_ZOOM, r.zoom(), EPS);
        assertEquals(400, r.panX() + (-500) * r.zoom(), EPS, "o ponto -500 cai no centro horizontal (800/2)");
        assertEquals(300, r.panY() + 700 * r.zoom(), EPS, "o ponto 700 cai no centro vertical (600/2)");
    }

    @Test
    void centerOnNeverPassesTheMaximumZoom() {
        ViewFit.Result r = ViewReveal.centerOn(0, 0, 800, 600, 0.1, 0.3);
        assertEquals(0.3, r.zoom(), EPS, "se o máximo é menor que o legível, vale o máximo");
    }

    @Test
    void centerOnAtExactlyTheMinimumDoesNotChangeTheZoom() {
        assertEquals(0.5, ViewReveal.centerOn(0, 0, 800, 600, 0.5, 3.0).zoom(), EPS);
        assertEquals(0.51, ViewReveal.centerOn(0, 0, 800, 600, 0.51, 3.0).zoom(), EPS);
    }

    @Test
    void fullyVisibleChecksAllFourSides() {
        Box box = new Box(100, 100, 200, 100); // na tela com zoom 1 e pan 0: 100..300 x 100..200
        assertTrue(ViewReveal.isFullyVisible(box, 0, 0, 1, 800, 600));
        assertFalse(ViewReveal.isFullyVisible(box, -150, 0, 1, 800, 600), "cortado à esquerda");
        assertFalse(ViewReveal.isFullyVisible(box, 0, -150, 1, 800, 600), "cortado em cima");
        assertFalse(ViewReveal.isFullyVisible(box, 600, 0, 1, 800, 600), "cortado à direita");
        assertFalse(ViewReveal.isFullyVisible(box, 0, 450, 1, 800, 600), "cortado embaixo");
        assertTrue(ViewReveal.isFullyVisible(box, -100, -100, 1, 200, 100), "encostado nas bordas conta como visível");
        assertFalse(ViewReveal.isFullyVisible(box, 0, 0, 4, 800, 600), "com zoom 4 já não cabe");
    }
}
