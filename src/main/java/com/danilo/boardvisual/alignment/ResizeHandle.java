package com.danilo.boardvisual.alignment;

/**
 * As 8 alças de redimensionamento de uma caixa.
 *
 * Cada alça diz qual linha de cada eixo ela puxa: a alça leste (E) puxa o
 * fim do eixo X (borda direita) e nada no Y; a sudeste (SE) puxa o fim dos
 * dois. {@code null} = aquele eixo não muda.
 */
public enum ResizeHandle {
    NORTH_WEST(Edge.START, Edge.START),
    NORTH(null, Edge.START),
    NORTH_EAST(Edge.END, Edge.START),
    EAST(Edge.END, null),
    SOUTH_EAST(Edge.END, Edge.END),
    SOUTH(null, Edge.END),
    SOUTH_WEST(Edge.START, Edge.END),
    WEST(Edge.START, null);

    private final Edge xEdge;
    private final Edge yEdge;

    ResizeHandle(Edge xEdge, Edge yEdge) {
        this.xEdge = xEdge;
        this.yEdge = yEdge;
    }

    /** Linha puxada no eixo X ({@code START} = esquerda, {@code END} = direita), ou {@code null}. */
    public Edge xEdge() {
        return xEdge;
    }

    /** Linha puxada no eixo Y ({@code START} = topo, {@code END} = base), ou {@code null}. */
    public Edge yEdge() {
        return yEdge;
    }

    public boolean isCorner() {
        return xEdge != null && yEdge != null;
    }
}
