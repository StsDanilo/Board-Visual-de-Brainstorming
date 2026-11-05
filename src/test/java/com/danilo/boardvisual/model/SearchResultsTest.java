package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Percorrer os resultados (Enter / Shift+Enter): ordem, volta ao começo, contador e o atual que se mantém. */
class SearchResultsTest {

    private final Board board = new Board("b");
    private final Card a = add("tema a");
    private final Card b = add("tema b");
    private final Card c = add("tema c");

    private Card add(String text) {
        Card card = new Card(0, 0);
        card.setText(text);
        board.addCard(card);
        return card;
    }

    private List<BoardSearch.Hit> search(String query) {
        return BoardSearch.searchBoard(board, query);
    }

    private static Card cardOf(BoardSearch.Hit hit) {
        return hit.card();
    }

    @Test
    void startsOnTheFirstResult() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));

        assertEquals(3, results.size());
        assertEquals(0, results.index());
        assertEquals(a, cardOf(results.current()));
        assertEquals("1 de 3", results.counterText());
    }

    @Test
    void nextWalksForwardAndWrapsAround() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));

        assertEquals(b, cardOf(results.next()));
        assertEquals("2 de 3", results.counterText());
        assertEquals(c, cardOf(results.next()));
        assertEquals(a, cardOf(results.next()), "depois do último, o primeiro");
        assertEquals("1 de 3", results.counterText());
    }

    @Test
    void previousWalksBackwardAndWrapsAround() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));

        assertEquals(c, cardOf(results.previous()), "antes do primeiro, o último");
        assertEquals("3 de 3", results.counterText());
        assertEquals(b, cardOf(results.previous()));
        assertEquals(a, cardOf(results.previous()));
    }

    @Test
    void emptyResultsHaveNoCurrentAndNothingToWalk() {
        SearchResults results = new SearchResults();
        assertTrue(results.isEmpty());
        assertNull(results.current());
        assertNull(results.next());
        assertNull(results.previous());
        assertEquals(-1, results.index());
        assertEquals("", results.counterText());

        results.replace(search("tema"));
        results.replace(search("nada disso"));
        assertTrue(results.isEmpty());
        assertNull(results.current());
        assertEquals("", results.counterText());
    }

    @Test
    void singleResultStaysOnItself() {
        SearchResults results = new SearchResults();
        results.replace(search("tema b"));
        assertEquals(b, cardOf(results.next()));
        assertEquals(b, cardOf(results.previous()));
        assertEquals("1 de 1", results.counterText());
    }

    @Test
    void theCurrentResultSurvivesARefinedSearch() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));
        results.next();
        results.next(); // atual: c

        results.replace(search("tema c")); // só c casa agora
        assertEquals(c, cardOf(results.current()));
        assertEquals("1 de 1", results.counterText());

        results.replace(search("tema")); // volta a casar tudo: continua em c
        assertEquals(c, cardOf(results.current()));
        assertEquals("3 de 3", results.counterText());
    }

    @Test
    void whenTheCurrentOneIsGoneItGoesBackToTheFirst() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));
        results.next(); // atual: b

        results.replace(search("tema c")); // b saiu
        assertEquals(c, cardOf(results.current()));

        results.select(0);
        results.replace(search("tema a"));
        assertEquals(a, cardOf(results.current()));
    }

    @Test
    void selectGoesToThatResultAndRejectsOutOfRange() {
        SearchResults results = new SearchResults();
        results.replace(search("tema"));

        assertTrue(results.select(2));
        assertEquals(c, cardOf(results.current()));
        assertFalse(results.select(3));
        assertFalse(results.select(-1));
        assertEquals(c, cardOf(results.current()), "escolha inválida não muda nada");
    }

    @Test
    void hitsInABoardAreTheOnesToHighlightWhenItIsOnScreen() {
        Board child = new Board("filho");
        Card door = add("porta");
        door.setChildBoard(child);
        Card inside = new Card(0, 0);
        inside.setText("tema dentro");
        child.addCard(inside);

        SearchResults results = new SearchResults();
        results.replace(BoardSearch.search(board, board, "tema", BoardSearch.Scope.ALL_BOARDS));

        assertEquals(4, results.size());
        assertEquals(3, results.hitsIn(board).size());
        assertEquals(List.of(inside), results.hitsIn(child).stream().map(BoardSearch.Hit::card).toList());
        assertTrue(results.currentIsIn(board));
        results.select(3);
        assertTrue(results.currentIsIn(child));
        assertFalse(results.currentIsIn(board));
    }
}
