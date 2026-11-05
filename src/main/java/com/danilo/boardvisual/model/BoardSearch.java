package com.danilo.boardvisual.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Busca de texto nos boards (Ctrl+F), sem tela e sem alterar nada (testável).
 *
 * <b>O que é pesquisado</b>, por card, nesta ordem (o primeiro campo que casa dá o trecho):
 * <ol>
 *   <li>o texto do card (numa seção, o título; num card de imagem, a legenda; num card
 *       com board aninhado, o nome do board);</li>
 *   <li>o conteúdo do painel flutuante: o texto detalhado ({@link PanelMode#TEXT}) ou os
 *       itens da lista ({@link PanelMode#LIST});</li>
 * </ol>
 * e o texto das setas ({@link Connection#getLabel()}). Um card (ou uma seta) aparece no máximo
 * uma vez nos resultados, mesmo que case em mais de um campo. Links, cores e nomes de arquivo
 * de imagem não são pesquisados.
 *
 * <b>Como casa:</b> o trecho digitado precisa aparecer inteiro, sem diferenciar maiúsculas
 * nem acentos ("acao" acha "Ação", "CAFÉ" acha "cafe"), e quebras de linha e espaços repetidos
 * valem como um espaço só ("dois  mundos" acha "dois\nmundos"). Busca em branco não acha nada.
 *
 * <b>Ordem dos resultados:</b> board a board; em cada board, primeiro os cards na ordem do
 * board ({@link Board#getCards()}, a de empilhamento) e depois as setas, na ordem delas. Na busca em
 * todos os boards, os boards vêm na ordem do PDF ({@link BoardTree#entries}: o principal e, em
 * profundidade, os filhos de cada card na ordem dos cards).
 */
public final class BoardSearch {

    /** Onde procurar. */
    public enum Scope {
        /** Só o board que está na tela. */
        CURRENT_BOARD,
        /** O board principal e todos os aninhados, em qualquer profundidade. */
        ALL_BOARDS
    }

    /** O campo em que o trecho foi achado. */
    public enum Field {
        /** O texto do card (título da seção, legenda da imagem). */
        TEXT,
        /** O texto detalhado do painel flutuante. */
        DETAIL,
        /** Um item da lista do painel flutuante. */
        LIST_ITEM,
        /** O texto de uma seta. */
        ARROW
    }

    /** Quantos caracteres do original aparecem antes e depois do trecho achado, no resumo. */
    static final int SNIPPET_BEFORE = 24;
    static final int SNIPPET_AFTER = 40;

    /**
     * Um resultado: um card (ou uma seta) que casa com a busca.
     *
     * @param path       o caminho do board principal até o board do resultado
     * @param card       o card que casa; {@code null} se o resultado é uma seta
     * @param connection a seta que casa; {@code null} se o resultado é um card
     * @param field      em qual campo achou
     * @param snippet    o trecho do texto onde casou, numa linha só ("…" nos cortes)
     */
    public record Hit(List<BoardTree.Step> path, Card card, Connection connection, Field field, String snippet) {
        public Hit {
            path = List.copyOf(path);
        }

        /** O board onde o resultado está. */
        public Board board() {
            return path.get(path.size() - 1).board();
        }

        public String boardId() {
            return board().getId();
        }

        /** Id do card, ou {@code null} se o resultado é uma seta. */
        public String cardId() {
            return card == null ? null : card.getId();
        }

        /** Id da seta, ou {@code null} se o resultado é um card. */
        public String connectionId() {
            return connection == null ? null : connection.getId();
        }

        public boolean isArrow() {
            return connection != null;
        }

        /** Caminho em texto: {@code Principal › Projeto B › Custos}. */
        public String pathLabel() {
            return BoardTree.label(path);
        }

        /** Identifica o resultado entre uma busca e a seguinte (o mesmo card ou seta, no mesmo board). */
        public String key() {
            return boardId() + "/" + (card != null ? "c:" + card.getId() : "a:" + connection.getId());
        }
    }

    private BoardSearch() {
    }

    /**
     * Procura o texto.
     *
     * @param root    o board principal (a raiz da árvore)
     * @param current o board que está na tela (usado no escopo {@link Scope#CURRENT_BOARD})
     * @return os resultados na ordem descrita na classe; vazio se a busca é em branco
     */
    public static List<Hit> search(Board root, Board current, String query, Scope scope) {
        List<Hit> hits = new ArrayList<>();
        Folded folded = Folded.of(query);
        if (folded.text().isEmpty() || root == null) {
            return hits;
        }
        if (scope == Scope.ALL_BOARDS) {
            for (BoardTree.Entry entry : BoardTree.entries(root)) {
                searchBoard(entry.path(), folded, hits);
            }
        } else if (current != null) {
            List<BoardTree.Step> path = BoardTree.pathTo(root, current.getId());
            searchBoard(path.isEmpty() ? List.of(new BoardTree.Step(current, null)) : path, folded, hits);
        }
        return hits;
    }

    /** Procura só num board (o caminho do resultado é só ele). */
    public static List<Hit> searchBoard(Board board, String query) {
        List<Hit> hits = new ArrayList<>();
        Folded folded = Folded.of(query);
        if (!folded.text().isEmpty()) {
            searchBoard(List.of(new BoardTree.Step(board, null)), folded, hits);
        }
        return hits;
    }

    private static void searchBoard(List<BoardTree.Step> path, Folded query, List<Hit> hits) {
        Board board = path.get(path.size() - 1).board();
        for (Card card : board.getCards()) {
            Hit hit = hitOf(path, card, query);
            if (hit != null) {
                hits.add(hit);
            }
        }
        for (Connection connection : board.getConnections()) {
            String snippet = snippetOf(connection.getLabel(), query);
            if (snippet != null) {
                hits.add(new Hit(path, null, connection, Field.ARROW, snippet));
            }
        }
    }

    private static Hit hitOf(List<BoardTree.Step> path, Card card, Folded query) {
        String snippet = snippetOf(card.getText(), query);
        if (snippet != null) {
            return new Hit(path, card, null, Field.TEXT, snippet);
        }
        if (card.getPanelMode() == PanelMode.TEXT) {
            snippet = snippetOf(card.getDetailText(), query);
            if (snippet != null) {
                return new Hit(path, card, null, Field.DETAIL, snippet);
            }
        } else if (card.getPanelMode() == PanelMode.LIST) {
            for (String item : card.getListItemTexts()) {
                snippet = snippetOf(item, query);
                if (snippet != null) {
                    return new Hit(path, card, null, Field.LIST_ITEM, snippet);
                }
            }
        }
        return null;
    }

    /** O trecho (com um pouco de contexto) onde o texto casa com a busca, ou {@code null} se não casa. */
    private static String snippetOf(String text, Folded query) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Folded folded = Folded.of(text);
        int at = folded.text().indexOf(query.text());
        if (at < 0) {
            return null;
        }
        int from = folded.originalIndex(at);
        int to = folded.originalEnd(at + query.text().length() - 1);
        int start = Math.max(0, from - SNIPPET_BEFORE);
        int end = Math.min(text.length(), to + SNIPPET_AFTER);
        String excerpt = text.substring(start, end).replaceAll("\\s+", " ").strip();
        return (start > 0 ? "…" : "") + excerpt + (end < text.length() ? "…" : "");
    }

    /** Se o texto contém a busca (sem diferenciar maiúsculas nem acentos). Busca em branco não casa. */
    public static boolean matches(String text, String query) {
        Folded folded = Folded.of(query);
        return !folded.text().isEmpty() && text != null && Folded.of(text).text().contains(folded.text());
    }

    /** O texto na forma comparada: minúsculas, sem acentos e com os espaços colapsados e aparados. */
    public static String normalize(String text) {
        return Folded.of(text).text();
    }

    /**
     * O texto "dobrado" para comparar (minúsculas, sem acentos, espaços colapsados) com o
     * mapa de cada caractere dobrado para o lugar dele no texto original, para o resumo
     * poder cortar o original.
     */
    private record Folded(String text, int[] originalStart, int[] originalEnd) {

        static Folded of(String original) {
            StringBuilder out = new StringBuilder();
            List<Integer> starts = new ArrayList<>();
            List<Integer> ends = new ArrayList<>();
            boolean pendingSpace = false;
            int pendingSpaceAt = 0;
            String source = original == null ? "" : original;
            for (int i = 0; i < source.length(); ) {
                int codePoint = source.codePointAt(i);
                int length = Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    if (!pendingSpace) {
                        pendingSpace = true;
                        pendingSpaceAt = i;
                    }
                } else {
                    String piece = fold(new String(Character.toChars(codePoint)));
                    if (!piece.isEmpty()) {
                        if (pendingSpace && out.length() > 0) {
                            out.append(' ');
                            starts.add(pendingSpaceAt);
                            ends.add(pendingSpaceAt + 1);
                        }
                        pendingSpace = false;
                        for (int k = 0; k < piece.length(); k++) {
                            out.append(piece.charAt(k));
                            starts.add(i);
                            ends.add(i + length);
                        }
                    }
                }
                i += length;
            }
            return new Folded(out.toString(), starts.stream().mapToInt(Integer::intValue).toArray(),
                    ends.stream().mapToInt(Integer::intValue).toArray());
        }

        /** Posição, no original, do começo do caractere dobrado de índice {@code folded}. */
        int originalIndex(int folded) {
            return originalStart[folded];
        }

        /** Posição, no original, logo depois do caractere dobrado de índice {@code folded}. */
        int originalEnd(int folded) {
            return originalEnd[folded];
        }

        private static String fold(String piece) {
            return Normalizer.normalize(piece, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT);
        }
    }
}
