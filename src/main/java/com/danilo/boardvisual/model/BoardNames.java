package com.danilo.boardvisual.model;

/**
 * Nome exibido de cada board (no caminho do topo da tela e no PDF): o do
 * arquivo no board principal; a primeira linha do texto do card nos filhos.
 */
public final class BoardNames {

    public static final String UNTITLED = "Sem título";

    private BoardNames() {
    }

    public static String ofRoot(Board board) {
        return orUntitled(board.getName());
    }

    /** Nome do board filho do card. */
    public static String ofChild(Card card) {
        return orUntitled(firstLine(card.getText()));
    }

    /** Primeira linha não vazia do texto, sem espaços nas pontas. */
    public static String firstLine(String text) {
        String stripped = text == null ? "" : text.strip();
        int lineBreak = stripped.indexOf('\n');
        return lineBreak < 0 ? stripped : stripped.substring(0, lineBreak).strip();
    }

    private static String orUntitled(String name) {
        return name == null || name.isBlank() ? UNTITLED : name;
    }
}
