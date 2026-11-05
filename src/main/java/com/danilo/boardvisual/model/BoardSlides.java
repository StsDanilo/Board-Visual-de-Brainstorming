package com.danilo.boardvisual.model;

import java.util.List;
import java.util.Optional;

/**
 * A ordem dos boards como "slides" da apresentação (F5): a mesma das páginas do
 * PDF ({@link BoardTree#entries}), isto é, o board principal, depois, para cada
 * card com board aninhado na ordem dos cards, esse board e os filhos dele,
 * recursivamente. Java puro, sem tela: quem muda de board de verdade é o
 * {@code PresentationController}.
 *
 * Os boards são comparados pelo <b>id</b> ({@link Board#getId()}), que é único na
 * árvore. Se o board informado não está na árvore (ex.: o card que o continha foi
 * excluído), não há próximo nem anterior.
 */
public final class BoardSlides {

    private BoardSlides() {
    }

    /** Todos os boards na ordem da apresentação (e do PDF). */
    public static List<Board> order(Board root) {
        return BoardTree.entries(root).stream().map(BoardTree.Entry::board).toList();
    }

    /** Posição do board na ordem, começando em 0; -1 se ele não está na árvore. */
    public static int indexOf(Board root, Board current) {
        if (current == null) {
            return -1;
        }
        List<Board> order = order(root);
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getId().equals(current.getId())) {
                return i;
            }
        }
        return -1;
    }

    /** Quantos boards a apresentação tem. */
    public static int count(Board root) {
        return order(root).size();
    }

    /** O board seguinte na ordem, ou vazio se {@code current} é o último (ou não está na árvore). */
    public static Optional<Board> next(Board root, Board current) {
        return neighbor(root, current, +1);
    }

    /** O board anterior na ordem, ou vazio se {@code current} é o primeiro (ou não está na árvore). */
    public static Optional<Board> previous(Board root, Board current) {
        return neighbor(root, current, -1);
    }

    private static Optional<Board> neighbor(Board root, Board current, int step) {
        List<Board> order = order(root);
        int index = indexOf(root, current);
        int target = index + step;
        if (index < 0 || target < 0 || target >= order.size()) {
            return Optional.empty();
        }
        return Optional.of(order.get(target));
    }
}
