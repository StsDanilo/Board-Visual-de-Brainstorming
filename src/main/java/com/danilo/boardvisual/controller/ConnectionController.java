package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Optional;

/**
 * Criação de conexões arrastando de um card até outro. Começa de dois jeitos:
 *
 * - Ferramenta Conexão: pressionar em qualquer ponto do card (inclusive no
 *   texto, que nesse modo não é editado). A ferramenta continua ativa para
 *   ligar vários cards seguidos.
 * - Ferramenta Selecionar: arrastar a alça na borda direita do card, como
 *   atalho rápido sem trocar de ferramenta.
 *
 * Enquanto arrasta, mostra uma prévia tracejada e destaca o card de destino.
 */
public class ConnectionController {

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final EditHistory history;

    private CardView source;
    private CardView highlighted;

    public ConnectionController(BoardView view, ObjectProperty<Tool> activeTool, EditHistory history) {
        this.view = view;
        this.activeTool = activeTool;
        this.history = history;
    }

    public void attach(CardView cardView) {
        // Filtros no próprio card: pegam o evento antes do TextArea e da alça.
        cardView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Card card = cardView.getCard();
            boolean onHandle = e.getPickResult().getIntersectedNode() == cardView.getConnectorHandle();
            if (activeTool.get() == Tool.CONNECTION) {
                begin(cardView, card.getCenterX(), card.getCenterY());
                e.consume();
            } else if (activeTool.get() == Tool.SELECT && onHandle) {
                Point2D handle = cardView.getConnectorHandle().localToScene(
                        cardView.getConnectorHandle().getCenterX(), cardView.getConnectorHandle().getCenterY());
                Point2D start = view.sceneToWorld(handle.getX(), handle.getY());
                begin(cardView, start.getX(), start.getY());
                e.consume();
            }
        });
        cardView.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (source != cardView) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            view.updateConnectionPreview(p.getX(), p.getY());
            highlight(targetAt(e).orElse(null));
            e.consume();
        });
        cardView.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (source != cardView) {
                return;
            }
            targetAt(e).ifPresent(target ->
                    history.perform(() -> view.getBoard().connect(cardView.getCard(), target.getCard())));
            view.hideConnectionPreview();
            highlight(null);
            source = null;
            e.consume();
        });
    }

    private void begin(CardView cardView, double worldX, double worldY) {
        source = cardView;
        view.showConnectionPreview(worldX, worldY);
    }

    private Optional<CardView> targetAt(MouseEvent e) {
        return view.cardViewAt(e.getSceneX(), e.getSceneY()).filter(target -> target != source);
    }

    private void highlight(CardView target) {
        if (highlighted == target) {
            return;
        }
        if (highlighted != null) {
            highlighted.setConnectionTarget(false);
        }
        highlighted = target;
        if (highlighted != null) {
            highlighted.setConnectionTarget(true);
        }
    }
}
