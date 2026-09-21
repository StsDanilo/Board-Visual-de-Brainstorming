package com.danilo.boardvisual.view;

import com.danilo.boardvisual.alignment.Axis;
import com.danilo.boardvisual.alignment.Guide;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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

import java.util.ArrayList;
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
 *  ├─ marquee          (caixa de seleção, em pixels de tela)
 *  ├─ guideLayer       (linhas guia do alinhamento, em pixels de tela)
 *  ├─ resizeHandles    (alças de redimensionamento do card selecionado, em pixels de tela)
 *  ├─ selectionOverlay (barra flutuante da seleção, em pixels de tela: não sofre zoom)
 *  └─ panelOverlay     (painel flutuante aberto, ao lado do card, em pixels de tela)
 * </pre>
 * Cards e setas usam coordenadas de "mundo"; pan/zoom só mexem nas
 * transformações do {@code world}, nunca no modelo.
 */
public class BoardView extends Pane {

    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.0;
    private static final PseudoClass PANNING = PseudoClass.getPseudoClass("panning");
    /** Espaço pressionado: arrastar move a visão. */
    private static final PseudoClass SPACE_PAN = PseudoClass.getPseudoClass("space-pan");
    /** Ativa com qualquer ferramenta que cria cards (retângulo, elipse, losango). */
    private static final PseudoClass TOOL_CREATE = PseudoClass.getPseudoClass("tool-create");
    /** Distância entre a barra flutuante e o card, e margem até as bordas do canvas. */
    private static final double OVERLAY_GAP = 10;
    /**
     * Folga extra da barra flutuante quando há alças de redimensionar: sem ela,
     * num card pequeno a barra fica sobre as alças de cima e "come" o clique.
     */
    private static final double RESIZE_HANDLE_CLEARANCE = 14;

    private final Group world = new Group();
    private final Group connectionLayer = new Group();
    private final Group cardLayer = new Group();
    private final Group overlayLayer = new Group();

    private final Translate pan = new Translate();
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(this, "zoom", 1);

    private final Line connectionPreview = new Line();
    private final Rectangle marquee = new Rectangle();
    private final Group guideLayer = new Group();
    private final ResizeHandlesView resizeHandles = new ResizeHandlesView();
    private Card resizeTarget;
    private boolean resizeCornersOnly;
    private final InvalidationListener resizeUpdater = obs -> updateResizeHandles();

    private final Map<Card, CardView> cardViews = new HashMap<>();
    private final Map<Connection, ConnectionView> connectionViews = new HashMap<>();

    /** Cards selecionados (um ou vários). */
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

    private Region panelOverlay;
    private final ReadOnlyObjectWrapper<Card> panelAnchor = new ReadOnlyObjectWrapper<>(this, "panelAnchor");
    private final InvalidationListener panelUpdater = obs -> updatePanelOverlay();

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

        marquee.getStyleClass().add("selection-marquee");
        marquee.setManaged(false);
        marquee.setMouseTransparent(true);
        marquee.setVisible(false);

        guideLayer.setManaged(false);
        guideLayer.setMouseTransparent(true);

        resizeHandles.setManaged(false);
        resizeHandles.setVisible(false);

        getChildren().addAll(world, marquee, guideLayer, resizeHandles);

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
        zoom.addListener(panelUpdater);
        pan.xProperty().addListener(panelUpdater);
        pan.yProperty().addListener(panelUpdater);
        zoom.addListener(resizeUpdater);
        pan.xProperty().addListener(resizeUpdater);
        pan.yProperty().addListener(resizeUpdater);
        widthProperty().addListener(panelUpdater);
        heightProperty().addListener(panelUpdater);
        setActiveTool(Tool.SELECT);
    }

    // ---------------------------------------------------------------- board

    public Board getBoard() {
        return board;
    }

    /** Troca o board exibido, recriando todos os nós visuais. */
    public void setBoard(Board newBoard) {
        hidePanel();
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
                // contains() respeita a silhueta do formato (cantos da elipse não contam).
                if (local != null && cardView.getBody().contains(local)) {
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
        if (card == panelAnchor.get()) {
            hidePanel();
        }
        if (card == resizeTarget) {
            setResizeTarget(null, false);
        }
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

    public void selectAll() {
        setSelection(cardViews.keySet());
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
            selectionOverlay.heightProperty().removeListener(overlayUpdater);
        }
        selectionOverlay = overlay;
        if (overlay != null) {
            // Fica "managed": quando o conteúdo muda (ex.: botões que aparecem),
            // o Pane reajusta o tamanho no próximo layout e os listeners reposicionam.
            overlay.widthProperty().addListener(overlayUpdater);
            overlay.heightProperty().addListener(overlayUpdater);
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
        // Durante a caixa de seleção a barra some, para não ficar pulando a cada card incluído.
        selectionOverlay.setVisible(target.isPresent() && !marquee.isVisible());
        if (target.isEmpty()) {
            return;
        }
        Bounds bounds = target.get();
        selectionOverlay.applyCss();
        selectionOverlay.autosize();
        double w = selectionOverlay.getWidth();
        double h = selectionOverlay.getHeight();

        double gap = resizeTarget != null ? OVERLAY_GAP + RESIZE_HANDLE_CLEARANCE : OVERLAY_GAP;
        double x = bounds.getCenterX() - w / 2;
        double y = bounds.getMinY() - gap - h;
        if (y < OVERLAY_GAP) {
            y = bounds.getMaxY() + gap; // sem espaço acima: abre abaixo do card
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

    // ------------------------------------------------------ painel flutuante

    /**
     * Mostra um painel ancorado ao card: à direita dele (ou à esquerda, se não
     * couber), alinhado ao topo. Acompanha o card ao mover, pan e zoom. Só um
     * painel fica aberto por vez; abrir outro fecha o anterior.
     */
    public void showPanel(Region panel, Card anchor) {
        hidePanel();
        panelOverlay = panel;
        panelAnchor.set(anchor);
        // "Managed" pelo mesmo motivo da barra: cresce sozinho ao adicionar itens.
        panel.widthProperty().addListener(panelUpdater);
        panel.heightProperty().addListener(panelUpdater);
        CardGeometry.forEach(anchor, p -> p.addListener(panelUpdater));
        getChildren().add(panel);
        getCardView(anchor).ifPresent(v -> v.setPanelOpen(true));
        updatePanelOverlay();
    }

    /** Fecha o painel aberto (também acontece sozinho se o card sair do board). */
    public void hidePanel() {
        Card anchor = panelAnchor.get();
        if (panelOverlay == null || anchor == null) {
            return;
        }
        panelOverlay.widthProperty().removeListener(panelUpdater);
        panelOverlay.heightProperty().removeListener(panelUpdater);
        CardGeometry.forEach(anchor, p -> p.removeListener(panelUpdater));
        getChildren().remove(panelOverlay);
        getCardView(anchor).ifPresent(v -> v.setPanelOpen(false));
        panelOverlay = null;
        panelAnchor.set(null);
    }

    /** Card cujo painel está aberto, ou {@code null}. */
    public ReadOnlyObjectProperty<Card> panelAnchorProperty() {
        return panelAnchor.getReadOnlyProperty();
    }

    private void updatePanelOverlay() {
        Card anchor = panelAnchor.get();
        if (panelOverlay == null || anchor == null) {
            return;
        }
        panelOverlay.applyCss();
        panelOverlay.autosize();
        double w = panelOverlay.getWidth();
        double h = panelOverlay.getHeight();
        Point2D topLeft = world.localToParent(anchor.getX(), anchor.getY());
        Point2D bottomRight = world.localToParent(anchor.getX() + anchor.getWidth(), anchor.getY() + anchor.getHeight());

        double x = bottomRight.getX() + OVERLAY_GAP;
        if (x + w > getWidth() - OVERLAY_GAP) {
            x = topLeft.getX() - OVERLAY_GAP - w; // sem espaço à direita: abre à esquerda
        }
        x = Math.max(OVERLAY_GAP, Math.min(x, getWidth() - w - OVERLAY_GAP));
        double y = Math.max(OVERLAY_GAP, Math.min(topLeft.getY(), getHeight() - h - OVERLAY_GAP));
        panelOverlay.relocate(Math.round(x), Math.round(y));
    }

    // ------------------------------------------------------ redimensionamento

    public ResizeHandlesView getResizeHandles() {
        return resizeHandles;
    }

    /**
     * Mostra as alças em volta do card ({@code null} esconde). Elas acompanham
     * o card ao mover, redimensionar, pan e zoom.
     *
     * @param cornersOnly só as alças dos cantos (formatos que mantêm a proporção)
     */
    public void setResizeTarget(Card card, boolean cornersOnly) {
        if (resizeTarget != null) {
            CardGeometry.forEach(resizeTarget, p -> p.removeListener(resizeUpdater));
        }
        resizeTarget = card != null && cardViews.containsKey(card) ? card : null;
        resizeCornersOnly = cornersOnly;
        if (resizeTarget != null) {
            CardGeometry.forEach(resizeTarget, p -> p.addListener(resizeUpdater));
        }
        updateResizeHandles();
        updateSelectionOverlay(); // a folga da barra depende de haver alças
    }

    private void updateResizeHandles() {
        // Some durante a caixa de seleção, como a barra flutuante.
        boolean show = resizeTarget != null && !marquee.isVisible();
        resizeHandles.setVisible(show);
        if (show) {
            Point2D topLeft = world.localToParent(resizeTarget.getX(), resizeTarget.getY());
            Point2D bottomRight = world.localToParent(resizeTarget.getX() + resizeTarget.getWidth(),
                    resizeTarget.getY() + resizeTarget.getHeight());
            resizeHandles.layoutAround(new BoundingBox(topLeft.getX(), topLeft.getY(),
                    bottomRight.getX() - topLeft.getX(), bottomRight.getY() - topLeft.getY()), resizeCornersOnly);
        }
    }

    // ------------------------------------------------------ linhas guia

    /**
     * Mostra as linhas guia do alinhamento. Elas vêm em coordenadas do board e
     * são desenhadas em pixels de tela, para ter a mesma espessura em qualquer zoom.
     */
    public void showGuides(List<Guide> guides) {
        List<Node> lines = new ArrayList<>();
        for (Guide guide : guides) {
            Point2D a;
            Point2D b;
            if (guide.axis() == Axis.X) {
                a = world.localToParent(guide.position(), guide.start());
                b = world.localToParent(guide.position(), guide.end());
            } else {
                a = world.localToParent(guide.start(), guide.position());
                b = world.localToParent(guide.end(), guide.position());
            }
            // Meio pixel: linha de 1px nítida em vez de borrada entre dois pixels.
            Line line = new Line(snapHalf(a.getX()), snapHalf(a.getY()), snapHalf(b.getX()), snapHalf(b.getY()));
            line.getStyleClass().add("alignment-guide");
            lines.add(line);
        }
        guideLayer.getChildren().setAll(lines);
    }

    public void hideGuides() {
        guideLayer.getChildren().clear();
    }

    private static double snapHalf(double value) {
        return Math.round(value) + 0.5;
    }

    // ------------------------------------------------------ caixa de seleção

    /** Mostra a caixa de seleção entre dois pontos (coordenadas de cena). */
    public void showMarquee(double sceneX1, double sceneY1, double sceneX2, double sceneY2) {
        Point2D a = sceneToLocal(sceneX1, sceneY1);
        Point2D b = sceneToLocal(sceneX2, sceneY2);
        marquee.setX(Math.min(a.getX(), b.getX()));
        marquee.setY(Math.min(a.getY(), b.getY()));
        marquee.setWidth(Math.abs(a.getX() - b.getX()));
        marquee.setHeight(Math.abs(a.getY() - b.getY()));
        if (!marquee.isVisible()) {
            marquee.setVisible(true);
            updateSelectionOverlay();
            updateResizeHandles();
        }
    }

    public void hideMarquee() {
        if (marquee.isVisible()) {
            marquee.setVisible(false);
            updateSelectionOverlay();
            updateResizeHandles();
        }
    }

    /** Cards que tocam o retângulo entre dois pontos (coordenadas de cena). */
    public List<Card> cardsInSceneRect(double sceneX1, double sceneY1, double sceneX2, double sceneY2) {
        if (board == null) {
            return List.of();
        }
        Point2D a = sceneToWorld(sceneX1, sceneY1);
        Point2D b = sceneToWorld(sceneX2, sceneY2);
        Bounds area = new BoundingBox(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
        return board.getCards().stream()
                .filter(c -> area.intersects(c.getX(), c.getY(), c.getWidth(), c.getHeight()))
                .toList();
    }

    // ------------------------------------------------ estado visual da interação

    /** Reflete a ferramenta ativa como pseudo-classe (o CSS ajusta cursores e alças). */
    public void setActiveTool(Tool activeTool) {
        for (Tool tool : Tool.values()) {
            pseudoClassStateChanged(tool.getPseudoClass(), tool == activeTool);
        }
        pseudoClassStateChanged(TOOL_CREATE, activeTool != null && activeTool.getShape() != null);
    }

    public void setSpacePan(boolean active) {
        pseudoClassStateChanged(SPACE_PAN, active);
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

    /** Posição e zoom da visão, para cada board aninhado lembrar onde você estava. */
    public record ViewState(double panX, double panY, double zoom) {
        public static final ViewState DEFAULT = new ViewState(0, 0, 1);
    }

    public ViewState getViewState() {
        return new ViewState(pan.getX(), pan.getY(), zoom.get());
    }

    public void setViewState(ViewState state) {
        zoom.set(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, state.zoom())));
        pan.setX(state.panX());
        pan.setY(state.panY());
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
