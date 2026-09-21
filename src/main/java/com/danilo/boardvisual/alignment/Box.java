package com.danilo.boardvisual.alignment;

/**
 * Retângulo em coordenadas do board: o que o alinhamento enxerga de um card
 * (ou de um grupo de cards). Formatos não retangulares usam a caixa que os
 * envolve, como nos editores gráficos.
 */
public record Box(double x, double y, double width, double height) {

    /** Valor da borda no eixo informado (ex.: X + START = esquerda). */
    public double edge(Axis axis, Edge edge) {
        double start = axis == Axis.X ? x : y;
        double size = axis == Axis.X ? width : height;
        return switch (edge) {
            case START -> start;
            case CENTER -> start + size / 2;
            case END -> start + size;
        };
    }

    /** Início e fim no eixo perpendicular (usado para o comprimento da linha guia). */
    double spanStart(Axis axis) {
        return axis == Axis.X ? y : x;
    }

    double spanEnd(Axis axis) {
        return axis == Axis.X ? y + height : x + width;
    }

    /** A mesma caixa deslocada. */
    public Box translate(double dx, double dy) {
        return new Box(x + dx, y + dy, width, height);
    }

    /** Menor caixa que envolve as duas. */
    public Box union(Box other) {
        double minX = Math.min(x, other.x);
        double minY = Math.min(y, other.y);
        double maxX = Math.max(x + width, other.x + other.width);
        double maxY = Math.max(y + height, other.y + other.height);
        return new Box(minX, minY, maxX - minX, maxY - minY);
    }
}
