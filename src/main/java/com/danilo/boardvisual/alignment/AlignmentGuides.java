package com.danilo.boardvisual.alignment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Alinhamento simplificado ("smart guides"): quando uma linha de uma caixa em
 * movimento (borda ou centro) fica perto de uma linha de outra caixa, calcula
 * o ajuste para grudar exatamente nela e as linhas guia para mostrar.
 *
 * Não depende de JavaFX nem do modelo: trabalha só com {@link Box}. Por isso
 * serve tanto para mover quanto, no futuro, para redimensionar:
 *
 * <pre>
 * mover          → snap(caixa, Edge.ALL, Edge.ALL, outras, limite)
 *                  e aplica dx/dy na posição
 * redimensionar  → snap(caixa, {END}, {END}, outras, limite)   (ex.: canto inferior direito)
 *                  e aplica dx/dy no tamanho
 * </pre>
 *
 * Cada eixo é resolvido separadamente: pode grudar só na horizontal, só na
 * vertical ou nas duas.
 */
public final class AlignmentGuides {

    /** Tolerância para considerar duas linhas "a mesma" depois do ajuste. */
    private static final double EPSILON = 0.5;

    private AlignmentGuides() {
    }

    /** Atalho para mover uma caixa inteira (todas as linhas participam). */
    public static SnapResult snapMove(Box moving, Collection<Box> others, double threshold) {
        return snap(moving, Edge.ALL, Edge.ALL, others, threshold);
    }

    /**
     * @param moving    caixa na posição/tamanho "livre" (sem ajuste)
     * @param xEdges    linhas da caixa que podem grudar no eixo X
     * @param yEdges    linhas da caixa que podem grudar no eixo Y
     * @param others    caixas de referência (as que não estão se movendo)
     * @param threshold distância máxima para grudar, em unidades do board
     */
    public static SnapResult snap(Box moving, Set<Edge> xEdges, Set<Edge> yEdges,
                                  Collection<Box> others, double threshold) {
        double bestX = bestDelta(moving, Axis.X, xEdges, others, threshold);
        double bestY = bestDelta(moving, Axis.Y, yEdges, others, threshold);
        // NaN = nada perto naquele eixo: não ajusta.
        double dx = Double.isNaN(bestX) ? 0 : bestX;
        double dy = Double.isNaN(bestY) ? 0 : bestY;
        Box snapped = moving.translate(dx, dy);

        List<Guide> guides = new ArrayList<>();
        if (!Double.isNaN(bestX)) {
            guides.addAll(guidesFor(snapped, Axis.X, xEdges, others));
        }
        if (!Double.isNaN(bestY)) {
            guides.addAll(guidesFor(snapped, Axis.Y, yEdges, others));
        }
        return new SnapResult(dx, dy, guides);
    }

    /** Menor ajuste que encosta alguma linha da caixa numa linha de outra; NaN se nenhuma estiver perto. */
    private static double bestDelta(Box moving, Axis axis, Set<Edge> edges, Collection<Box> others, double threshold) {
        double best = Double.NaN;
        for (Edge edge : edges) {
            double value = moving.edge(axis, edge);
            for (Box other : others) {
                for (Edge otherEdge : Edge.ALL) {
                    double delta = other.edge(axis, otherEdge) - value;
                    if (Math.abs(delta) <= threshold && (Double.isNaN(best) || Math.abs(delta) < Math.abs(best))) {
                        best = delta;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Linhas guia do eixo depois do ajuste: uma para cada posição em que alguma
     * linha da caixa coincide com linhas de outras caixas. A guia vai de uma
     * ponta à outra de todas as caixas envolvidas.
     */
    private static List<Guide> guidesFor(Box snapped, Axis axis, Set<Edge> edges, Collection<Box> others) {
        List<Guide> guides = new ArrayList<>();
        for (Edge edge : edges) {
            double position = snapped.edge(axis, edge);
            double start = snapped.spanStart(axis);
            double end = snapped.spanEnd(axis);
            boolean matched = false;
            for (Box other : others) {
                for (Edge otherEdge : Edge.ALL) {
                    if (Math.abs(other.edge(axis, otherEdge) - position) < EPSILON) {
                        matched = true;
                        start = Math.min(start, other.spanStart(axis));
                        end = Math.max(end, other.spanEnd(axis));
                    }
                }
            }
            if (matched && guides.stream().noneMatch(g -> Math.abs(g.position() - position) < EPSILON)) {
                guides.add(new Guide(axis, position, start, end));
            }
        }
        return guides;
    }
}
