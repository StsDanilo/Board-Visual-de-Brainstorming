package com.danilo.boardvisual.model;

import java.util.List;

/**
 * A lista de resultados da busca e qual deles é o atual (o que o Enter e o Shift+Enter
 * percorrem), sem tela (testável).
 *
 * O resultado atual é o que aparece mais forte na tela. {@link #next()} e {@link #previous()}
 * dão a volta (depois do último vem o primeiro). Quando a busca é refeita
 * ({@link #replace}), o atual continua o mesmo se ele ainda está na lista (digitar mais uma
 * letra não faz o foco pular); senão, passa ao primeiro.
 */
public final class SearchResults {

    private List<BoardSearch.Hit> hits = List.of();
    private int index = -1;

    /**
     * Troca os resultados. O atual continua o mesmo (pela {@link BoardSearch.Hit#key}) se ainda
     * existe; senão passa ao primeiro resultado do board {@code preferredBoard} (o que está na
     * tela: ao digitar numa busca em todos os boards, o foco começa no que se está vendo) e, se
     * não há nenhum lá, ao primeiro de todos.
     */
    public void replace(List<BoardSearch.Hit> newHits, Board preferredBoard) {
        String currentKey = current() == null ? null : current().key();
        hits = List.copyOf(newHits);
        index = hits.isEmpty() ? -1 : 0;
        if (preferredBoard != null) {
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).board() == preferredBoard) {
                    index = i;
                    break;
                }
            }
        }
        if (currentKey != null) {
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).key().equals(currentKey)) {
                    index = i;
                    break;
                }
            }
        }
    }

    /** {@link #replace(List, Board)} sem preferência de board. */
    public void replace(List<BoardSearch.Hit> newHits) {
        replace(newHits, null);
    }

    public List<BoardSearch.Hit> hits() {
        return hits;
    }

    public int size() {
        return hits.size();
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    /** Posição do atual (começa em 0), ou -1 se não há resultados. */
    public int index() {
        return index;
    }

    /** O resultado atual, ou {@code null} se não há resultados. */
    public BoardSearch.Hit current() {
        return index < 0 ? null : hits.get(index);
    }

    /** Vai ao próximo (do último, ao primeiro) e o devolve; {@code null} se não há resultados. */
    public BoardSearch.Hit next() {
        if (hits.isEmpty()) {
            return null;
        }
        index = (index + 1) % hits.size();
        return hits.get(index);
    }

    /** Vai ao anterior (do primeiro, ao último) e o devolve; {@code null} se não há resultados. */
    public BoardSearch.Hit previous() {
        if (hits.isEmpty()) {
            return null;
        }
        index = (index - 1 + hits.size()) % hits.size();
        return hits.get(index);
    }

    /** Escolhe o resultado de posição {@code position}; false se não existe (nada muda). */
    public boolean select(int position) {
        if (position < 0 || position >= hits.size()) {
            return false;
        }
        index = position;
        return true;
    }

    /** O contador exibido: {@code "3 de 12"}; vazio se não há resultados. */
    public String counterText() {
        return hits.isEmpty() ? "" : (index + 1) + " de " + hits.size();
    }

    /** Os resultados que estão no board informado (para destacá-los quando ele está na tela). */
    public List<BoardSearch.Hit> hitsIn(Board board) {
        return hits.stream().filter(hit -> hit.board() == board).toList();
    }

    /** Se o atual está no board informado. */
    public boolean currentIsIn(Board board) {
        return current() != null && current().board() == board;
    }
}
