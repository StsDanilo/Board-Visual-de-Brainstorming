package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A "escada" do colar (3.25 do DOCUMENTACAO.md): colar de novo no mesmo lugar desce um degrau. */
class PasteStaircaseTest {

    private final PasteStaircase staircase = new PasteStaircase();

    @Test
    void firstPasteGoesExactlyToTheBasePoint() {
        assertEquals(new PasteStaircase.Spot(100, 200), staircase.next("board", 100, 200));
    }

    @Test
    void pastingAgainInTheSamePlaceDescendsOneStepEachTime() {
        staircase.next("board", 100, 200);

        assertEquals(new PasteStaircase.Spot(124, 224), staircase.next("board", 100, 200));
        assertEquals(new PasteStaircase.Spot(148, 248), staircase.next("board", 100, 200));
    }

    @Test
    void anotherBoardStartsOver() {
        staircase.next("a", 100, 200);
        staircase.next("a", 100, 200);

        assertEquals(new PasteStaircase.Spot(100, 200), staircase.next("b", 100, 200));
        assertEquals(new PasteStaircase.Spot(124, 224), staircase.next("b", 100, 200));
    }

    @Test
    void movingTheViewStartsOverAtTheNewPoint() {
        staircase.next("board", 100, 200);
        staircase.next("board", 100, 200);

        assertEquals(new PasteStaircase.Spot(500, 600), staircase.next("board", 500, 600),
                "deu pan: cola onde o usuário está olhando");
        assertEquals(new PasteStaircase.Spot(524, 624), staircase.next("board", 500, 600));
    }

    @Test
    void aSubPixelDifferenceIsStillTheSamePoint() {
        staircase.next("board", 100, 200);

        assertEquals(new PasteStaircase.Spot(124.2, 224.2), staircase.next("board", 100.2, 200.2));
    }

    @Test
    void resetStartsOverInTheSameBoardAndPlace() {
        staircase.next("board", 100, 200);
        staircase.next("board", 100, 200);

        staircase.reset(); // copiou outra coisa

        assertEquals(new PasteStaircase.Spot(100, 200), staircase.next("board", 100, 200));
    }
}
