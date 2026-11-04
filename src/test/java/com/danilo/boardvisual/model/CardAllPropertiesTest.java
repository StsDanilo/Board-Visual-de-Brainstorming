package com.danilo.boardvisual.model;

import com.danilo.boardvisual.persistence.BoardStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rede de segurança para "adicionei uma propriedade ao Card e esqueci um lugar"
 * (checklist 5.1 do DOCUMENTACAO.md).
 *
 * Um card com TODAS as propriedades diferentes do padrão passa por copiar,
 * foto/restauração (desfazer) e salvar/abrir; o que não vier igual denuncia o
 * lugar esquecido. Os valores são lidos por reflexão de todo getter público do
 * Card ({@code get*}, {@code is*}, {@code has*}), então uma propriedade nova
 * entra na comparação sem mexer aqui. A única obrigação de quem cria uma
 * propriedade é preenchê-la em {@link #fullCard()}: o primeiro teste falha
 * enquanto isso não for feito.
 */
class CardAllPropertiesTest {

    private static final String CARD_ID = "card-completo";
    private static final String ID_KEY = "Id";

    /** Um card com cada propriedade diferente do padrão. Ao criar uma propriedade nova, preencha-a aqui. */
    static Card fullCard() {
        Card card = new Card(CARD_ID, 123.5, -45.25);
        card.setShape(CardShape.DIAMOND);
        card.setWidth(333);
        card.setHeight(222);
        card.setBaseWidth(310);
        card.setBaseHeight(180);
        card.setFontSize(21);
        card.setText("Texto\ncom acentuação");
        card.setColor("#CFE2FF");
        card.setPanelMode(PanelMode.LIST);
        card.setDetailText("detalhe longo");
        card.setListItemTexts(List.of("um", "dois"));

        Board child = new Board("board-filho", "");
        Card inside = new Card("dentro-1", 5, 6);
        inside.setText("dentro");
        Card other = new Card("dentro-2", 300, 6);
        child.addCard(inside);
        child.addCard(other);
        child.connect(inside, other);
        card.setChildBoard(child);
        return card;
    }

    /** Todos os valores que o card expõe, por nome, em forma comparável. */
    private static Map<String, Object> describe(Card card) {
        Map<String, Object> values = new TreeMap<>();
        for (Method method : Card.class.getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
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
                values.put(key, comparable(method.invoke(card)));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError("Não foi possível ler " + name + " do Card", e);
            }
        }
        return values;
    }

    /** Boards comparam pelo conteúdo (foto); listas, item a item. */
    private static Object comparable(Object value) {
        if (value instanceof Board board) {
            return board.snapshot();
        }
        if (value instanceof ListItem item) {
            return item.getText();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(CardAllPropertiesTest::comparable).toList();
        }
        return value;
    }

    private static Map<String, Object> describeWithoutId(Card card) {
        Map<String, Object> values = describe(card);
        values.remove(ID_KEY);
        return values;
    }

    @Test
    void fullCardFillsEveryPropertyWithANonDefaultValue() {
        Map<String, Object> full = describe(fullCard());
        Map<String, Object> defaults = describe(new Card(CARD_ID, 0, 0));

        assertEquals(defaults.keySet(), full.keySet());
        Set<String> stillDefault = new TreeSet<>();
        full.forEach((key, value) -> {
            if (Objects.equals(value, defaults.get(key))) {
                stillDefault.add(key);
            }
        });
        stillDefault.remove(ID_KEY);
        assertTrue(stillDefault.isEmpty(),
                "Preencha com um valor diferente do padrão, em fullCard(), a(s) propriedade(s): " + stillDefault);
    }

    @Test
    void copyKeepsEveryProperty() {
        Card original = fullCard();

        Card copy = original.copy();

        assertNotEquals(original.getId(), copy.getId(), "a cópia tem id novo");
        assertEquals(describeWithoutId(original), describeWithoutId(copy));
    }

    @Test
    void snapshotAndRestoreKeepEveryPropertyInANewCard() {
        Board board = new Board("origem");
        Card original = fullCard();
        board.addCard(original);
        BoardSnapshot snapshot = board.snapshot();

        Board restored = new Board("destino");
        restored.restore(snapshot);

        Card card = restored.findCard(CARD_ID).orElseThrow();
        assertEquals(describe(original), describe(card));
        assertEquals(original.getChildBoard().getId(), card.getChildBoard().getId());
    }

    @Test
    void restoreKeepsEveryPropertyInAnExistingCard() {
        Board board = new Board("origem");
        Card card = fullCard();
        board.addCard(card);
        Map<String, Object> before = describe(card);
        BoardSnapshot snapshot = board.snapshot();

        // Estraga o card com os valores padrão e volta à foto.
        Card blank = new Card(CARD_ID, 0, 0);
        Board other = new Board("outro");
        other.addCard(blank);
        other.restore(snapshot);

        assertEquals(before, describe(blank));
    }

    @Test
    void saveAndLoadKeepEveryProperty(@TempDir Path dir) throws IOException {
        Board board = new Board("completo");
        Card original = fullCard();
        board.addCard(original);
        BoardStorage storage = new BoardStorage();
        Path folder = dir.resolve("completo");

        storage.save(board, folder);
        Board loaded = storage.load(folder).board();

        Card card = loaded.findCard(CARD_ID).orElseThrow();
        assertEquals(describe(original), describe(card));
        assertEquals(original.getChildBoard().getId(), card.getChildBoard().getId());
    }
}
