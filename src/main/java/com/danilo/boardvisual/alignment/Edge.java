package com.danilo.boardvisual.alignment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Uma das três linhas de uma caixa em um eixo: início (esquerda/topo),
 * centro, fim (direita/base).
 */
public enum Edge {
    START,
    CENTER,
    END;

    /** Todas as linhas: é o que participa ao mover uma caixa inteira. */
    public static final Set<Edge> ALL = EnumSet.allOf(Edge.class);
}
