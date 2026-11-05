package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.ConnectionStyle;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static com.danilo.boardvisual.model.ConnectionStyle.ARROW;
import static com.danilo.boardvisual.model.ConnectionStyle.DASHED;
import static com.danilo.boardvisual.model.ConnectionStyle.DOUBLE;
import static com.danilo.boardvisual.model.ConnectionStyle.LINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Troca de posição dos estilos de seta na barra de ferramentas (lógica pura). */
class ConnectionStyleOrderTest {

    @Test
    void startsWithThePlainArrowInTheMainPlace() {
        ConnectionStyleOrder order = new ConnectionStyleOrder();
        assertEquals(ARROW, order.primary());
        assertEquals(List.of(DASHED, DOUBLE, LINE), order.extras());
    }

    @Test
    void choosingAnExtraSwapsItWithTheMainOne() {
        ConnectionStyleOrder order = new ConnectionStyleOrder();

        assertTrue(order.choose(DOUBLE));
        assertEquals(DOUBLE, order.primary());
        // O antigo principal vai para o lugar que o escolhido ocupava (o 2º extra).
        assertEquals(List.of(DASHED, ARROW, LINE), order.extras());

        assertTrue(order.choose(LINE));
        assertEquals(List.of(LINE, DASHED, ARROW, DOUBLE), order.order());

        assertTrue(order.choose(ARROW));
        assertEquals(List.of(ARROW, DASHED, LINE, DOUBLE), order.order());
    }

    @Test
    void choosingTheMainOneChangesNothing() {
        ConnectionStyleOrder order = new ConnectionStyleOrder();
        order.choose(DASHED);
        List<ConnectionStyle> before = order.order();

        assertFalse(order.choose(DASHED));
        assertEquals(before, order.order());
    }

    @Test
    void theStylesAreAlwaysTheSameOnlyTheirPlacesChange() {
        ConnectionStyleOrder order = new ConnectionStyleOrder();
        for (ConnectionStyle style : List.of(LINE, DOUBLE, ARROW, DASHED, DASHED, LINE)) {
            order.choose(style);
            assertEquals(ConnectionStyle.values().length, order.order().size());
            assertEquals(EnumSet.allOf(ConnectionStyle.class), EnumSet.copyOf(order.order()));
            assertEquals(order.primary(), order.order().get(0));
        }
    }
}
