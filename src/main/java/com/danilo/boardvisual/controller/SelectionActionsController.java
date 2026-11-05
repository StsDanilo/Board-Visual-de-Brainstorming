package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import com.danilo.boardvisual.view.ToolBarView;
import javafx.beans.InvalidationListener;
import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Ações sobre os cards selecionados: cor, formato, tamanho do texto, duplicar e excluir.
 *
 * Cards travados ({@link Card#isLocked()}) não trocam de formato nem são
 * excluídos; cor e tamanho do texto continuam livres. A cópia de um travado
 * nasce destravada.
 *
 * A cor pode vir da barra flutuante da seleção ou da barra de ferramentas
 * (que também define a cor dos próximos cards). Todas as ações já operam
 * sobre a seleção inteira, prontas para a seleção múltipla.
 */
public class SelectionActionsController {

    /** Deslocamento da cópia em relação ao original, em coordenadas do board. */
    private static final double DUPLICATE_OFFSET = 24;

    private final BoardView view;
    private final SelectionToolbarView selectionToolbar;
    private final EditHistory history;

    /** Cards observados, para a barra refletir mudanças de cor e formato vindas de qualquer lugar. */
    private final Set<Card> observedCards = new HashSet<>();
    private final InvalidationListener appearanceListener = obs -> refreshToolbar();
    private Consumer<String> notifier = text -> { };

    public SelectionActionsController(BoardView view, ToolBarView toolBar, SelectionToolbarView selectionToolbar,
                                      EditHistory history) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;

        toolBar.setOnColorPicked(this::applyColor);
        selectionToolbar.getColorButton().setOnColorPicked(this::applyColor);
        selectionToolbar.getShapeButton().setOnShapePicked(this::applyShape);
        selectionToolbar.getFontSizeButton().setOnFontSizePicked(this::applyFontSize);
        selectionToolbar.getDuplicateButton().setOnAction(e -> duplicateSelection());
        selectionToolbar.getDeleteButton().setOnAction(e -> deleteSelection());

        view.getSelection().addListener((SetChangeListener<Card>) change -> observeSelection());
        observeSelection();
    }

    private List<Card> selectedCards() {
        return List.copyOf(view.getSelection());
    }

    private void applyColor(String color) {
        history.perform(() -> selectedCards().forEach(card -> card.setColor(color)));
    }

    private void applyShape(CardShape shape) {
        applyShape(history, selectedCards(), shape);
    }

    /**
     * Troca o formato num passo do desfazer. Fica de fora: card de imagem (é
     * sempre retângulo) e card travado (trocar o formato muda o tamanho dele).
     */
    static void applyShape(EditHistory history, Collection<Card> cards, CardShape shape) {
        history.perform(() -> cards.stream().filter(SelectionActionsController::canChangeShape)
                .forEach(card -> card.changeShape(shape)));
    }

    private static boolean canChangeShape(Card card) {
        return !card.hasImage() && !card.isLocked();
    }

    private void applyFontSize(double size) {
        history.perform(() -> selectedCards().forEach(card -> card.setFontSize(size)));
    }

    public void duplicateSelection() {
        Board board = view.getBoard();
        if (board == null) {
            return;
        }
        List<Card> copies = duplicate(history, board, selectedCards());
        // A seleção passa para as cópias: duplicar de novo gera uma "escada".
        view.setSelection(copies);
    }

    /**
     * Duplica os cards num passo do desfazer. As cópias entram no <b>topo</b> da
     * ordem de empilhamento (como um card novo), deslocadas, e entre si na mesma
     * ordem dos originais (a ordem da seleção não importa). A cópia de um card
     * travado nasce <b>destravada</b>, para poder ser posicionada.
     */
    static List<Card> duplicate(EditHistory history, Board board, Collection<Card> cards) {
        List<Card> originals = inStackOrder(board, cards);
        List<Card> copies = new ArrayList<>();
        history.perform(() -> {
            for (Card card : originals) {
                Card copy = card.copy();
                copy.setX(card.getX() + DUPLICATE_OFFSET);
                copy.setY(card.getY() + DUPLICATE_OFFSET);
                copy.setLocked(false);
                board.addCard(copy);
                copies.add(copy);
            }
        });
        return copies;
    }

    /** Os cards do board que estão em {@code cards}, de baixo para cima na pilha. */
    private static List<Card> inStackOrder(Board board, Collection<Card> cards) {
        Set<Card> wanted = new HashSet<>(cards);
        return board.getCards().stream().filter(wanted::contains).toList();
    }

    /**
     * Exclui os selecionados que não estão travados. Os travados ficam (e
     * continuam selecionados), com um aviso na barra de status: para excluir,
     * destrave antes.
     */
    public void deleteSelection() {
        Board board = view.getBoard();
        if (board == null) {
            return;
        }
        int kept = delete(history, board, selectedCards());
        if (kept > 0) {
            notifier.accept(kept == 1
                    ? "Card travado não é excluído: destrave antes (Ctrl+L ou o cadeado)."
                    : kept + " cards travados não foram excluídos: destrave antes (Ctrl+L ou o cadeado).");
        }
    }

    /**
     * Exclui os cards não travados num passo do desfazer (nenhum passo se todos
     * estão travados).
     *
     * @return quantos ficaram por estarem travados
     */
    static int delete(EditHistory history, Board board, Collection<Card> cards) {
        List<Card> deletable = cards.stream().filter(card -> !card.isLocked()).toList();
        if (!deletable.isEmpty()) {
            history.perform(() -> deletable.forEach(board::removeCard));
        }
        return cards.size() - deletable.size();
    }

    /** Onde aparecem os avisos curtos (na aplicação, a barra de status). */
    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier == null ? text -> { } : notifier;
    }

    private void observeSelection() {
        observedCards.forEach(card -> {
            card.colorProperty().removeListener(appearanceListener);
            card.shapeProperty().removeListener(appearanceListener);
            card.fontSizeProperty().removeListener(appearanceListener);
            card.lockedProperty().removeListener(appearanceListener);
        });
        observedCards.clear();
        observedCards.addAll(view.getSelection());
        observedCards.forEach(card -> {
            card.colorProperty().addListener(appearanceListener);
            card.shapeProperty().addListener(appearanceListener);
            card.fontSizeProperty().addListener(appearanceListener);
            card.lockedProperty().addListener(appearanceListener);
        });
        refreshToolbar();
    }

    /** Os botões mostram cor, formato e tamanho do texto da seleção; se os cards diferem, ficam em estado "misto". */
    private void refreshToolbar() {
        Set<String> colors = view.getSelection().stream()
                .map(card -> card.getColor().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        selectionToolbar.getColorButton().setColor(colors.size() == 1 ? colors.iterator().next() : null);

        Set<CardShape> shapes = view.getSelection().stream().filter(card -> !card.hasImage())
                .map(Card::getShape).collect(Collectors.toSet());
        selectionToolbar.getShapeButton().setShape(shapes.size() == 1 ? shapes.iterator().next() : null);
        // Só imagens selecionadas: não há formato a escolher.
        selectionToolbar.getShapeButton().setVisible(!shapes.isEmpty() || view.getSelection().isEmpty());
        // Todos os que poderiam trocar de formato estão travados: o botão fica desabilitado.
        selectionToolbar.getShapeButton().setDisable(!shapes.isEmpty()
                && view.getSelection().stream().filter(card -> !card.hasImage()).allMatch(Card::isLocked));

        Set<Double> fontSizes = view.getSelection().stream().map(Card::getFontSize).collect(Collectors.toSet());
        selectionToolbar.getFontSizeButton().setFontSize(fontSizes.size() == 1 ? fontSizes.iterator().next() : Double.NaN);
    }
}
