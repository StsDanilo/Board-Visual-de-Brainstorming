package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedBoardTest {

    /** Board raiz com um card "Nível 2", que contém um card "Nível 3", que contém um card folha. */
    private static Board threeLevels() {
        Board root = new Board("Principal");
        Card level2 = new Card(0, 0);
        level2.setText("Nível 2");
        level2.setChildBoard(new Board(""));
        root.addCard(level2);

        Card level3 = new Card(10, 10);
        level3.setText("Nível 3");
        level3.setChildBoard(new Board(""));
        level2.getChildBoard().addCard(level3);

        Card leaf = new Card(20, 20);
        leaf.setText("folha");
        level3.getChildBoard().addCard(leaf);
        return root;
    }

    private static Card only(Board board) {
        return board.getCards().get(0);
    }

    @Test
    void restoreKeepsExistingChildBoardUntouched() {
        Board root = threeLevels();
        Card level2 = only(root);
        Board child = level2.getChildBoard();
        BoardSnapshot before = root.snapshot();

        // Algo feito dentro do filho, depois uma mudança no pai.
        child.addCard(new Card(99, 99));
        level2.setColor("#CFE2FF");
        root.restore(before);

        assertEquals(Card.DEFAULT_COLOR, level2.getColor(), "o pai volta");
        assertSame(child, level2.getChildBoard(), "mesmo board filho");
        assertEquals(2, child.getCards().size(), "o que foi feito no filho não é desfeito pelo pai");
    }

    @Test
    void restoreOfDeletedCardBringsWholeSubtreeBack() {
        Board root = threeLevels();
        BoardSnapshot before = root.snapshot();

        root.removeCard(only(root));
        assertTrue(root.getCards().isEmpty());
        root.restore(before);

        Card level2 = only(root);
        Card level3 = only(level2.getChildBoard());
        assertEquals("Nível 3", level3.getText());
        assertEquals("folha", only(level3.getChildBoard()).getText());
    }

    @Test
    void deepCopyIsIndependent() {
        Board root = threeLevels();
        Card level2 = only(root);

        Card copy = level2.copy();
        Board copiedChild = copy.getChildBoard();
        assertNotSame(level2.getChildBoard(), copiedChild);
        only(only(copiedChild).getChildBoard()).setText("alterado na cópia");

        assertEquals("folha", only(only(level2.getChildBoard()).getChildBoard()).getText());
    }

    @Test
    void snapshotEqualityIncludesChildren() {
        Board root = threeLevels();
        BoardSnapshot before = root.snapshot();
        only(only(only(root).getChildBoard()).getChildBoard()).setText("mudou lá no fundo");
        assertTrue(!before.equals(root.snapshot()));
    }

    @Test
    void normalCardHasNoChildBoard() {
        assertNull(new Card(0, 0).getChildBoard());
    }
}
