package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.AlignmentGuides;
import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.alignment.SnapResult;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Frames;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selecionar e arrastar cards (ferramenta Selecionar).
 *
 * - Clique: seleciona só o card. Shift+clique: inclui/retira da seleção.
 * - Arrastar um card selecionado move todos os selecionados juntos, menos os
 *   travados ({@link Card#isLocked()}), que ficam parados (e servem de
 *   referência para o alinhamento dos que se movem).
 * - Clicar não traz o card para frente: a ordem de empilhamento só muda pelas
 *   ações explícitas ({@link StackOrderController}), e é salva.
 * - Arrastar uma seção (frame) leva junto os cards que estão dentro dela no
 *   início do arrasto ({@link Frames#carriedBy}: centro dentro da área), mesmo
 *   travados, sem que eles entrem na seleção. Seção travada não se move e não
 *   leva nada. Cada card se move uma vez só, mesmo dentro de duas seções
 *   arrastadas juntas ou selecionado junto com a seção dele.
 *
 * Só altera x/y dos cards no modelo; views e setas acompanham por binding.
 * Trabalha em coordenadas de mundo, então funciona em qualquer nível de zoom.
 * Cada arrasto vira um único passo no histórico de desfazer.
 *
 * Alinhamento: ao arrastar, bordas e centros dos cards movidos (como um bloco,
 * se forem vários) grudam nos dos outros cards quando ficam perto, com linhas
 * guia. Segurar Alt desliga, para posicionar livremente. As seções só servem de
 * referência quando o que se arrasta inclui uma seção ({@link #alignmentReferences}):
 * arrastando cards comuns, elas não aparecem nas guias (não poluem); o bloco
 * que alinha é o que foi pego (a seleção), não o conteúdo levado junto.
 */
public class CardDragController {

    /** Distância, em pixels de tela, a partir da qual o card gruda (também usada ao redimensionar). */
    static final double SNAP_DISTANCE_PX = 6;

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

        // Qualquer clique no card (inclusive no texto) ajusta a seleção. Não muda a
        // ordem de empilhamento: isso é só pelas ações explícitas (StackOrderController).
        cardView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            state.selectOnlyOnRelease = false;
            // Clicar num marcador (ex.: o clipe do link) só aciona o marcador:
            // não seleciona, não arrasta.
            if (cardView.isMarkerNode(e.getPickResult().getIntersectedNode())) {
                return;
            }
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
            e.consume();
            // Travados ficam parados; o resto da seleção se move normalmente, e cada seção
            // leva o que está dentro dela agora (calculado uma vez, aqui no início).
            List<Card> grabbed = movableCards(view.getSelection());
            if (grabbed.isEmpty()) {
                return;
            }
            List<Card> all = view.getBoard().getCards();
            List<Card> moving = draggedCards(view.getSelection(), all);
            state.start = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            state.startPositions.clear();
            moving.forEach(c -> state.startPositions.put(c, new Point2D(c.getX(), c.getY())));
            // O bloco que alinha é o que foi pego; as referências não mudam durante o arrasto.
            state.startBox = null;
            for (Card c : all) {
                if (grabbed.contains(c)) {
                    state.startBox = state.startBox == null ? boxOf(c) : state.startBox.union(boxOf(c));
                }
            }
            state.others.clear();
            state.others.addAll(alignmentReferences(all, moving, grabbed.stream().anyMatch(Card::isFrame)));
            state.before = history.capture();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (state.start == null) {
                return;
            }
            Point2D p = view.sceneToWorld(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - state.start.getX();
            double dy = p.getY() - state.start.getY();
            if (e.isAltDown() || state.startBox == null) {
                view.hideGuides();
            } else {
                double threshold = SNAP_DISTANCE_PX / view.zoomProperty().get();
                SnapResult snap = AlignmentGuides.snapMove(state.startBox.translate(dx, dy), state.others, threshold);
                dx += snap.dx();
                dy += snap.dy();
                view.showGuides(snap.guides());
            }
            double finalDx = dx;
            double finalDy = dy;
            state.startPositions.forEach((c, origin) -> {
                c.setX(origin.getX() + finalDx);
                c.setY(origin.getY() + finalDy);
            });
            e.consume();
        });
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (state.start != null) {
                view.hideGuides();
                history.record(state.before);
            }
            state.start = null;
            state.before = null;
            state.startPositions.clear();
        });
    }

    /**
     * Os cards que um arrasto da seleção move: todos, menos os travados (o
     * arrasto em grupo leva os outros e deixa o travado onde está). Vazio = o
     * arrasto não move nada (só cards travados).
     */
    static List<Card> movableCards(Collection<Card> selection) {
        return selection.stream().filter(card -> !card.isLocked()).toList();
    }

    /**
     * Tudo o que um arrasto da seleção move: os {@link #movableCards} e o que
     * está dentro das seções entre eles ({@link Frames#carriedBy}, inclusive
     * travados e seções dentro de seções), cada card uma vez só.
     */
    static List<Card> draggedCards(Collection<Card> selection, Collection<Card> boardCards) {
        List<Card> movable = movableCards(selection);
        Set<Card> dragged = new LinkedHashSet<>(movable);
        dragged.addAll(Frames.carriedBy(movable, boardCards));
        return List.copyOf(dragged);
    }

    /**
     * Caixas de referência do alinhamento: os cards que não se movem. As seções
     * só entram se o gesto envolve uma seção ({@code framesToo}): ao arrastar ou
     * redimensionar cards comuns, as bordas das seções não puxam (nem desenham guias).
     */
    static List<Box> alignmentReferences(Collection<Card> boardCards, Collection<Card> moving, boolean framesToo) {
        List<Box> references = new ArrayList<>();
        for (Card card : boardCards) {
            if (!moving.contains(card) && (framesToo || !card.isFrame())) {
                references.add(boxOf(card));
            }
        }
        return references;
    }

    private static final class DragState {
        /** Ponto inicial do arrasto em coordenadas de mundo; {@code null} fora de um arrasto. */
        Point2D start;
        final Map<Card, Point2D> startPositions = new LinkedHashMap<>();
        BoardSnapshot before;
        boolean selectOnlyOnRelease;
        /** Caixa que envolve os cards movidos, na posição inicial. */
        Box startBox;
        final List<Box> others = new ArrayList<>();
    }

    static Box boxOf(Card card) {
        return new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight());
    }
}
