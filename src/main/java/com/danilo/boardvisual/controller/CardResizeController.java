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
 * - Retângulo e seção (frame): 8 alças, largura e altura livres; Shift num canto
 *   mantém a proporção. Redimensionar a seção não mexe nos cards de dentro, e o
 *   título nunca a faz crescer (o mínimo é fixo).
 * - Elipse e losango: só os 4 cantos, sempre mantendo a proporção.
 * - Card de imagem: só os 4 cantos, sempre com a proporção da imagem (a
 *   legenda, se houver, acompanha embaixo).
 * - As bordas puxadas grudam nas linhas de outros cards (mesmo alinhamento de
 *   quando se move; Alt desliga).
 * - O card nunca fica menor do que o texto precisa (na fonte mínima, se a
 *   fonte é automática): passando disso, a alça "trava" com o lado oposto parado.
 * - O tamanho final vira o "tamanho definido à mão" do card.
 * - Cada gesto é um passo no desfazer. Setas, painel e barra flutuante
 *   acompanham sozinhos, porque dependem da largura/altura do modelo.
 *
 * As alças aparecem só com exatamente um card selecionado, não travado, e a
 * ferramenta Selecionar.
 */
public class CardResizeController {

    /** Piso de tamanho, mesmo com pouco texto; acima dele, quem manda é o texto. */
    static final double MIN_WIDTH = 60;
    static final double MIN_HEIGHT = 40;
    /** Menor tamanho do maior lado de uma imagem. */
    static final double MIN_IMAGE_SIDE = 40;
    /** Menor seção: cabe a faixa do título e um pouco de área. */
    static final double MIN_FRAME_WIDTH = 120;
    static final double MIN_FRAME_HEIGHT = 80;

    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final EditHistory history;

    /** Card com as alças (nulo se não há, ou se o único selecionado está travado). */
    private Card target;
    /** Único card selecionado, observado (formato e travado) mesmo sem alças. */
    private Card observed;
    private final InvalidationListener shapeListener = obs -> refresh();

    /** Estado do gesto em andamento; {@code start == null} fora dele. */
    private Point2D start;
    private Box startBox;
    /** Largura ÷ altura da imagem, se o card é de imagem; 0 nos demais. */
    private double startImageRatio;
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
            // Mesmas referências do mover: as seções só contam quando é uma seção que muda.
            others.addAll(CardDragController.alignmentReferences(view.getBoard().getCards(), List.of(target),
                    target.isFrame()));
            // Card de imagem: a proporção é a da imagem (o tamanho definido à mão), não a do card com legenda.
            startImageRatio = target.hasImage() && target.getBaseHeight() > 0
                    ? target.getBaseWidth() / target.getBaseHeight() : 0;
            before = history.capture();
            e.consume();
        });
        region.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (start == null) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            double threshold = e.isAltDown() ? 0 : CardDragController.SNAP_DISTANCE_PX / view.zoomProperty().get();
            if (startImageRatio > 0) {
                dragImage(handle, p.getX() - start.getX(), p.getY() - start.getY(), threshold);
                e.consume();
                return;
            }
            boolean keepAspect = !freeAspect(target) || (e.isShiftDown() && handle.isCorner());
            BoxResizer.Result result = BoxResizer.resize(startBox, handle, p.getX() - start.getX(), p.getY() - start.getY(),
                    target.isFrame() ? MIN_FRAME_WIDTH : MIN_WIDTH, target.isFrame() ? MIN_FRAME_HEIGHT : MIN_HEIGHT,
                    keepAspect, others, threshold);
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

    /**
     * Alças só com um card selecionado, destravado, e na ferramenta Selecionar.
     * O card único selecionado é observado mesmo travado: destravar (ou o
     * desfazer) faz as alças aparecerem na hora.
     */
    private void refresh() {
        Card single = activeTool.get() == Tool.SELECT && view.getSelection().size() == 1
                ? view.getSelection().iterator().next()
                : null;
        if (observed != single) {
            if (observed != null) {
                observed.shapeProperty().removeListener(shapeListener);
                observed.lockedProperty().removeListener(shapeListener);
            }
            observed = single;
            if (observed != null) {
                observed.shapeProperty().addListener(shapeListener);
                observed.lockedProperty().addListener(shapeListener);
            }
        }
        target = single != null && !single.isLocked() ? single : null;
        view.setResizeTarget(target, target != null && (!freeAspect(target) || target.hasImage()));
    }

    /** Retângulo e seção mudam largura e altura livremente; elipse e losango mantêm a proporção. */
    private static boolean freeAspect(Card card) {
        return card.getShape() == CardShape.RECTANGLE || card.getShape() == CardShape.FRAME;
    }

    /**
     * Card de imagem: só cantos, sempre com a proporção da imagem. Quem é
     * redimensionado é a imagem (o tamanho definido à mão); a legenda, se
     * houver, continua embaixo dela, e o lado oposto à alça fica parado.
     */
    private void dragImage(ResizeHandle handle, double dx, double dy, double threshold) {
        BoxResizer.Result result = resizeImage(startBox, startImageRatio, handle, dx, dy, others, threshold);
        Box image = result.box();
        double shownHeight = CardTextFit.forScreen().fit(target, image.width(), image.height()).height();
        // Alça de cima: a base do card (fim da legenda) fica parada.
        double y = handle.yEdge() == Edge.START ? startBox.y() + startBox.height() - shownHeight : image.y();
        target.setX(image.x());
        target.setY(y);
        target.resize(image.width(), image.height()); // o recálculo do texto põe a legenda embaixo
        view.showGuides(result.guides());
    }

    /**
     * Nova caixa da imagem de um card de imagem. {@code shown} é a caixa do card
     * na tela no início do gesto (imagem + legenda); a imagem é o topo dela, na
     * proporção {@code ratio} (largura ÷ altura). O tamanho mínimo vale para o
     * maior lado, para imagens muito largas ou altas não "pularem" de tamanho.
     */
    static BoxResizer.Result resizeImage(Box shown, double ratio, ResizeHandle handle, double dx, double dy,
                                         List<Box> others, double threshold) {
        Box image = new Box(shown.x(), shown.y(), shown.width(), shown.width() / ratio);
        double minWidth = ratio >= 1 ? MIN_IMAGE_SIDE : MIN_IMAGE_SIDE * ratio;
        double minHeight = ratio >= 1 ? MIN_IMAGE_SIDE / ratio : MIN_IMAGE_SIDE;
        return BoxResizer.resize(image, handle, dx, dy, minWidth, minHeight, true, others, threshold);
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
