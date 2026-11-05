package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionGeometryTest {

    private static final double EPS = 1e-9;

    /** Dois cards 200x100 lado a lado: a seta vai de x=200 a x=400, na altura y=50. */
    private static Connection horizontal() {
        Card a = new Card(0, 0);
        a.setWidth(200);
        a.setHeight(100);
        Card b = new Card(400, 0);
        b.setWidth(200);
        b.setHeight(100);
        return new Connection(a, b);
    }

    @Test
    void arrowGoesFromBorderToBorder() {
        Connection c = horizontal();
        assertEquals(new ConnectionGeometry.Point(200, 50), ConnectionGeometry.start(c));
        assertEquals(new ConnectionGeometry.Point(400, 50), ConnectionGeometry.tip(c));
    }

    @Test
    void labelSitsInTheMiddleOfTheArrow() {
        ConnectionGeometry.Point center = ConnectionGeometry.labelCenter(horizontal(), 60, 18, false);
        assertEquals(300, center.x(), EPS);
        assertEquals(50, center.y(), EPS);
    }

    @Test
    void labelsOfOppositeArrowsSitSideBySide() {
        Connection ab = horizontal();
        Connection ba = new Connection(ab.getTarget(), ab.getSource());
        double width = 60;

        ConnectionGeometry.Point p = ConnectionGeometry.labelCenter(ab, width, 18, true);
        ConnectionGeometry.Point q = ConnectionGeometry.labelCenter(ba, width, 18, true);

        // Cada um anda para o lado do próprio destino: A→B para a direita, B→A para a esquerda.
        assertTrue(p.x() > 300 && q.x() < 300);
        assertEquals(50, p.y(), EPS);
        assertEquals(width + 2 * ConnectionGeometry.REVERSE_LABEL_GAP, p.x() - q.x(), EPS,
                "as duas caixas ficam encostadas, sem se cobrir");
    }

    @Test
    void verticalOppositeArrowsSeparateByHeight() {
        Card top = new Card(0, 0);
        Card bottom = new Card(0, 400);
        Connection down = new Connection(top, bottom);
        Connection up = new Connection(bottom, top);
        double height = 18;

        ConnectionGeometry.Point p = ConnectionGeometry.labelCenter(down, 120, height, true);
        ConnectionGeometry.Point q = ConnectionGeometry.labelCenter(up, 120, height, true);

        assertEquals(p.x(), q.x(), EPS);
        assertEquals(height + 2 * ConnectionGeometry.REVERSE_LABEL_GAP, p.y() - q.y(), EPS);
    }
}
