package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.alignment.BoxResizer;
import com.danilo.boardvisual.alignment.Edge;
import com.danilo.boardvisual.alignment.ResizeHandle;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardTextFit;
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
 * - O card nunca fica menor do que o texto precisa (na fonte mínima, se a
 *   fonte é automática): passando disso, a alça "trava" com o lado oposto parado.
 * - O tamanho final vira o "tamanho definido à mão" do card.
 * - Cada gesto é um passo no desfazer. Setas, painel e barra flutuante
 *   acompanham sozinhos, porque dependem da largura/altura do modelo.
 *
 * As alças aparecem só com exatamente um card selecionado e a ferramenta Selecionar.
 */
public class CardResizeController {

    /** Piso de tamanho, mesmo com pouco texto; acima dele, quem manda é o texto. */
    static final double MIN_WIDTH = 60;
    static final double MIN_HEIGHT = 40;

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
            Box box = fitText(target, handle, result.box());
            apply(target, box);
            view.showGuides(box.equals(result.box()) ? result.guides() : List.of());
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

    /**
     * Aumenta a caixa até o texto caber, mantendo parado o lado oposto à alça
     * (o texto é o mínimo "de verdade" do card).
     */
    private static Box fitText(Card card, ResizeHandle handle, Box box) {
        CardTextFit.Fit fit = CardTextFit.forScreen().fit(card, box.width(), box.height());
        if (fit.width() <= box.width() && fit.height() <= box.height()) {
            return box;
        }
        double x = handle.xEdge() == Edge.START ? box.x() + box.width() - fit.width() : box.x();
        double y = handle.yEdge() == Edge.START ? box.y() + box.height() - fit.height() : box.y();
        return new Box(x, y, fit.width(), fit.height());
    }

    private static void apply(Card card, Box box) {
        card.setX(box.x());
        card.setY(box.y());
        card.resize(box.width(), box.height());
    }

    private static Box boxOf(Card card) {
        return new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight());
    }
}
