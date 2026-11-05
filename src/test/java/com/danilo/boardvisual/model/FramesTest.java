package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seções (frames): conteúdo por geometria (centro dentro da área), caixa de seleção e camadas. */
class FramesTest {

    /** Seção de 400 × 300 com o canto em (0, 0). */
    private static Card frame(double x, double y, double w, double h) {
        Card frame = new Card(x, y);
        frame.setShape(CardShape.FRAME);
        frame.resize(w, h);
        return frame;
    }

    /** Card de 100 × 60 com o centro no ponto. */
    private static Card cardAt(double centerX, double centerY) {
        Card card = new Card(centerX - 50, centerY - 30);
        card.resize(100, 60);
        return card;
    }

    @Test
    void cardIsInsideWhenItsCenterIsInsideTheFrame() {
        Card frame = frame(0, 0, 400, 300);

        assertTrue(Frames.contains(frame, cardAt(200, 150)));
        // Metade para fora, mas o centro dentro: está dentro.
        assertTrue(Frames.contains(frame, cardAt(390, 290)));
        // A maior parte em cima da seção, mas o centro fora: não está.
        assertFalse(Frames.contains(frame, cardAt(410, 150)));
        assertFalse(Frames.contains(frame, cardAt(200, -1)));
    }

    @Test
    void frameBordersCountAsInside() {
        Card frame = frame(0, 0, 400, 300);

        assertTrue(Frames.contains(frame, cardAt(0, 0)), "canto de cima à esquerda");
        assertTrue(Frames.contains(frame, cardAt(400, 300)), "canto de baixo à direita");
        assertTrue(Frames.contains(frame, cardAt(400, 150)), "borda direita");
        assertFalse(Frames.contains(frame, cardAt(400.01, 150)), "logo depois da borda");
    }

    @Test
    void cardBiggerThanTheFrameCountsByItsCenter() {
        Card frame = frame(0, 0, 100, 100);
        Card big = new Card(-200, -200);
        big.resize(500, 500); // centro em (50, 50)

        assertTrue(Frames.contains(frame, big));
    }

    @Test
    void aFrameIsNotInsideItself() {
        Card frame = frame(0, 0, 400, 300);
        assertFalse(Frames.contains(frame, frame));
    }

    @Test
    void contentsFollowTheListOrder() {
        Card frame = frame(0, 0, 400, 300);
        Card a = cardAt(100, 100);
        Card outside = cardAt(900, 100);
        Card b = cardAt(300, 200);

        assertEquals(List.of(a, b), Frames.contentsOf(frame, List.of(frame, a, outside, b)));
    }

    @Test
    void cardInsideTwoMovedFramesIsCarriedOnce() {
        Card left = frame(0, 0, 400, 300);
        Card right = frame(200, 0, 400, 300); // sobreposta à esquerda
        Card shared = cardAt(300, 150);       // dentro das duas
        Card onlyLeft = cardAt(100, 150);
        Card onlyRight = cardAt(500, 150);

        Set<Card> carried = Frames.carriedBy(List.of(left, right), List.of(left, right, shared, onlyLeft, onlyRight));

        assertEquals(Set.of(shared, onlyLeft, onlyRight), carried);
    }

    @Test
    void framesBeingMovedAreNotCarriedEvenIfInsideAnother() {
        Card big = frame(0, 0, 1000, 800);
        Card small = frame(100, 100, 300, 200);

        Set<Card> carried = Frames.carriedBy(List.of(big, small), List.of(big, small));

        assertTrue(carried.isEmpty(), "as duas já se movem: nada a levar a mais");
    }

    @Test
    void nestedFrameIsCarriedWithWhatIsInsideIt() {
        Card big = frame(0, 0, 600, 400);
        Card small = frame(400, 250, 300, 200); // centro (550, 350) dentro da grande; metade para fora
        Card inSmallOnly = cardAt(650, 420);    // fora da grande, dentro da pequena
        Card outside = cardAt(900, 900);

        Set<Card> carried = Frames.carriedBy(List.of(big), List.of(big, small, inSmallOnly, outside));

        assertEquals(Set.of(small, inSmallOnly), carried, "seção dentro de seção leva o que tem dentro");
    }

    @Test
    void movingOnlyCommonCardsCarriesNothing() {
        Card frame = frame(0, 0, 400, 300);
        Card card = cardAt(100, 100);
        Card other = cardAt(120, 120);

        assertTrue(Frames.carriedBy(List.of(card), List.of(frame, card, other)).isEmpty());
    }

    @Test
    void lockedCardInsideIsCarried() {
        Card frame = frame(0, 0, 400, 300);
        Card locked = cardAt(100, 100);
        locked.setLocked(true);

        assertEquals(Set.of(locked), Frames.carriedBy(List.of(frame), List.of(frame, locked)));
    }

    @Test
    void marqueeTakesCommonCardsByTouchAndFramesOnlyWhole() {
        Card frame = frame(0, 0, 400, 300);
        Card card = cardAt(100, 100); // 50..150 × 70..130

        // Caixa no meio da seção, tocando o card.
        assertTrue(Frames.marqueeSelects(card, 140, 120, 50, 50));
        assertFalse(Frames.marqueeSelects(frame, 140, 120, 50, 50), "caixa dentro da seção não pega a seção");
        assertFalse(Frames.marqueeSelects(frame, -10, -10, 300, 400), "tocar não basta");
        assertTrue(Frames.marqueeSelects(frame, -10, -10, 420, 320), "seção inteira dentro: pega");
        assertTrue(Frames.marqueeSelects(frame, 0, 0, 400, 300), "bordas iguais contam");
    }

    @Test
    void layeredPutsFramesFirstKeepingTheRelativeOrder() {
        Card a = cardAt(0, 0);
        Card f1 = frame(0, 0, 10, 10);
        Card b = cardAt(0, 0);
        Card f2 = frame(0, 0, 10, 10);

        assertEquals(List.of(f1, f2, a, b), Frames.layered(List.of(a, f1, b, f2)));
    }
}
