package com.danilo.boardvisual.alignment;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlignmentGuidesTest {

    private static final double THRESHOLD = 6;
    private static final double EPS = 1e-9;

    /** Referência fixa: x 100..300, y 100..200 (centro 200, 150). */
    private final Box reference = new Box(100, 100, 200, 100);

    @Test
    void snapsLeftEdgeToLeftEdge() {
        Box moving = new Box(104, 400, 100, 50); // esquerda a 4 da esquerda da referência
        SnapResult r = AlignmentGuides.snapMove(moving, List.of(reference), THRESHOLD);

        assertEquals(-4, r.dx(), EPS);
        assertEquals(0, r.dy(), EPS, "nada perto no eixo Y");
        Guide guide = r.guides().get(0);
        assertEquals(Axis.X, guide.axis());
        assertEquals(100, guide.position(), EPS);
        assertEquals(100, guide.start(), EPS, "guia começa no topo da referência");
        assertEquals(450, guide.end(), EPS, "e termina na base da caixa movida");
    }

    @Test
    void snapsCenterToCenterOnBothAxes() {
        Box moving = new Box(152, 128, 100, 40); // centro (202, 148): perto de (200, 150)
        SnapResult r = AlignmentGuides.snapMove(moving, List.of(reference), THRESHOLD);

        assertEquals(-2, r.dx(), EPS);
        assertEquals(2, r.dy(), EPS);
        assertTrue(r.guides().stream().anyMatch(g -> g.axis() == Axis.X && Math.abs(g.position() - 200) < EPS));
        assertTrue(r.guides().stream().anyMatch(g -> g.axis() == Axis.Y && Math.abs(g.position() - 150) < EPS));
    }

    @Test
    void picksTheClosestCandidate() {
        Box other = new Box(500, 100, 50, 50);
        // Esquerda (303) a 3 de 300 (direita da referência); direita (493) a 7 de 500.
        Box moving = new Box(303, 300, 190, 50);
        assertEquals(-3, AlignmentGuides.snapMove(moving, List.of(reference, other), THRESHOLD).dx(), EPS);

        // Invertendo: esquerda (306) a 6 de 300; direita (498) a 2 de 500 → ganha a direita.
        Box closerRight = new Box(306, 300, 192, 50);
        assertEquals(2, AlignmentGuides.snapMove(closerRight, List.of(reference, other), THRESHOLD).dx(), EPS);
    }

    @Test
    void doesNotSnapBeyondThreshold() {
        Box moving = new Box(110, 400, 100, 50); // 10 de distância
        SnapResult r = AlignmentGuides.snapMove(moving, List.of(reference), THRESHOLD);
        assertEquals(0, r.dx(), EPS);
        assertTrue(r.guides().isEmpty());
    }

    @Test
    void alreadyAlignedShowsGuideWithoutMoving() {
        Box moving = new Box(100, 400, 60, 60);
        SnapResult r = AlignmentGuides.snapMove(moving, List.of(reference), THRESHOLD);
        assertEquals(0, r.dx(), EPS);
        assertEquals(1, r.guides().size());
    }

    @Test
    void guideSpansAllAlignedBoxes() {
        Box below = new Box(100, 600, 80, 80);
        Box moving = new Box(102, 350, 50, 50);
        SnapResult r = AlignmentGuides.snapMove(moving, List.of(reference, below), THRESHOLD);

        Guide guide = r.guides().get(0);
        assertEquals(100, guide.start(), EPS, "do topo da primeira");
        assertEquals(680, guide.end(), EPS, "até a base da última");
    }

    @Test
    void resizeUsesOnlyTheDraggedEdges() {
        // Redimensionando pelo canto inferior direito: só END participa.
        Set<Edge> dragged = EnumSet.of(Edge.END);
        Box resizing = new Box(0, 0, 297, 60); // direita em 297, perto de 300 (direita da ref)
        SnapResult r = AlignmentGuides.snap(resizing, dragged, dragged, List.of(reference), THRESHOLD);
        assertEquals(3, r.dx(), EPS, "a largura cresceria 3 para encostar em 300");

        // O início (x = 0) não participa, mesmo se estiver perto de algo.
        Box nearStart = new Box(98, 0, 50, 60); // esquerda perto de 100, mas é o lado fixo
        SnapResult fixedStart = AlignmentGuides.snap(nearStart, dragged, dragged, List.of(reference), THRESHOLD);
        assertEquals(0, fixedStart.dx(), EPS);
    }
}
