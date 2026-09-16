package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import com.danilo.boardvisual.view.ToolBarView;
import javafx.beans.InvalidationListener;
import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ações sobre os cards selecionados: cor, duplicar e excluir.
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

    /** Cards cuja cor está sendo observada, para a bolinha da barra refletir qualquer mudança. */
    private final Set<Card> observedCards = new HashSet<>();
    private final InvalidationListener colorListener = obs -> refreshColor();

    public SelectionActionsController(BoardView view, ToolBarView toolBar, SelectionToolbarView selectionToolbar) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;

        toolBar.setOnColorPicked(this::applyColor);
        selectionToolbar.getColorButton().setOnColorPicked(this::applyColor);
        selectionToolbar.getDuplicateButton().setOnAction(e -> duplicateSelection());
        selectionToolbar.getDeleteButton().setOnAction(e -> deleteSelection());

        view.getSelection().addListener((SetChangeListener<Card>) change -> observeSelection());
        observeSelection();
    }

    private List<Card> selectedCards() {
        return List.copyOf(view.getSelection());
    }

    private void applyColor(String color) {
        selectedCards().forEach(card -> card.setColor(color));
    }

    private void duplicateSelection() {
        Board board = view.getBoard();
        List<Card> copies = new ArrayList<>();
        for (Card card : selectedCards()) {
            Card copy = card.copy();
            copy.setX(card.getX() + DUPLICATE_OFFSET);
            copy.setY(card.getY() + DUPLICATE_OFFSET);
            board.addCard(copy);
            copies.add(copy);
        }
        // A seleção passa para as cópias: duplicar de novo gera uma "escada".
        view.setSelection(copies);
    }

    private void deleteSelection() {
        Board board = view.getBoard();
        selectedCards().forEach(board::removeCard);
    }

    private void observeSelection() {
        observedCards.forEach(card -> card.colorProperty().removeListener(colorListener));
        observedCards.clear();
        observedCards.addAll(view.getSelection());
        observedCards.forEach(card -> card.colorProperty().addListener(colorListener));
        refreshColor();
    }

    /** A bolinha mostra a cor da seleção; com cores diferentes, fica em estado "misto". */
    private void refreshColor() {
        Set<String> colors = view.getSelection().stream()
                .map(card -> card.getColor().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        selectionToolbar.getColorButton().setColor(colors.size() == 1 ? colors.iterator().next() : null);
    }
}
