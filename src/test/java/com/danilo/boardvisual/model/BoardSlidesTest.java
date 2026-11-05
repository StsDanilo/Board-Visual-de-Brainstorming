package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A ordem dos slides da apresentação: a mesma das páginas do PDF, sem tela. */
class BoardSlidesTest {

    /**
     * <pre>
     * Principal                      (0)
     *   Projeto A                    (1)
     *     Orçamento                  (2)
     *   Projeto B                    (3)
     *     Custos                     (4)
     *       Detalhe                  (5)
     *   Projeto C                    (6)
     * </pre>
     * O Projeto B tem também um card comum (sem board) antes do card "Custos".
     */
    private final BoardTreeTest.Sample s = new BoardTreeTest.Sample();
    private final Card projectC = BoardTreeTest.Sample.cardBoard(s.root, "Projeto C");

    private List<Board> expected() {
        return List.of(s.root, s.projectA.getChildBoard(), s.budget.getChildBoard(),
                s.projectB.getChildBoard(), s.costs.getChildBoard(), s.detail.getChildBoard(),
                projectC.getChildBoard());
    }

    @Test
    void orderIsTheSameAsThePdfPages() {
        assertEquals(expected(), BoardSlides.order(s.root),
                "board, depois os filhos de cada card na ordem dos cards, em profundidade");
        assertEquals(BoardTree.entries(s.root).stream().map(BoardTree.Entry::board).toList(),
                BoardSlides.order(s.root));
        assertEquals(7, BoardSlides.count(s.root));
    }

    @Test
    void nextWalksTheWholeTreeInOrder() {
        List<Board> walked = new ArrayList<>();
        Board current = s.root;
        walked.add(current);
        while (BoardSlides.next(s.root, current).isPresent()) {
            current = BoardSlides.next(s.root, current).get();
            walked.add(current);
        }
        assertEquals(expected(), walked);
    }

    @Test
    void previousWalksBackwards() {
        List<Board> walked = new ArrayList<>();
        Board current = projectC.getChildBoard();
        walked.add(current);
        while (BoardSlides.previous(s.root, current).isPresent()) {
            current = BoardSlides.previous(s.root, current).get();
            walked.add(current);
        }
        List<Board> reversed = new ArrayList<>(expected());
        java.util.Collections.reverse(reversed);
        assertEquals(reversed, walked);
    }

    @Test
    void nextOfAParentIsItsFirstChildAndNextOfTheLastChildIsTheNextSibling() {
        assertSame(s.projectA.getChildBoard(), BoardSlides.next(s.root, s.root).orElseThrow(),
                "do principal vai ao primeiro filho");
        assertSame(s.projectB.getChildBoard(),
                BoardSlides.next(s.root, s.budget.getChildBoard()).orElseThrow(),
                "do último filho de A vai ao irmão de A (B), subindo um nível");
        assertSame(projectC.getChildBoard(),
                BoardSlides.next(s.root, s.detail.getChildBoard()).orElseThrow(),
                "do mais fundo vai ao próximo irmão de algum ancestral");
        assertSame(s.root, BoardSlides.previous(s.root, s.projectA.getChildBoard()).orElseThrow());
        assertSame(s.detail.getChildBoard(),
                BoardSlides.previous(s.root, projectC.getChildBoard()).orElseThrow(),
                "o anterior de C é o último descendente de B");
    }

    @Test
    void theEndsHaveNoNeighbor() {
        assertTrue(BoardSlides.previous(s.root, s.root).isEmpty(), "o primeiro não tem anterior");
        assertTrue(BoardSlides.next(s.root, projectC.getChildBoard()).isEmpty(), "o último não tem próximo");
    }

    @Test
    void aSingleBoardHasNoNeighbors() {
        Board alone = new Board("Sozinho");
        assertEquals(1, BoardSlides.count(alone));
        assertTrue(BoardSlides.next(alone, alone).isEmpty());
        assertTrue(BoardSlides.previous(alone, alone).isEmpty());
        assertEquals(0, BoardSlides.indexOf(alone, alone));
    }

    @Test
    void aBoardOutsideTheTreeHasNoNeighbors() {
        Board stranger = new Board("De fora");
        assertEquals(-1, BoardSlides.indexOf(s.root, stranger));
        assertTrue(BoardSlides.next(s.root, stranger).isEmpty());
        assertTrue(BoardSlides.previous(s.root, stranger).isEmpty());
        assertEquals(-1, BoardSlides.indexOf(s.root, null));
        assertTrue(BoardSlides.next(s.root, null).isEmpty());
        assertTrue(BoardSlides.order(null).isEmpty());
    }

    @Test
    void cardsWithoutChildBoardAreSkippedAndEmptyBoardsStillCount() {
        Board root = new Board("R");
        root.addCard(new Card(0, 0)); // card comum: não é um slide
        Card first = BoardTreeTest.Sample.cardBoard(root, "Vazio"); // board filho sem nenhum card: continua sendo um slide
        root.addCard(new Card(900, 0));
        Card second = BoardTreeTest.Sample.cardBoard(root, "Outro");

        assertEquals(List.of(root, first.getChildBoard(), second.getChildBoard()), BoardSlides.order(root));
        assertSame(second.getChildBoard(), BoardSlides.next(root, first.getChildBoard()).orElseThrow());
    }

    @Test
    void indexIsFoundByIdSoARestoredCopyOfTheSameBoardStillCounts() {
        // Desfazer uma exclusão recria cards e boards como objetos novos, com os mesmos ids.
        Board copyOfBudget = new Board(s.budget.getChildBoard().getId(), "outro objeto");
        assertEquals(2, BoardSlides.indexOf(s.root, copyOfBudget));
        assertSame(s.projectB.getChildBoard(), BoardSlides.next(s.root, copyOfBudget).orElseThrow());
    }
}
