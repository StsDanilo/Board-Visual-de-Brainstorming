package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Texto e estilo das setas, e as regras do {@link Board#connect} com estilo. */
class ConnectionTest {

    private static Board boardWith(Card... cards) {
        Board board = new Board("teste");
        for (Card card : cards) {
            board.addCard(card);
        }
        return board;
    }

    @Test
    void newConnectionHasNoLabelAndTheDefaultStyle() {
        Connection connection = new Connection(new Card(0, 0), new Card(300, 0));
        assertEquals("", connection.getLabel());
        assertFalse(connection.hasLabel());
        assertEquals(ConnectionStyle.ARROW, connection.getStyle());
        assertEquals(ConnectionStyle.ARROW, ConnectionStyle.DEFAULT);
    }

    @Test
    void labelIsAlwaysOneLineAndNeverNull() {
        Connection connection = new Connection(new Card(0, 0), new Card(300, 0));
        connection.setLabel("leva\na\r\nisso");
        assertEquals("leva a isso", connection.getLabel());
        connection.setLabel(null);
        assertEquals("", connection.getLabel());
        connection.setLabel("   ");
        assertFalse(connection.hasLabel(), "só espaços = sem texto");
        connection.setStyle(null);
        assertEquals(ConnectionStyle.DEFAULT, connection.getStyle());
    }

    @Test
    void stylesDescribeTheirTips() {
        assertTrue(ConnectionStyle.ARROW.hasEndArrow());
        assertFalse(ConnectionStyle.ARROW.hasStartArrow());
        assertFalse(ConnectionStyle.ARROW.isDashed());
        assertTrue(ConnectionStyle.DASHED.isDashed());
        assertTrue(ConnectionStyle.DASHED.hasEndArrow());
        assertTrue(ConnectionStyle.DOUBLE.hasStartArrow());
        assertTrue(ConnectionStyle.DOUBLE.hasEndArrow());
        assertFalse(ConnectionStyle.LINE.hasStartArrow());
        assertFalse(ConnectionStyle.LINE.hasEndArrow());
    }

    @Test
    void styleFromNameFallsBackToTheDefault() {
        assertEquals(ConnectionStyle.DASHED, ConnectionStyle.fromName("DASHED"));
        assertEquals(ConnectionStyle.LINE, ConnectionStyle.fromName(" line "));
        assertEquals(ConnectionStyle.ARROW, ConnectionStyle.fromName(null));
        assertEquals(ConnectionStyle.ARROW, ConnectionStyle.fromName("CURVA"));
    }

    @Test
    void connectWithStyleKeepsTheRules() {
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        Card frame = new Card(0, 300);
        frame.setShape(CardShape.FRAME);
        Card outside = new Card(600, 0);
        Board board = boardWith(a, b, frame);

        Connection line = board.connect(a, b, ConnectionStyle.LINE).orElseThrow();
        assertEquals(ConnectionStyle.LINE, line.getStyle());
        assertFalse(board.connect(a, b, ConnectionStyle.DASHED).isPresent(), "A→B repetida, mesmo em outro estilo");
        assertTrue(board.connect(b, a, ConnectionStyle.DOUBLE).isPresent(), "B→A é outra seta");
        assertFalse(board.connect(a, a, ConnectionStyle.DASHED).isPresent(), "seta para si mesmo");
        assertFalse(board.connect(a, frame, ConnectionStyle.DASHED).isPresent(), "seta para seção");
        assertFalse(board.connect(frame, a, ConnectionStyle.DASHED).isPresent(), "seta saindo de seção");
        assertFalse(board.connect(a, outside, ConnectionStyle.DASHED).isPresent(), "card fora do board");
        assertEquals(2, board.getConnections().size());
    }

    @Test
    void removeConnectionKeepsTheCards() {
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        Board board = boardWith(a, b);
        Connection connection = board.connect(a, b).orElseThrow();

        assertTrue(board.removeConnection(connection));
        assertFalse(board.removeConnection(connection));
        assertTrue(board.getConnections().isEmpty());
        assertEquals(List.of(a, b), board.getCards());
        assertTrue(board.connect(a, b).isPresent(), "depois de excluída, a seta pode ser criada de novo");
    }

    @Test
    void reverseIsDetectedInBothDirections() {
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        Board board = boardWith(a, b);
        Connection ab = board.connect(a, b).orElseThrow();
        assertFalse(board.hasReverse(ab));
        Connection ba = board.connect(b, a).orElseThrow();
        assertTrue(board.hasReverse(ab));
        assertTrue(board.hasReverse(ba));
    }

    @Test
    void duplicatingACardDoesNotCopyItsConnections() {
        Card a = new Card(0, 0);
        Card b = new Card(300, 0);
        Board board = boardWith(a, b);
        Connection connection = board.connect(a, b, ConnectionStyle.DASHED).orElseThrow();
        connection.setLabel("leva a");

        Card copy = a.copy();
        board.addCard(copy);

        assertEquals(1, board.getConnections().size(), "a cópia nasce sem setas, como antes");
        assertTrue(board.getConnections().stream().noneMatch(c -> c.getSource() == copy || c.getTarget() == copy));
    }

    @Test
    void undoAfterDeletingACardBringsBackItsLabelledConnection() {
        Card a = new Card("a", 0, 0);
        Card b = new Card("b", 300, 0);
        Board board = boardWith(a, b);
        Connection connection = board.connect(a, b, ConnectionStyle.DOUBLE).orElseThrow();
        connection.setLabel("depende de");
        BoardSnapshot before = board.snapshot();

        board.removeCard(b);
        board.restore(before);

        Connection back = board.findConnection(connection.getId()).orElseThrow();
        assertEquals("depende de", back.getLabel());
        assertEquals(ConnectionStyle.DOUBLE, back.getStyle());
    }
}
