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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ações sobre os cards selecionados: cor, formato, duplicar e excluir.
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

    public SelectionActionsController(BoardView view, ToolBarView toolBar, SelectionToolbarView selectionToolbar,
                                      EditHistory history) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;

        toolBar.setOnColorPicked(this::applyColor);
        selectionToolbar.getColorButton().setOnColorPicked(this::applyColor);
        selectionToolbar.getShapeButton().setOnShapePicked(this::applyShape);
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
        history.perform(() -> selectedCards().forEach(card -> card.changeShape(shape)));
    }

    public void duplicateSelection() {
        Board board = view.getBoard();
        if (board == null) {
            return;
        }
        List<Card> copies = new ArrayList<>();
        history.perform(() -> {
            for (Card card : selectedCards()) {
                Card copy = card.copy();
                copy.setX(card.getX() + DUPLICATE_OFFSET);
                copy.setY(card.getY() + DUPLICATE_OFFSET);
                board.addCard(copy);
                copies.add(copy);
            }
        });
        // A seleção passa para as cópias: duplicar de novo gera uma "escada".
        view.setSelection(copies);
    }

    public void deleteSelection() {
        Board board = view.getBoard();
        if (board != null) {
            history.perform(() -> selectedCards().forEach(board::removeCard));
        }
    }

    private void observeSelection() {
        observedCards.forEach(card -> {
            card.colorProperty().removeListener(appearanceListener);
            card.shapeProperty().removeListener(appearanceListener);
        });
        observedCards.clear();
        observedCards.addAll(view.getSelection());
        observedCards.forEach(card -> {
            card.colorProperty().addListener(appearanceListener);
            card.shapeProperty().addListener(appearanceListener);
        });
        refreshToolbar();
    }

    /** Os botões mostram cor e formato da seleção; se os cards diferem, ficam em estado "misto". */
    private void refreshToolbar() {
        Set<String> colors = view.getSelection().stream()
                .map(card -> card.getColor().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        selectionToolbar.getColorButton().setColor(colors.size() == 1 ? colors.iterator().next() : null);

        Set<CardShape> shapes = view.getSelection().stream().map(Card::getShape).collect(Collectors.toSet());
        selectionToolbar.getShapeButton().setShape(shapes.size() == 1 ? shapes.iterator().next() : null);
    }
}
