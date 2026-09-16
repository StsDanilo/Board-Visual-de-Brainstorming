package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Canvas navegável que exibe um {@link Board}.
 *
 * Estrutura:
 * <pre>
 * BoardView (viewport, recortado ao tamanho visível)
 *  └─ world (recebe pan + zoom)
 *      ├─ connectionLayer  (setas, ficam atrás dos cards)
 *      ├─ cardLayer        (cards)
 *      └─ overlayLayer     (prévia da seta sendo criada)
 *  └─ selectionOverlay (barra flutuante da seleção, em pixels de tela: não sofre zoom)
 * </pre>
 * Cards e setas usam coordenadas de "mundo"; pan/zoom só mexem nas
 * transformações do {@code world}, nunca no modelo.
 */
public class BoardView extends Pane {

    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.0;
    private static final PseudoClass PANNING = PseudoClass.getPseudoClass("panning");
    /** Distância entre a barra flutuante e o card, e margem até as bordas do canvas. */
    private static final double OVERLAY_GAP = 10;

    private final Group world = new Group();
    private final Group connectionLayer = new Group();
    private final Group cardLayer = new Group();
    private final Group overlayLayer = new Group();

    private final Translate pan = new Translate();
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(this, "zoom", 1);

    private final Line connectionPreview = new Line();

    private final Map<Card, CardView> cardViews = new HashMap<>();
    private final Map<Connection, ConnectionView> connectionViews = new HashMap<>();

    /**
     * Cards selecionados. Já é um conjunto para a seleção múltipla encaixar
     * sem mudança de estrutura; por enquanto só se seleciona um por vez.
     */
    private final ObservableSet<Card> selection = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableSet<Card> readOnlySelection = FXCollections.unmodifiableObservableSet(selection);

    private final ListChangeListener<Card> cardsListener = change -> {
        while (change.next()) {
            change.getRemoved().forEach(this::removeCardView);
            change.getAddedSubList().forEach(this::addCardView);
        }
    };
    private final ListChangeListener<Connection> connectionsListener = change -> {
        while (change.next()) {
            change.getRemoved().forEach(this::removeConnectionView);
            change.getAddedSubList().forEach(this::addConnectionView);
        }
    };

    private Board board;
    private Consumer<CardView> cardViewInitializer = cardView -> { };

    private Region selectionOverlay;
    private final Set<Card> overlayTrackedCards = new HashSet<>();
    private final InvalidationListener overlayUpdater = obs -> updateSelectionOverlay();

    public BoardView() {
        getStyleClass().add("board-view");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        scale.xProperty().bind(zoom);
        scale.yProperty().bind(zoom);
        world.getTransforms().addAll(pan, scale);
        world.setManaged(false);

        connectionPreview.getStyleClass().add("connection-preview");
        connectionPreview.setMouseTransparent(true);
        connectionPreview.setVisible(false);
        overlayLayer.getChildren().add(connectionPreview);
        overlayLayer.setMouseTransparent(true);

        world.getChildren().addAll(connectionLayer, cardLayer, overlayLayer);
        getChildren().add(world);

        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        selection.addListener((SetChangeListener<Card>) change -> {
            if (change.wasRemoved()) {
                getCardView(change.getElementRemoved()).ifPresent(v -> v.setSelected(false));
            }
            if (change.wasAdded()) {
                getCardView(change.getElementAdded()).ifPresent(v -> v.setSelected(true));
            }
            trackSelectionForOverlay();
        });
        zoom.addListener(overlayUpdater);
        pan.xProperty().addListener(overlayUpdater);
        pan.yProperty().addListener(overlayUpdater);
        widthProperty().addListener(overlayUpdater);
        heightProperty().addListener(overlayUpdater);
        setActiveTool(Tool.SELECT);
    }

    // ---------------------------------------------------------------- board

    public Board getBoard() {
        return board;
    }

    /** Troca o board exibido, recriando todos os nós visuais. */
    public void setBoard(Board newBoard) {
        if (board != null) {
            board.getCards().removeListener(cardsListener);
            board.getConnections().removeListener(connectionsListener);
        }
        selection.clear();
        cardLayer.getChildren().clear();
        connectionLayer.getChildren().clear();
        cardViews.clear();
        connectionViews.clear();

        board = newBoard;
        if (board == null) {
            return;
        }
        board.getCards().forEach(this::addCardView);
        board.getConnections().forEach(this::addConnectionView);
        board.getCards().addListener(cardsListener);
        board.getConnections().addListener(connectionsListener);
    }

    /**
     * Chamado para cada {@link CardView} criado. É assim que os controllers
     * instalam seus handlers sem que a view precise conhecê-los.
     */
    public void setCardViewInitializer(Consumer<CardView> initializer) {
        cardViewInitializer = initializer == null ? cardView -> { } : initializer;
    }

    public Optional<CardView> getCardView(Card card) {
        return Optional.ofNullable(cardViews.get(card));
    }

    /** Card visualmente mais à frente sob o ponto (coordenadas de cena). */
    public Optional<CardView> cardViewAt(double sceneX, double sceneY) {
        List<Node> children = cardLayer.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof CardView cardView) {
                Point2D local = cardView.getBody().sceneToLocal(sceneX, sceneY);
                if (local != null && cardView.getBody().getLayoutBounds().contains(local)) {
                    return Optional.of(cardView);
                }
            }
        }
        return Optional.empty();
    }

    private void addCardView(Card card) {
        CardView cardView = new CardView(card);
        cardViews.put(card, cardView);
        cardLayer.getChildren().add(cardView);
        cardViewInitializer.accept(cardView);
    }

    private void removeCardView(Card card) {
        selection.remove(card);
        CardView cardView = cardViews.remove(card);
        if (cardView != null) {
            cardLayer.getChildren().remove(cardView);
        }
    }

    private void addConnectionView(Connection connection) {
        ConnectionView view = new ConnectionView(connection);
        connectionViews.put(connection, view);
        connectionLayer.getChildren().add(view);
    }

    private void removeConnectionView(Connection connection) {
        ConnectionView view = connectionViews.remove(connection);
        if (view != null) {
            connectionLayer.getChildren().remove(view);
        }
    }

    // ------------------------------------------------------------ seleção

    public ObservableSet<Card> getSelection() {
        return readOnlySelection;
    }

    /** Seleciona somente o card informado. */
    public void select(Card card) {
        setSelection(card == null ? List.of() : List.of(card));
    }

    /** Substitui a seleção pelos cards informados (ignora cards fora do board). */
    public void setSelection(Collection<Card> cards) {
        Set<Card> wanted = new HashSet<>();
        for (Card card : cards) {
            if (cardViews.containsKey(card)) {
                wanted.add(card);
            }
        }
        if (selection.equals(wanted)) {
            return;
        }
        selection.retainAll(wanted);
        selection.addAll(wanted);
    }

    public void clearSelection() {
        selection.clear();
    }

    // ------------------------------------------- barra flutuante da seleção

    /**
     * Define o nó exibido logo acima da seleção (ou abaixo, se não houver
     * espaço). Ele acompanha a seleção quando cards se movem e quando há pan
     * ou zoom, mantendo sempre o mesmo tamanho na tela.
     */
    public void setSelectionOverlay(Region overlay) {
        if (selectionOverlay != null) {
            getChildren().remove(selectionOverlay);
            selectionOverlay.widthProperty().removeListener(overlayUpdater);
        }
        selectionOverlay = overlay;
        if (overlay != null) {
            overlay.setManaged(false);
            overlay.widthProperty().addListener(overlayUpdater);
            getChildren().add(overlay);
        }
        updateSelectionOverlay();
    }

    private void trackSelectionForOverlay() {
        overlayTrackedCards.forEach(card -> CardGeometry.forEach(card, p -> p.removeListener(overlayUpdater)));
        overlayTrackedCards.clear();
        overlayTrackedCards.addAll(selection);
        overlayTrackedCards.forEach(card -> CardGeometry.forEach(card, p -> p.addListener(overlayUpdater)));
        updateSelectionOverlay();
    }

    private void updateSelectionOverlay() {
        if (selectionOverlay == null) {
            return;
        }
        Optional<Bounds> target = selectionBoundsInView();
        selectionOverlay.setVisible(target.isPresent());
        if (target.isEmpty()) {
            return;
        }
        Bounds bounds = target.get();
        selectionOverlay.applyCss();
        selectionOverlay.autosize();
        double w = selectionOverlay.getWidth();
        double h = selectionOverlay.getHeight();

        double x = bounds.getCenterX() - w / 2;
        double y = bounds.getMinY() - OVERLAY_GAP - h;
        if (y < OVERLAY_GAP) {
            y = bounds.getMaxY() + OVERLAY_GAP; // sem espaço acima: abre abaixo do card
        }
        x = Math.max(OVERLAY_GAP, Math.min(x, getWidth() - w - OVERLAY_GAP));
        y = Math.max(OVERLAY_GAP, Math.min(y, getHeight() - h - OVERLAY_GAP));
        selectionOverlay.relocate(Math.round(x), Math.round(y));
    }

    /** Retângulo que envolve os cards selecionados, em coordenadas deste componente. */
    private Optional<Bounds> selectionBoundsInView() {
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Card card : selection) {
            Point2D topLeft = world.localToParent(card.getX(), card.getY());
            Point2D bottomRight = world.localToParent(card.getX() + card.getWidth(), card.getY() + card.getHeight());
            minX = Math.min(minX, topLeft.getX());
            minY = Math.min(minY, topLeft.getY());
            maxX = Math.max(maxX, bottomRight.getX());
            maxY = Math.max(maxY, bottomRight.getY());
        }
        return Optional.of(new BoundingBox(minX, minY, maxX - minX, maxY - minY));
    }

    // ------------------------------------------------ estado visual da interação

    /** Reflete a ferramenta ativa como pseudo-classe (o CSS ajusta cursores e alças). */
    public void setActiveTool(Tool activeTool) {
        for (Tool tool : Tool.values()) {
            pseudoClassStateChanged(tool.getPseudoClass(), tool == activeTool);
        }
    }

    public void setPanning(boolean panning) {
        pseudoClassStateChanged(PANNING, panning);
    }

    // ------------------------------------------------------ pan / zoom

    public ReadOnlyDoubleProperty zoomProperty() {
        return zoom.getReadOnlyProperty();
    }

    /** Converte coordenadas de cena (eventos de mouse) para coordenadas do mundo. */
    public Point2D sceneToWorld(double sceneX, double sceneY) {
        return world.sceneToLocal(sceneX, sceneY);
    }

    /** Desloca a visualização, em pixels de tela. */
    public void panBy(double dx, double dy) {
        pan.setX(pan.getX() + dx);
        pan.setY(pan.getY() + dy);
    }

    /**
     * Aplica zoom mantendo fixo o ponto ({@code viewX}, {@code viewY}),
     * dado em coordenadas locais deste componente (ex.: posição do cursor).
     */
    public void zoomAt(double factor, double viewX, double viewY) {
        double oldZoom = zoom.get();
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, oldZoom * factor));
        if (newZoom == oldZoom) {
            return;
        }
        double worldX = (viewX - pan.getX()) / oldZoom;
        double worldY = (viewY - pan.getY()) / oldZoom;
        zoom.set(newZoom);
        pan.setX(viewX - worldX * newZoom);
        pan.setY(viewY - worldY * newZoom);
    }

    public void resetView() {
        zoom.set(1);
        pan.setX(0);
        pan.setY(0);
    }

    // ------------------------------------------------ prévia de conexão

    public void showConnectionPreview(double worldX, double worldY) {
        connectionPreview.setStartX(worldX);
        connectionPreview.setStartY(worldY);
        connectionPreview.setEndX(worldX);
        connectionPreview.setEndY(worldY);
        connectionPreview.setVisible(true);
    }

    public void updateConnectionPreview(double worldX, double worldY) {
        connectionPreview.setEndX(worldX);
        connectionPreview.setEndY(worldY);
    }

    public void hideConnectionPreview() {
        connectionPreview.setVisible(false);
    }
}
