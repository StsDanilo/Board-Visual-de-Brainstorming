package com.danilo.boardvisual.model;

import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordem de empilhamento no modelo: {@link Board#reorder}, foto/restauração. */
class StackOrderTest {

    private final Board board = new Board("pilha");
    // De baixo (a) para cima (e).
    private final Card a = add("a");
    private final Card b = add("b");
    private final Card c = add("c");
    private final Card d = add("d");
    private final Card e = add("e");

    private Card add(String id) {
        Card card = new Card(id, 0, 0);
        board.addCard(card);
        return card;
    }

    private String order() {
        StringBuilder text = new StringBuilder();
        board.getCards().forEach(card -> text.append(card.getId()));
        return text.toString();
    }

    @Test
    void forwardAndBackwardMoveOneLevel() {
        assertTrue(board.reorder(List.of(b), StackMove.FORWARD));
        assertEquals("acbde", order());
        assertTrue(board.reorder(List.of(b), StackMove.BACKWARD));
        assertEquals("abcde", order());
    }

    @Test
    void toFrontAndToBack() {
        assertTrue(board.reorder(List.of(b), StackMove.TO_FRONT));
        assertEquals("acdeb", order());
        assertTrue(board.reorder(List.of(d), StackMove.TO_BACK));
        assertEquals("daceb", order());
    }

    @Test
    void limitsChangeNothing() {
        assertFalse(board.reorder(List.of(e), StackMove.FORWARD), "já no topo");
        assertFalse(board.reorder(List.of(e), StackMove.TO_FRONT));
        assertFalse(board.reorder(List.of(a), StackMove.BACKWARD), "já no fundo");
        assertFalse(board.reorder(List.of(a), StackMove.TO_BACK));
        assertFalse(board.reorder(List.of(), StackMove.TO_FRONT), "nada selecionado");
        assertFalse(board.reorder(List.of(new Card("fora", 0, 0)), StackMove.TO_FRONT), "card de outro board");
        assertFalse(board.canReorder(List.of(e), StackMove.FORWARD));
        assertTrue(board.canReorder(List.of(e), StackMove.BACKWARD));
        assertEquals("abcde", order());
    }

    @Test
    void severalCardsKeepTheirRelativeOrder() {
        // Selecionados em ordem diferente da pilha: o resultado não depende disso.
        assertTrue(board.reorder(List.of(d, b), StackMove.TO_FRONT));
        assertEquals("acebd", order());
        assertTrue(board.reorder(List.of(d, b), StackMove.TO_BACK));
        assertEquals("bdace", order());
    }

    @Test
    void severalCardsMoveOneLevelEach() {
        assertTrue(board.reorder(List.of(b, d), StackMove.FORWARD));
        assertEquals("acbed", order());
        // a e c ficaram juntos no fundo: o bloco sobe junto, um nível (passa o b).
        assertTrue(board.reorder(List.of(a, c), StackMove.FORWARD));
        assertEquals("baced", order());
    }

    @Test
    void blockOfAdjacentCardsMovesTogether() {
        assertTrue(board.reorder(List.of(b, c), StackMove.FORWARD));
        assertEquals("adbce", order());
        assertTrue(board.reorder(List.of(b, c), StackMove.BACKWARD));
        assertEquals("abcde", order());
    }

    @Test
    void cardAtTheTopStaysAndTheOtherSelectedMoves() {
        assertTrue(board.reorder(List.of(c, e), StackMove.FORWARD));
        assertEquals("abdce", order(), "e já está no topo; c sobe um nível");
        assertTrue(board.reorder(List.of(d, e), StackMove.FORWARD));
        assertEquals("abcde", order(), "d passa o c; e continua no topo");
    }

    @Test
    void reorderIsAPermutationNotRemoveAndAdd() {
        List<String> events = new ArrayList<>();
        board.getCards().addListener((ListChangeListener<Card>) change -> {
            while (change.next()) {
                events.add(change.wasPermutated() ? "permutação"
                        : change.wasRemoved() ? "removido" : change.wasAdded() ? "adicionado" : "outro");
            }
        });

        board.reorder(List.of(a), StackMove.TO_FRONT);

        assertEquals(List.of("permutação"), events);
    }

    @Test
    void snapshotSeesTheOrder() {
        BoardSnapshot before = board.snapshot();
        board.reorder(List.of(a), StackMove.FORWARD);
        assertNotEquals(before, board.snapshot(), "mudar só a ordem é uma mudança (vira passo no desfazer)");
    }

    @Test
    void restoreBringsBackTheOrderWithTheSameObjects() {
        BoardSnapshot before = board.snapshot();
        board.reorder(List.of(a, c), StackMove.TO_FRONT);
        List<String> events = new ArrayList<>();
        board.getCards().addListener((ListChangeListener<Card>) change -> {
            while (change.next()) {
                events.add(change.wasPermutated() ? "permutação" : "outro");
            }
        });

        board.restore(before);

        assertEquals("abcde", order());
        assertEquals(List.of("permutação"), events, "os cards continuam os mesmos objetos");
        assertEquals(Set.of(a, b, c, d, e), Set.copyOf(board.getCards()));
    }

    @Test
    void restoreAfterDeletePutsTheCardBackInItsPlace() {
        BoardSnapshot before = board.snapshot();
        board.removeCard(b);

        board.restore(before);

        assertEquals("abcde", order(), "o card excluído volta ao seu lugar na pilha, não ao topo");
    }

    @Test
    void restoreIntoAnEmptyBoardKeepsTheOrder() {
        board.reorder(List.of(a), StackMove.TO_FRONT);
        Board other = new Board("outro");

        other.restore(board.snapshot());

        assertEquals(List.of("b", "c", "d", "e", "a"), other.getCards().stream().map(Card::getId).toList());
    }

    @Test
    void deepCopyKeepsTheOrderOfNestedBoards() {
        Card holder = new Card("holder", 0, 0);
        Board child = new Board("filho");
        child.addCard(new Card("x", 0, 0));
        child.addCard(new Card("y", 0, 0));
        child.reorder(List.of(child.getCards().get(0)), StackMove.TO_FRONT);
        holder.setChildBoard(child);

        Card copy = holder.copy();

        assertEquals(List.of("y", "x"), copy.getChildBoard().getCards().stream().map(Card::getId).toList());
    }
}
