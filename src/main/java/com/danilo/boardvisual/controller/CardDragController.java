package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Selecionar e arrastar cards (ferramenta Selecionar).
 *
 * - Clique: seleciona só o card. Shift+clique: inclui/retira da seleção.
 * - Arrastar um card selecionado move todos os selecionados juntos.
 *
 * Só altera x/y dos cards no modelo; views e setas acompanham por binding.
 * Trabalha em coordenadas de mundo, então funciona em qualquer nível de zoom.
 * Cada arrasto vira um único passo no histórico de desfazer.
 */
public class CardDragController {

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final EditHistory history;

    public CardDragController(BoardView view, ObjectProperty<Tool> activeTool, EditHistory history) {
        this.view = view;
        this.activeTool = activeTool;
        this.history = history;
    }

    public void attach(CardView cardView) {
        Card card = cardView.getCard();
        DragState state = new DragState();

        // Qualquer clique no card (inclusive no texto) o traz para frente e ajusta a seleção.
        cardView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            cardView.toFront();
            state.selectOnlyOnRelease = false;
            if (activeTool.get() != Tool.SELECT || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Set<Card> selection = new LinkedHashSet<>(view.getSelection());
            if (e.isShiftDown()) {
                if (!selection.remove(card)) {
                    selection.add(card);
                }
                view.setSelection(selection);
            } else if (!selection.contains(card)) {
                view.select(card);
            } else if (selection.size() > 1) {
                // Pode ser o início de um arrasto do grupo; se for só um clique, seleciona só este.
                state.selectOnlyOnRelease = true;
            }
        });
        cardView.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (state.selectOnlyOnRelease && e.isStillSincePress()) {
                view.select(card);
            }
            state.selectOnlyOnRelease = false;
        });

        cardView.getBody().addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (activeTool.get() != Tool.SELECT
                    || e.getButton() != MouseButton.PRIMARY
                    || !view.getSelection().contains(card)
                    || cardView.isTextNode(e.getPickResult().getIntersectedNode())) {
                return;
            }
            view.requestFocus(); // sai da edição de texto, liberando os atalhos de teclado
            state.start = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            state.startPositions.clear();
            view.getSelection().forEach(c -> state.startPositions.put(c, new Point2D(c.getX(), c.getY())));
            state.before = history.capture();
            e.consume();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (state.start == null) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - state.start.getX();
            double dy = p.getY() - state.start.getY();
            state.startPositions.forEach((c, origin) -> {
                c.setX(origin.getX() + dx);
                c.setY(origin.getY() + dy);
            });
            e.consume();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (state.start != null) {
                history.record(state.before);
            }
            state.start = null;
            state.before = null;
            state.startPositions.clear();
        });
    }

    private static final class DragState {
        /** Ponto inicial do arrasto em coordenadas de mundo; {@code null} fora de um arrasto. */
        Point2D start;
        final Map<Card, Point2D> startPositions = new LinkedHashMap<>();
        BoardSnapshot before;
        boolean selectOnlyOnRelease;
    }
}
