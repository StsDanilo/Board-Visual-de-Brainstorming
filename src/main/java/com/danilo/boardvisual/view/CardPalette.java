package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;

import java.util.List;
import java.util.Locale;

/**
 * Paleta de cores oferecida para os cards.
 *
 * Tons claros de propósito: o texto do card continua legível em qualquer um.
 * O modelo guarda só o hex, então mudar ou ampliar a paleta não afeta
 * arquivos já salvos.
 */
public final class CardPalette {

    public record Entry(String name, String hex) {
    }

    public static final List<Entry> COLORS = List.of(
            new Entry("Neutro", Card.DEFAULT_COLOR),
            new Entry("Amarelo", "#FFF1A8"),
            new Entry("Laranja", "#FFD9B3"),
            new Entry("Rosa", "#FFD1E0"),
            new Entry("Roxo", "#E2D6FF"),
            new Entry("Azul", "#CFE2FF"),
            new Entry("Verde", "#CFF0D2"),
            new Entry("Cinza", "#E4E4DF"));

    private CardPalette() {
    }

    /** Forma canônica de um hex para comparação ("#fff1a8" == "#FFF1A8"). */
    static String normalize(String hex) {
        return hex == null ? null : hex.trim().toUpperCase(Locale.ROOT);
    }
}
