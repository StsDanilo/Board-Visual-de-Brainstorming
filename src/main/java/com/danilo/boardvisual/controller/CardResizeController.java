package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.alignment.BoxResizer;
import com.danilo.boardvisual.alignment.ResizeHandle;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Redimensionar o card selecionado pelas alças.
 *
 * - Retângulo: 8 alças, largura e altura livres; Shift num canto mantém a proporção.
 * - Elipse e losango: só os 4 cantos, sempre mantendo a proporção.
 * - As bordas puxadas grudam nas linhas de outros cards (mesmo alinhamento de
 *   quando se move; Alt desliga).
 * - Cada gesto é um passo no desfazer. Setas, painel e barra flutuante
 *   acompanham sozinhos, porque dependem da largura/altura do modelo.
 *
 * As alças aparecem só com exatamente um card selecionado e a ferramenta Selecionar.
 */
public class CardResizeController {

    /**
     * Tamanho mínimo de um card. Provisório: quando o tamanho da fonte for
     * automático (card [2] no Trello), o mínimo passa a depender do texto.
     */
    static final double MIN_WIDTH = 80;
    static final double MIN_HEIGHT = 50;

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final EditHistory history;

    private Card target;
    private final InvalidationListener shapeListener = obs -> refresh();

    /** Estado do gesto em andamento; {@code start == null} fora dele. */
    private Point2D start;
    private Box startBox;
    private final List<Box> others = new ArrayList<>();
    private BoardSnapshot before;

    public CardResizeController(BoardView view, ObjectProperty<Tool> activeTool, EditHistory history) {
        this.view = view;
        this.activeTool = activeTool;
        this.history = history;

        for (ResizeHandle handle : ResizeHandle.values()) {
            install(handle, view.getResizeHandles().getHandle(handle));
        }
        view.getSelection().addListener((SetChangeListener<Card>) change -> refresh());
        activeTool.addListener(obs -> refresh());
        refresh();
    }

    private void install(ResizeHandle handle, Region region) {
        region.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (target == null || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            view.requestFocus(); // encerra uma edição de texto em andamento
            start = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            startBox = boxOf(target);
            others.clear();
            view.getBoard().getCards().stream().filter(c -> c != target).map(CardResizeController::boxOf)
                    .forEach(others::add);
            before = history.capture();
            e.consume();
        });
        region.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (start == null) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            boolean keepAspect = target.getShape() != CardShape.RECTANGLE || (e.isShiftDown() && handle.isCorner());
            double threshold = e.isAltDown() ? 0 : CardDragController.SNAP_DISTANCE_PX / view.zoomProperty().get();
            BoxResizer.Result result = BoxResizer.resize(startBox, handle, p.getX() - start.getX(), p.getY() - start.getY(),
                    MIN_WIDTH, MIN_HEIGHT, keepAspect, others, threshold);
            apply(target, result.box());
            view.showGuides(result.guides());
            e.consume();
        });
        region.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (start == null) {
                return;
            }
            view.hideGuides();
            history.record(before);
            start = null;
            before = null;
            e.consume();
        });
    }

    /** Alças só com um card selecionado e na ferramenta Selecionar. */
    private void refresh() {
        Card newTarget = activeTool.get() == Tool.SELECT && view.getSelection().size() == 1
                ? view.getSelection().iterator().next()
                : null;
        if (target != newTarget) {
            if (target != null) {
                target.shapeProperty().removeListener(shapeListener);
            }
            target = newTarget;
            if (target != null) {
                target.shapeProperty().addListener(shapeListener);
            }
        }
        view.setResizeTarget(target, target != null && target.getShape() != CardShape.RECTANGLE);
    }

    private static void apply(Card card, Box box) {
        card.setX(box.x());
        card.setY(box.y());
        card.setWidth(box.width());
        card.setHeight(box.height());
    }

    private static Box boxOf(Card card) {
        return new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight());
    }
}
