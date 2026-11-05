package com.danilo.boardvisual.model;

/**
 * Geometria de uma seta, em coordenadas do board, sem JavaFX: a tela
 * ({@code ConnectionView}) e o PDF (texto invisível do texto da seta) fazem a
 * mesma conta.
 *
 * A seta é a reta entre os centros dos dois cards, cortada nas bordas: começa
 * na borda da origem e termina na borda do destino ({@link #borderPoint}).
 */
public final class ConnectionGeometry {

    /** Espaço entre os textos de A→B e B→A, quando as duas setas existem. */
    static final double REVERSE_LABEL_GAP = 6;

    private ConnectionGeometry() {
    }

    /** Um ponto do board. */
    public record Point(double x, double y) {
    }

    /**
     * Ponto onde a reta que sai do centro do card em direção a
     * ({@code towardX}, {@code towardY}) cruza a borda do card (respeita o
     * formato: retângulo, elipse, losango).
     */
    public static Point borderPoint(Card card, double towardX, double towardY) {
        double cx = card.getCenterX();
        double cy = card.getCenterY();
        double dx = towardX - cx;
        double dy = towardY - cy;
        if (dx == 0 && dy == 0) {
            return new Point(cx, cy);
        }
        // t: quanto andar na direção (dx, dy) a partir do centro até tocar a borda.
        double a = card.getWidth() / 2;
        double b = card.getHeight() / 2;
        double t = switch (card.getShape()) {
            // Seções não têm setas; a conta é a do retângulo, por completude.
            case RECTANGLE, FRAME -> Math.min(
                    dx == 0 ? Double.POSITIVE_INFINITY : a / Math.abs(dx),
                    dy == 0 ? Double.POSITIVE_INFINITY : b / Math.abs(dy));
            // (x/a)² + (y/b)² = 1
            case ELLIPSE -> 1 / Math.sqrt((dx / a) * (dx / a) + (dy / b) * (dy / b));
            // |x|/a + |y|/b = 1
            case DIAMOND -> 1 / (Math.abs(dx) / a + Math.abs(dy) / b);
        };
        return new Point(cx + dx * t, cy + dy * t);
    }

    /** Onde a seta sai (borda da origem). */
    public static Point start(Connection connection) {
        Card target = connection.getTarget();
        return borderPoint(connection.getSource(), target.getCenterX(), target.getCenterY());
    }

    /** Onde a seta chega (borda do destino, a ponta). */
    public static Point tip(Connection connection) {
        Card source = connection.getSource();
        return borderPoint(connection.getTarget(), source.getCenterX(), source.getCenterY());
    }

    /**
     * Centro do texto da seta, para um texto de {@code width} x {@code height}.
     *
     * Normalmente é o meio da seta. Se existe também a seta no sentido contrário
     * ({@code reverse}), as duas ficam sobre a mesma reta e os dois textos se
     * cobririam; então cada texto anda, ao longo da reta, em direção ao próprio
     * destino, o suficiente para os dois ficarem lado a lado (encostados, com
     * {@link #REVERSE_LABEL_GAP} de folga) em vez de um por cima do outro.
     */
    public static Point labelCenter(Connection connection, double width, double height, boolean reverse) {
        Point a = start(connection);
        Point b = tip(connection);
        double midX = (a.x() + b.x()) / 2;
        double midY = (a.y() + b.y()) / 2;
        double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
        if (!reverse || length == 0) {
            return new Point(midX, midY);
        }
        double cos = (b.x() - a.x()) / length;
        double sin = (b.y() - a.y()) / length;
        // Os textos são caixas horizontais: na reta mais deitada, basta afastar
        // meia largura na horizontal; na mais em pé, meia altura na vertical.
        double shift = Math.abs(cos) >= Math.abs(sin)
                ? width / 2 / Math.abs(cos)
                : height / 2 / Math.abs(sin);
        shift += REVERSE_LABEL_GAP;
        return new Point(midX + cos * shift, midY + sin * shift);
    }
}
