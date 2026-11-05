package com.danilo.boardvisual.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Consultas na árvore de boards (o principal, os filhos dentro dos cards, os
 * filhos dos filhos...). Java puro, sem tela: serve à navegação (achar o
 * caminho de um board pelo id e reconstruir a pilha de níveis), à lista de
 * destinos do "link para outro board", ao marcador quebrado e ao PDF.
 *
 * Os boards são achados pelo <b>id</b> ({@link Board#getId()}), que é único na
 * árvore: {@link Board#deepCopy} dá ids novos a todos os boards copiados, e o
 * {@code BoardStorage} corrige ids repetidos de saves antigos ao abrir.
 *
 * A ordem é sempre a mesma do PDF: o board, depois os filhos na ordem dos
 * cards (cada um com os seus), em profundidade.
 */
public final class BoardTree {

    /** Separador do caminho, igual ao do topo da tela: {@code Principal › Projeto B › Custos}. */
    public static final String SEPARATOR = " › ";

    /** Um nível de um caminho: o board e o card que o contém ({@code null} no principal). */
    public record Step(Board board, Card card) {
        /** Nome exibido do board (regra em {@link BoardNames}). */
        public String name() {
            return card == null ? BoardNames.ofRoot(board) : BoardNames.ofChild(card);
        }
    }

    /** Um board da árvore com o caminho do principal até ele (para listas de escolha). */
    public record Entry(List<Step> path) {
        public Entry {
            path = List.copyOf(path);
        }

        public Board board() {
            return path.get(path.size() - 1).board();
        }

        /** 0 = board principal, 1 = filho dele... */
        public int depth() {
            return path.size() - 1;
        }

        public String name() {
            return path.get(path.size() - 1).name();
        }

        /** Caminho completo em texto: {@code Principal › Projeto B › Custos}. */
        public String label() {
            return BoardTree.label(path);
        }
    }

    private BoardTree() {
    }

    /**
     * Caminho do board principal até o board com esse id (os dois inclusos),
     * ou lista vazia se ele não está na árvore.
     */
    public static List<Step> pathTo(Board root, String boardId) {
        List<Step> path = new ArrayList<>();
        if (root != null && boardId != null && search(new Step(root, null), boardId, path)) {
            return List.copyOf(path);
        }
        return List.of();
    }

    private static boolean search(Step step, String boardId, List<Step> path) {
        path.add(step);
        if (step.board().getId().equals(boardId)) {
            return true;
        }
        for (Card card : step.board().getCards()) {
            if (card.hasChildBoard() && search(new Step(card.getChildBoard(), card), boardId, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /** O board com esse id, se ele está na árvore. */
    public static Optional<Board> find(Board root, String boardId) {
        List<Step> path = pathTo(root, boardId);
        return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1).board());
    }

    public static boolean contains(Board root, String boardId) {
        return !pathTo(root, boardId).isEmpty();
    }

    /** Ids de todos os boards da árvore (para conferir muitos links de uma vez). */
    public static Set<String> ids(Board root) {
        Set<String> ids = new HashSet<>();
        if (root != null) {
            collectIds(root, ids);
        }
        return ids;
    }

    private static void collectIds(Board board, Set<String> ids) {
        ids.add(board.getId());
        for (Card card : board.getCards()) {
            if (card.hasChildBoard()) {
                collectIds(card.getChildBoard(), ids);
            }
        }
    }

    /** Todos os boards da árvore, com o caminho de cada um, na ordem do PDF. */
    public static List<Entry> entries(Board root) {
        List<Entry> entries = new ArrayList<>();
        if (root != null) {
            List<Step> path = new ArrayList<>();
            collectEntries(new Step(root, null), path, entries);
        }
        return entries;
    }

    private static void collectEntries(Step step, List<Step> path, List<Entry> entries) {
        path.add(step);
        entries.add(new Entry(path));
        for (Card card : step.board().getCards()) {
            if (card.hasChildBoard()) {
                collectEntries(new Step(card.getChildBoard(), card), path, entries);
            }
        }
        path.remove(path.size() - 1);
    }

    /** Caminho em texto: {@code Principal › Projeto B › Custos}. */
    public static String label(List<Step> path) {
        return String.join(SEPARATOR, path.stream().map(Step::name).toList());
    }
}
