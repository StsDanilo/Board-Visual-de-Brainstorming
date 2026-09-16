package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * Interações com o fundo do canvas.
 *
 * - Em qualquer ferramenta: arrastar o fundo (botão esquerdo) ou qualquer
 *   lugar (botão do meio) faz pan; roda do mouse / pinça faz zoom.
 * - Selecionar: clicar no fundo limpa a seleção.
 * - Retângulo / Elipse / Losango: clicar no fundo cria um card desse formato
 *   ali (com a cor atual da barra) e volta para Selecionar.
 */
public class CanvasController {

    private static final double SCROLL_ZOOM_SENSITIVITY = 0.003;

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final ObservableValue<String> newCardColor;

    private boolean panPressed;
    private double lastSceneX;
    private double lastSceneY;

    public CanvasController(BoardView view, ObjectProperty<Tool> activeTool, ObservableValue<String> newCardColor) {
        this.view = view;
        this.activeTool = activeTool;
        this.newCardColor = newCardColor;
        installHandlers();
    }

    private void installHandlers() {
        // Botão do meio faz pan mesmo sobre um card, por isso é filtro (pega o evento antes dos filhos).
        view.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                startPan(e);
                e.consume();
            }
        });
        view.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && isBackground(e)) {
                view.requestFocus(); // tira o foco do texto de um card
                if (activeTool.get() == Tool.SELECT) {
                    view.clearSelection();
                }
                startPan(e);
            }
        });
        view.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (panPressed) {
                view.setPanning(true);
                view.panBy(e.getSceneX() - lastSceneX, e.getSceneY() - lastSceneY);
                lastSceneX = e.getSceneX();
                lastSceneY = e.getSceneY();
                e.consume();
            }
        });
        view.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (panPressed) {
                panPressed = false;
                view.setPanning(false);
            }
        });
        view.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (activeTool.get().getShape() != null
                    && e.getButton() == MouseButton.PRIMARY
                    && e.isStillSincePress()
                    && isBackground(e)) {
                createCardAt(activeTool.get().getShape(), e.getSceneX(), e.getSceneY());
                activeTool.set(Tool.SELECT);
            }
        });

        view.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() != 0) {
                view.zoomAt(Math.exp(e.getDeltaY() * SCROLL_ZOOM_SENSITIVITY), e.getX(), e.getY());
                e.consume();
            }
        });
        view.setOnZoom(e -> view.zoomAt(e.getZoomFactor(), e.getX(), e.getY()));
    }

    /** Marca o início de um possível pan; só vira pan de fato se o mouse se mover. */
    private void startPan(MouseEvent e) {
        panPressed = true;
        lastSceneX = e.getSceneX();
        lastSceneY = e.getSceneY();
    }

    private boolean isBackground(MouseEvent e) {
        return e.getPickResult().getIntersectedNode() == view;
    }

    private void createCardAt(CardShape shape, double sceneX, double sceneY) {
        Board board = view.getBoard();
        if (board == null) {
            return;
        }
        Point2D p = view.sceneToWorld(sceneX, sceneY);
        Card card = Card.create(shape, p.getX(), p.getY());
        card.setColor(newCardColor.getValue());
        board.addCard(card);
        view.select(card);
        view.getCardView(card).ifPresent(CardView::focusText);
    }
}
