package com.danilo.boardvisual.alignment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxResizerTest {

    private static final double EPS = 1e-9;
    private static final double MIN_W = 80;
    private static final double MIN_H = 50;

    /** Caixa inicial: x 100..300, y 100..220. */
    private final Box start = new Box(100, 100, 200, 120);

    private BoxResizer.Result resize(ResizeHandle handle, double dx, double dy) {
        return BoxResizer.resize(start, handle, dx, dy, MIN_W, MIN_H, false, List.of(), 0);
    }

    @Test
    void eastChangesOnlyWidth() {
        Box b = resize(ResizeHandle.EAST, 50, 999).box();
        assertEquals(new Box(100, 100, 250, 120), b);
    }

    @Test
    void southEastChangesWidthAndHeight() {
        Box b = resize(ResizeHandle.SOUTH_EAST, 50, 30).box();
        assertEquals(new Box(100, 100, 250, 150), b);
    }

    @Test
    void northWestMovesOriginKeepingOppositeCorner() {
        Box b = resize(ResizeHandle.NORTH_WEST, -20, -10).box();
        assertEquals(new Box(80, 90, 220, 130), b);
        assertEquals(300, b.x() + b.width(), EPS, "borda direita parada");
        assertEquals(220, b.y() + b.height(), EPS, "base parada");
    }

    @Test
    void minimumSizeHoldsTheOppositeSide() {
        Box shrunk = resize(ResizeHandle.WEST, 500, 0).box(); // tentaria largura negativa
        assertEquals(MIN_W, shrunk.width(), EPS);
        assertEquals(300, shrunk.x() + shrunk.width(), EPS, "a direita não se mexe");

        Box flat = resize(ResizeHandle.SOUTH, 0, -500).box();
        assertEquals(MIN_H, flat.height(), EPS);
        assertEquals(100, flat.y(), EPS);
    }

    @Test
    void keepAspectOnCornerFollowsWidth() {
        Box b = BoxResizer.resize(start, ResizeHandle.SOUTH_EAST, 100, 0, MIN_W, MIN_H, true, List.of(), 0).box();
        assertEquals(300, b.width(), EPS);
        assertEquals(180, b.height(), EPS, "mesma proporção 200:120");
        assertEquals(100, b.y(), EPS);

        Box north = BoxResizer.resize(start, ResizeHandle.NORTH_WEST, -100, 0, MIN_W, MIN_H, true, List.of(), 0).box();
        assertEquals(300 + 0, north.x() + north.width(), EPS, "canto oposto (direita) parado");
        assertEquals(220, north.y() + north.height(), EPS, "canto oposto (base) parado");
        assertEquals(180, north.height(), EPS);
    }

    @Test
    void keepAspectRespectsMinimumOnBothAxes() {
        Box b = BoxResizer.resize(start, ResizeHandle.SOUTH_EAST, -500, 0, MIN_W, MIN_H, true, List.of(), 0).box();
        assertTrue(b.width() >= MIN_W - EPS && b.height() >= MIN_H - EPS);
        assertEquals(200.0 / 120.0, b.width() / b.height(), 1e-6, "proporção mantida no mínimo");
    }

    @Test
    void pulledEdgeSnapsToOtherBox() {
        Box other = new Box(400, 500, 100, 100); // esquerda em 400
        // Borda direita livre em 300 + 97 = 397: gruda em 400.
        BoxResizer.Result r = BoxResizer.resize(start, ResizeHandle.EAST, 97, 0, MIN_W, MIN_H, false, List.of(other), 6);
        assertEquals(300, r.box().width(), EPS);
        assertEquals(1, r.guides().size());
        assertEquals(400, r.guides().get(0).position(), EPS);
    }

    @Test
    void fixedEdgeDoesNotSnap() {
        Box other = new Box(98, 500, 50, 50); // esquerda perto da esquerda da caixa (lado parado)
        BoxResizer.Result r = BoxResizer.resize(start, ResizeHandle.EAST, 10, 0, MIN_W, MIN_H, false, List.of(other), 6);
        assertEquals(100, r.box().x(), EPS);
        assertEquals(210, r.box().width(), EPS);
    }

    @Test
    void zeroThresholdDisablesSnapping() {
        Box other = new Box(400, 500, 100, 100);
        BoxResizer.Result r = BoxResizer.resize(start, ResizeHandle.EAST, 97, 0, MIN_W, MIN_H, false, List.of(other), 0);
        assertEquals(297, r.box().width(), EPS);
        assertTrue(r.guides().isEmpty());
    }
}
