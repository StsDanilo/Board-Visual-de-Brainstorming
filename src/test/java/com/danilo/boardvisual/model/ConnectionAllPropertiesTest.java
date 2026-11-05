package com.danilo.boardvisual.model;

import com.danilo.boardvisual.persistence.BoardStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O {@link CardAllPropertiesTest} das setas (checklist 5.1 adaptado a
 * conexões): uma seta com TODAS as propriedades mutáveis fora do padrão passa
 * por foto/restauração (desfazer), cópia do board e salvar/abrir. Os valores
 * são lidos por reflexão dos getters públicos da {@link Connection}; uma
 * propriedade nova entra na comparação sozinha, basta preenchê-la em
 * {@link #fullConnection}.
 */
class ConnectionAllPropertiesTest {

    private static final String CONNECTION_ID = "seta-completa";
    /** Fixos (identidade e pontas): não são "propriedades fora do padrão". */
    private static final Set<String> FIXED = Set.of("Id", "Source", "Target");

    private static Board boardWithFullConnection() {
        Board board = new Board("origem");
        Card a = new Card("a", 0, 0);
        Card b = new Card("b", 400, 0);
        board.addCard(a);
        board.addCard(b);
        board.addConnection(fullConnection(a, b));
        return board;
    }

    /** Uma seta com cada propriedade diferente do padrão. Ao criar uma propriedade nova, preencha-a aqui. */
    static Connection fullConnection(Card source, Card target) {
        Connection connection = new Connection(CONNECTION_ID, source, target);
        connection.setLabel("depende de, com acentuação");
        connection.setStyle(ConnectionStyle.DASHED);
        return connection;
    }

    /** Valores que a seta expõe, por nome; cards pelo id. */
    private static Map<String, Object> describe(Connection connection) {
        Map<String, Object> values = new TreeMap<>();
        for (Method method : Connection.class.getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String key;
            if (name.startsWith("get") && name.length() > 3) {
                key = name.substring(3);
            } else if (name.startsWith("is") && name.length() > 2) {
                key = name.substring(2);
            } else if (name.startsWith("has") && name.length() > 3) {
                key = name;
            } else {
                continue;
            }
            try {
                Object value = method.invoke(connection);
                values.put(key, value instanceof Card card ? card.getId() : value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError("Não foi possível ler " + name + " da Connection", e);
            }
        }
        return values;
    }

    @Test
    void fullConnectionFillsEveryPropertyWithANonDefaultValue() {
        Card a = new Card("a", 0, 0);
        Card b = new Card("b", 400, 0);
        Map<String, Object> full = describe(fullConnection(a, b));
        Map<String, Object> defaults = describe(new Connection(CONNECTION_ID, a, b));

        assertEquals(defaults.keySet(), full.keySet());
        Set<String> stillDefault = new TreeSet<>();
        full.forEach((key, value) -> {
            if (Objects.equals(value, defaults.get(key))) {
                stillDefault.add(key);
            }
        });
        stillDefault.removeAll(FIXED);
        assertTrue(stillDefault.isEmpty(),
                "Preencha com um valor diferente do padrão, em fullConnection(), a(s) propriedade(s): " + stillDefault);
    }

    @Test
    void snapshotAndRestoreKeepEveryPropertyInANewBoard() {
        Board board = boardWithFullConnection();
        Connection original = board.findConnection(CONNECTION_ID).orElseThrow();

        Board restored = new Board("destino");
        restored.restore(board.snapshot());

        assertEquals(describe(original), describe(restored.findConnection(CONNECTION_ID).orElseThrow()));
    }

    @Test
    void restoreKeepsEveryPropertyInAnExistingConnection() {
        Board board = boardWithFullConnection();
        Connection connection = board.findConnection(CONNECTION_ID).orElseThrow();
        Map<String, Object> before = describe(connection);
        BoardSnapshot snapshot = board.snapshot();

        // Estraga a seta com os valores padrão e volta à foto: o mesmo objeto é atualizado.
        connection.setLabel("");
        connection.setStyle(ConnectionStyle.DEFAULT);
        board.restore(snapshot);

        assertTrue(board.getConnections().contains(connection), "a seta é atualizada no lugar, não recriada");
        assertEquals(before, describe(connection));
    }

    @Test
    void snapshotsDifferWhenOnlyLabelOrStyleChange() {
        Board board = boardWithFullConnection();
        Connection connection = board.findConnection(CONNECTION_ID).orElseThrow();
        BoardSnapshot original = board.snapshot();

        connection.setLabel("outro texto");
        BoardSnapshot labelChanged = board.snapshot();
        connection.setLabel("depende de, com acentuação");
        connection.setStyle(ConnectionStyle.LINE);
        BoardSnapshot styleChanged = board.snapshot();

        assertTrue(!original.equals(labelChanged), "texto entra na foto (desfazer)");
        assertTrue(!original.equals(styleChanged), "estilo entra na foto (desfazer)");
    }

    @Test
    void deepCopyKeepsEveryProperty() {
        Board board = boardWithFullConnection();
        Connection original = board.findConnection(CONNECTION_ID).orElseThrow();

        Board copy = board.deepCopy();

        assertEquals(describe(original), describe(copy.findConnection(CONNECTION_ID).orElseThrow()));
    }

    /**
     * Copiar e colar cards (3.25) leva as setas entre eles: a seta colada tem id e pontas
     * novos, e todas as outras propriedades iguais.
     */
    @Test
    void copyAndPasteKeepEveryPropertyExceptIdAndEnds() {
        Board board = boardWithFullConnection();
        Connection original = board.findConnection(CONNECTION_ID).orElseThrow();

        CardClip.Pasted pasted = CardClip.of(board, board.getCards()).orElseThrow().pasteCenteredAt(0, 0);

        assertEquals(1, pasted.connections().size());
        Connection copy = pasted.connections().get(0);
        assertTrue(!original.getId().equals(copy.getId()), "a seta colada tem id novo");
        Map<String, Object> expected = describe(original);
        Map<String, Object> actual = describe(copy);
        FIXED.forEach(key -> {
            expected.remove(key);
            actual.remove(key);
        });
        assertEquals(expected, actual);
    }

    @Test
    void saveAndLoadKeepEveryProperty(@TempDir Path dir) throws IOException {
        Board board = boardWithFullConnection();
        Connection original = board.findConnection(CONNECTION_ID).orElseThrow();
        BoardStorage storage = new BoardStorage();
        Path folder = dir.resolve("seta");

        storage.save(board, folder);
        Board loaded = storage.load(folder).board();

        assertEquals(describe(original), describe(loaded.findConnection(CONNECTION_ID).orElseThrow()));
    }
}
