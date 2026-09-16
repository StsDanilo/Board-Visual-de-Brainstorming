package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Selecionar e arrastar cards (ferramenta Selecionar).
 *
 * Só altera x/y do {@link Card} no modelo; o CardView e as setas acompanham
 * por binding. Trabalha em coordenadas de mundo, então funciona em qualquer
 * nível de zoom.
 */
public class CardDragController {

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;

    public CardDragController(BoardView view, ObjectProperty<Tool> activeTool) {
        this.view = view;
        this.activeTool = activeTool;
    }

    public void attach(CardView cardView) {
        Card card = cardView.getCard();
        DragState state = new DragState();

        // Qualquer clique no card (inclusive no texto) o traz para frente e o seleciona.
        cardView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            cardView.toFront();
            if (activeTool.get() == Tool.SELECT && e.getButton() == MouseButton.PRIMARY) {
                view.select(card);
            }
        });

        cardView.getBody().addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (activeTool.get() != Tool.SELECT
                    || e.getButton() != MouseButton.PRIMARY
                    || cardView.isTextNode(e.getPickResult().getIntersectedNode())) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            state.offsetX = p.getX() - card.getX();
            state.offsetY = p.getY() - card.getY();
            state.dragging = true;
            e.consume();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!state.dragging) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            card.setX(p.getX() - state.offsetX);
            card.setY(p.getY() - state.offsetY);
            e.consume();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_RELEASED, e -> state.dragging = false);
    }

    private static final class DragState {
        boolean dragging;
        double offsetX;
        double offsetY;
    }
}
