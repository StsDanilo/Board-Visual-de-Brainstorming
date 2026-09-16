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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Interações com o fundo do canvas.
 *
 * - Mover a visão (pan): botão do meio ou direito em qualquer lugar,
 *   Espaço + arrastar, ou arrastar o fundo nas ferramentas de criação/conexão.
 * - Zoom: roda do mouse / pinça, em torno do cursor.
 * - Selecionar: clicar no fundo limpa a seleção; arrastar o fundo desenha a
 *   caixa de seleção (com Shift, soma à seleção atual).
 * - Retângulo / Elipse / Losango: clicar no fundo cria um card desse formato
 *   ali (com a cor atual da barra) e volta para Selecionar.
 */
public class CanvasController {

    private static final double SCROLL_ZOOM_SENSITIVITY = 0.003;

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final ObservableValue<String> newCardColor;
    private final EditHistory history;

    private boolean spaceDown;

    private boolean panPressed;
    private double lastSceneX;
    private double lastSceneY;

    private boolean marqueePressed;
    private double marqueeStartX;
    private double marqueeStartY;
    private Set<Card> selectionBeforeMarquee = Set.of();

    public CanvasController(BoardView view, ObjectProperty<Tool> activeTool,
                            ObservableValue<String> newCardColor, EditHistory history) {
        this.view = view;
        this.activeTool = activeTool;
        this.newCardColor = newCardColor;
        this.history = history;
        installHandlers();
    }

    /** Informado pelo controller de teclado: com Espaço pressionado, arrastar move a visão. */
    public void setSpaceDown(boolean down) {
        spaceDown = down;
        view.setSpacePan(down);
    }

    private void installHandlers() {
        // Filtro: pega o clique antes dos cards, para o pan funcionar mesmo sobre eles.
        view.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            boolean panButton = e.getButton() == MouseButton.MIDDLE || e.getButton() == MouseButton.SECONDARY;
            if (panButton || (spaceDown && e.getButton() == MouseButton.PRIMARY)) {
                view.requestFocus();
                startPan(e);
                e.consume();
            }
        });
        view.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || !isBackground(e)) {
                return;
            }
            view.requestFocus(); // tira o foco do texto de um card
            if (activeTool.get() == Tool.SELECT) {
                startMarquee(e);
            } else {
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
            } else if (marqueePressed) {
                updateMarquee(e);
                e.consume();
            }
        });
        view.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (panPressed) {
                panPressed = false;
                view.setPanning(false);
            }
            if (marqueePressed) {
                marqueePressed = false;
                view.hideMarquee();
            }
        });
        view.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            CardShape shape = activeTool.get().getShape();
            if (shape != null
                    && e.getButton() == MouseButton.PRIMARY
                    && e.isStillSincePress()
                    && isBackground(e)) {
                createCardAt(shape, e.getSceneX(), e.getSceneY());
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

    private void startMarquee(MouseEvent e) {
        marqueePressed = true;
        marqueeStartX = e.getSceneX();
        marqueeStartY = e.getSceneY();
        if (e.isShiftDown()) {
            selectionBeforeMarquee = Set.copyOf(view.getSelection());
        } else {
            selectionBeforeMarquee = Set.of();
            view.clearSelection();
        }
    }

    private void updateMarquee(MouseEvent e) {
        view.showMarquee(marqueeStartX, marqueeStartY, e.getSceneX(), e.getSceneY());
        List<Card> touched = view.cardsInSceneRect(marqueeStartX, marqueeStartY, e.getSceneX(), e.getSceneY());
        Set<Card> selection = new LinkedHashSet<>(selectionBeforeMarquee);
        selection.addAll(touched);
        view.setSelection(selection);
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
        history.perform(() -> board.addCard(card));
        view.select(card);
        view.getCardView(card).ifPresent(CardView::focusText);
    }
}
