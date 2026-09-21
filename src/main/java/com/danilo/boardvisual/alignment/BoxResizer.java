package com.danilo.boardvisual.alignment;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cálculo do redimensionamento de uma caixa por uma alça, com alinhamento.
 *
 * Usa o mesmo {@link AlignmentGuides} de quando se move um card, mas só com
 * as linhas puxadas pela alça, e aplica o ajuste no tamanho (e na posição,
 * quando a alça puxa o início: esquerda/topo, para o lado oposto ficar parado).
 *
 * Java puro, testável sem interface.
 */
public final class BoxResizer {

    /** Resultado: a nova caixa e as linhas guia a mostrar. */
    public record Result(Box box, List<Guide> guides) {

        public Result {
            guides = List.copyOf(guides);
        }
    }

    private BoxResizer() {
    }

    /**
     * @param start      caixa no início do gesto
     * @param handle     alça sendo arrastada
     * @param dx         deslocamento do mouse desde o início, em unidades do board
     * @param dy         idem
     * @param minWidth   largura mínima
     * @param minHeight  altura mínima
     * @param keepAspect mantém a proporção de {@code start} (só faz sentido em alças de canto)
     * @param others     caixas de referência para o alinhamento
     * @param threshold  distância para grudar; 0 ou menos desliga o alinhamento
     */
    public static Result resize(Box start, ResizeHandle handle, double dx, double dy,
                                double minWidth, double minHeight, boolean keepAspect,
                                Collection<Box> others, double threshold) {
        if (keepAspect && handle.isCorner()) {
            return resizeKeepingAspect(start, handle, dx, minWidth, minHeight, others, threshold);
        }

        Box free = pull(start, handle, dx, dy);
        SnapResult snap = snapPulledEdges(free, handle.xEdge(), handle.yEdge(), others, threshold);
        Box snapped = pull(free, handle, snap.dx(), snap.dy());
        Box clamped = clamp(snapped, start, handle, minWidth, minHeight);

        // Se o mínimo "segurou" o tamanho num eixo, a guia daquele eixo não vale mais.
        boolean xHeld = clamped.width() != snapped.width();
        boolean yHeld = clamped.height() != snapped.height();
        List<Guide> guides = snap.guides().stream()
                .filter(g -> !(g.axis() == Axis.X && xHeld) && !(g.axis() == Axis.Y && yHeld))
                .toList();
        return new Result(clamped, guides);
    }

    /**
     * Proporção fixa: a largura manda (com alinhamento no eixo X) e a altura
     * acompanha. O canto oposto à alça fica parado.
     */
    private static Result resizeKeepingAspect(Box start, ResizeHandle handle, double dx,
                                              double minWidth, double minHeight,
                                              Collection<Box> others, double threshold) {
        double ratio = start.width() / start.height();
        Box free = pull(start, handle, dx, 0);
        SnapResult snap = snapPulledEdges(free, handle.xEdge(), null, others, threshold);
        Box snapped = pull(free, handle, snap.dx(), 0);

        double width = Math.max(snapped.width(), Math.max(minWidth, minHeight * ratio));
        double height = width / ratio;
        double x = handle.xEdge() == Edge.START ? start.x() + start.width() - width : start.x();
        double y = handle.yEdge() == Edge.START ? start.y() + start.height() - height : start.y();
        List<Guide> guides = width == snapped.width() ? snap.guides() : List.of();
        return new Result(new Box(x, y, width, height), guides);
    }

    /** Move as linhas puxadas pela alça: fim muda o tamanho; início muda posição e tamanho. */
    private static Box pull(Box box, ResizeHandle handle, double dx, double dy) {
        double x = box.x();
        double y = box.y();
        double width = box.width();
        double height = box.height();
        if (handle.xEdge() == Edge.END) {
            width += dx;
        } else if (handle.xEdge() == Edge.START) {
            x += dx;
            width -= dx;
        }
        if (handle.yEdge() == Edge.END) {
            height += dy;
        } else if (handle.yEdge() == Edge.START) {
            y += dy;
            height -= dy;
        }
        return new Box(x, y, width, height);
    }

    private static SnapResult snapPulledEdges(Box free, Edge xEdge, Edge yEdge,
                                              Collection<Box> others, double threshold) {
        if (threshold <= 0) {
            return SnapResult.NONE;
        }
        return AlignmentGuides.snap(free, edges(xEdge), edges(yEdge), others, threshold);
    }

    private static Set<Edge> edges(Edge edge) {
        return edge == null ? EnumSet.noneOf(Edge.class) : EnumSet.of(edge);
    }

    /** Aplica o tamanho mínimo mantendo parado o lado oposto à alça. */
    private static Box clamp(Box box, Box start, ResizeHandle handle, double minWidth, double minHeight) {
        double width = Math.max(box.width(), minWidth);
        double height = Math.max(box.height(), minHeight);
        double x = handle.xEdge() == Edge.START ? start.x() + start.width() - width : box.x();
        double y = handle.yEdge() == Edge.START ? start.y() + start.height() - height : box.y();
        return new Box(x, y, width, height);
    }
}
