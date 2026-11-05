package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Frames;
import com.danilo.boardvisual.model.PanelMode;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Representação visual de um {@link Card}.
 *
 * Só faz binding com o modelo: posição, tamanho, texto, cor e formato vêm do
 * card. Não trata eventos de mouse; os controllers se penduram nos nós
 * expostos aqui ({@link #getBody()}, {@link #getConnectorHandle()}).
 *
 * <pre>
 * CardView
 *  ├─ body (.card)            fundo e borda; o CSS dá a silhueta de cada formato
 *  │   ├─ frameFill           só na seção: o fundo translúcido (não recebe clique)
 *  │   ├─ frameGrip           só na seção: anel invisível na borda, que recebe o clique
 *  │   ├─ imageBox            imagem no topo (só em cards de imagem), ou o aviso de que ela falta
 *  │   ├─ frameTitle          só na seção: o título (faixa do topo), fora da edição
 *  │   └─ content             área do texto, recuada para caber no formato (e abaixo da imagem)
 *  │       └─ textArea
 *  ├─ panelButton             abre o painel flutuante (só em cards com painel)
 *  ├─ boardButton             entra no board filho (só em cards com board aninhado)
 *  ├─ marcadores              botõezinhos na borda de cima (clipe do link, seta do link para outro board),
 *  │                          ver {@link CardMarkers}
 *  ├─ connectorHandle         alça para criar conexões
 *  └─ lockBadge               cadeado no canto de cima à esquerda (só com o card travado, só na tela)
 * </pre>
 *
 * <b>Seção (frame, {@link Card#isFrame()}):</b> o {@code body} não tem fundo
 * (o fundo é o {@code frameFill}, que não recebe clique), então o meio vazio da
 * seção não pertence a ela: o clique cai no que está atrás (o fundo do canvas,
 * com caixa de seleção, criar card, pan...). Só a borda ({@code frameGrip}, um
 * anel de 10 px) e o título ({@code frameTitle}) pegam o clique, e o evento
 * sobe até o {@code body} como em qualquer card (selecionar, arrastar, menu).
 * O título é o texto do card: fora da edição aparece num rótulo de uma linha,
 * cortado com reticências; na edição (duplo clique) aparece o campo de texto.
 */
public class CardView extends Region {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass CONNECTION_TARGET = PseudoClass.getPseudoClass("connection-target");
    private static final PseudoClass PANEL_OPEN = PseudoClass.getPseudoClass("panel-open");
    private static final PseudoClass HAS_BOARD = PseudoClass.getPseudoClass("has-board");
    private static final PseudoClass HAS_IMAGE = PseudoClass.getPseudoClass("has-image");
    private static final PseudoClass BROKEN = PseudoClass.getPseudoClass("broken");
    private static final PseudoClass LOCKED = PseudoClass.getPseudoClass("locked");
    private static final PseudoClass FRAME = PseudoClass.getPseudoClass("frame");
    private static final PseudoClass PLACEHOLDER = PseudoClass.getPseudoClass("placeholder");
    /** Título exibido de uma seção sem título (só na tela; no PDF/PNG fica em branco). */
    public static final String FRAME_PLACEHOLDER = "Seção";
    /** Texto de exemplo do título de uma seção, durante a edição. */
    public static final String FRAME_PROMPT_TEXT = "Título da seção";
    /** Selo do cadeado (card travado): tamanho e quanto ele sai para fora do canto de cima à esquerda. */
    private static final double LOCK_BADGE_SIZE = 16;
    private static final double LOCK_BADGE_OUTSIDE = 6;
    private static final String SHAPE_CLASS_PREFIX = "shape-";
    /** Texto de exemplo do card vazio (também usado para ajustar a fonte automática). */
    public static final String PROMPT_TEXT = "Escreva uma ideia...";
    /** Texto de exemplo da legenda de um card de imagem (aparece só enquanto ela é editada vazia). */
    public static final String CAPTION_PROMPT_TEXT = "Escreva uma legenda...";
    /** A imagem fica dentro da borda do card ({@code .card}: 1 px). */
    private static final double IMAGE_INSET = 1;
    /** Raio dos cantos da imagem: o do card (8) menos a borda. */
    private static final double IMAGE_CORNER = 7;
    /** Distância da alça de conexão para fora da borda direita. */
    private static final double CONNECTOR_OFFSET = 14;

    private final Card card;
    private final CardImageLoader imageLoader;
    private final StackPane body = new StackPane();
    private final VBox content = new VBox();
    private final TextArea textArea = new TextArea();
    private final Circle connectorHandle = new Circle(6);
    private final Button panelButton = new Button();
    private final Button boardButton = new Button();
    /** Área da imagem no topo do card (só em cards de imagem): a imagem ou o aviso de que ela falta. */
    private final StackPane imageBox = new StackPane();
    private final ImageView imageView = new ImageView();
    private final Label missingImage = new Label("Imagem não encontrada");
    /** Marcadores na borda de cima, um botão por tipo (ver {@link CardMarkers}). */
    private final Map<CardMarkers.Kind, Button> markers = new EnumMap<>(CardMarkers.Kind.class);
    /** Dica do marcador do link para outro board (o controller põe o caminho do destino). */
    private final StringProperty boardLinkDescription = new SimpleStringProperty("Ir para o board de destino");
    /** Cadeado pequeno no canto de cima à esquerda, só na tela, enquanto o card está travado. */
    private final StackPane lockBadge = new StackPane();
    /** Seção: fundo translúcido (o body da seção não tem fundo, para o meio não pegar clique). */
    private final Region frameFill = new Region();
    /** Seção: anel invisível em volta da borda, a área que recebe o clique. */
    private final Region frameGrip = new Region();
    /** Seção: o título fora da edição (uma linha, cortada com reticências). */
    private final Label frameTitle = new Label();

    public CardView(Card card) {
        this(card, CardImageLoader.NONE);
    }

    public CardView(Card card, CardImageLoader imageLoader) {
        this.card = card;
        this.imageLoader = imageLoader == null ? CardImageLoader.NONE : imageLoader;
        getStyleClass().add("card-view");
        setPickOnBounds(false);

        textArea.getStyleClass().add("card-text");
        textArea.setWrapText(true);
        textArea.promptTextProperty().bind(Bindings.createStringBinding(
                () -> card.isFrame() ? FRAME_PROMPT_TEXT : card.hasImage() ? CAPTION_PROMPT_TEXT : PROMPT_TEXT,
                card.imageFileProperty(), card.shapeProperty()));
        textArea.setPrefRowCount(1);
        textArea.setPrefColumnCount(1);
        textArea.setMinSize(0, 0);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        textArea.textProperty().bindBidirectional(card.textProperty());

        // O recuo depende do formato e do tamanho, por isso é calculado aqui e
        // não no CSS (e .card-content não deve definir -fx-padding no app.css).
        content.getStyleClass().add("card-content");
        List<Observable> paddingDeps = new ArrayList<>(List.of(
                card.shapeProperty(), card.widthProperty(), card.heightProperty(),
                card.panelModeProperty(), card.childBoardProperty(),
                card.imageFileProperty(), card.baseWidthProperty(), card.baseHeightProperty()));
        paddingDeps.addAll(List.of(CardMarkers.dependencies(card)));
        content.paddingProperty().bind(Bindings.createObjectBinding(
                () -> CardGeometry.contentInsets(card.getShape(), card.getWidth(), card.getHeight(),
                        card.isPanel() || card.hasChildBoard(), card.imageHeight(card.getWidth()),
                        CardMarkers.of(card).size()),
                paddingDeps.toArray(new Observable[0])));
        content.getChildren().add(textArea);
        // A área ocupa o card todo (o recuo fica por dentro); só o texto deve
        // ser clicável aqui, o resto cai no body, que respeita a silhueta.
        content.setPickOnBounds(false);

        setUpImage();
        setUpFrame();

        body.getStyleClass().add("card");
        body.getChildren().addAll(frameFill, frameGrip, imageBox, frameTitle, content);
        body.prefWidthProperty().bind(card.widthProperty());
        body.prefHeightProperty().bind(card.heightProperty());
        body.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        body.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Cliques fora da silhueta (ex.: cantos da elipse) não pertencem ao card.
        body.setPickOnBounds(false);

        // Alça à direita do card: arrastar a partir dela cria uma conexão. Fica um
        // pouco para fora para não disputar lugar com a alça de redimensionar da borda.
        connectorHandle.getStyleClass().add("connector-handle");
        connectorHandle.centerXProperty().bind(card.widthProperty().add(CONNECTOR_OFFSET));
        connectorHandle.centerYProperty().bind(card.heightProperty().divide(2));

        // Botão do painel flutuante: só aparece se o card for um painel; o ícone indica o modo.
        panelButton.getStyleClass().addAll("tool-button", "panel-button");
        panelButton.setFocusTraversable(false);
        panelButton.setTooltip(new Tooltip("Abrir painel"));
        panelButton.visibleProperty().bind(card.panelModeProperty().isNotEqualTo(PanelMode.NONE));
        applyPanelMode(card.getPanelMode());
        card.panelModeProperty().addListener((obs, old, mode) -> applyPanelMode(mode));
        card.shapeProperty().addListener(obs -> requestLayout());

        // Botão do board aninhado: mesmo lugar do botão do painel (um card não é os dois).
        boardButton.getStyleClass().addAll("tool-button", "panel-button", "board-button");
        boardButton.setFocusTraversable(false);
        boardButton.setTooltip(new Tooltip("Entrar no board"));
        boardButton.setGraphic(Icons.create(Icons.ENTER));
        boardButton.visibleProperty().bind(card.childBoardProperty().isNotNull());
        pseudoClassStateChanged(HAS_BOARD, card.hasChildBoard());
        card.childBoardProperty().addListener((obs, old, child) -> pseudoClassStateChanged(HAS_BOARD, child != null));

        setUpMarkers();
        setUpLock();

        getChildren().addAll(body, panelButton, boardButton);
        getChildren().addAll(markers.values());
        getChildren().addAll(connectorHandle, lockBadge);

        layoutXProperty().bind(card.xProperty());
        layoutYProperty().bind(card.yProperty());

        applyColor(card.getColor());
        card.colorProperty().addListener((obs, old, color) -> applyColor(color));
        applyShape(null, card.getShape());
        card.shapeProperty().addListener((obs, old, shape) -> applyShape(old, shape));
    }

    public Card getCard() {
        return card;
    }

    /** Área "arrastável" do card (tudo exceto o texto e a alça de conexão). */
    public Region getBody() {
        return body;
    }

    public Circle getConnectorHandle() {
        return connectorHandle;
    }

    public Button getPanelButton() {
        return panelButton;
    }

    public Button getBoardButton() {
        return boardButton;
    }

    /** O botão do marcador ({@link CardMarkers}); só aparece quando o card tem esse marcador. */
    public Button getMarker(CardMarkers.Kind kind) {
        return markers.get(kind);
    }

    /** Indica se o nó faz parte de um marcador (clicar nele age, não seleciona nem arrasta o card). */
    public boolean isMarkerNode(Node node) {
        for (Node n = node; n != null && n != this; n = n.getParent()) {
            if (n instanceof Button button && markers.containsValue(button)) {
                return true;
            }
        }
        return false;
    }

    /** Destaca o botão enquanto o painel deste card está aberto. */
    public void setPanelOpen(boolean open) {
        pseudoClassStateChanged(PANEL_OPEN, open);
    }

    /** Indica se o nó faz parte do campo de texto (onde o mouse deve editar, não arrastar). */
    public boolean isTextNode(Node node) {
        for (Node n = node; n != null && n != this; n = n.getParent()) {
            if (n == textArea) {
                return true;
            }
        }
        return false;
    }

    /**
     * Encerra a edição do texto devolvendo o foco ao canvas (os atalhos voltam a
     * funcionar). Usado pelo Enter no título de uma seção, que é de uma linha.
     */
    public void endTextEdit() {
        for (Node n = getParent(); n != null; n = n.getParent()) {
            if (n instanceof BoardView boardView) {
                boardView.requestFocus();
                return;
            }
        }
        requestFocus();
    }

    /**
     * Se o ponto (coordenadas de cena) pega este card, respeitando a silhueta.
     * Numa seção, só a borda (o anel) e a faixa do título contam: o meio vazio
     * não é dela (o mesmo que o clique de verdade decide, ver a descrição da classe).
     */
    public boolean hitsScenePoint(double sceneX, double sceneY) {
        if (!card.isFrame()) {
            Point2D local = body.sceneToLocal(sceneX, sceneY);
            return local != null && body.contains(local);
        }
        for (Node part : List.of(frameGrip, frameTitle, textArea)) {
            if (!part.isVisible() || part.isMouseTransparent()) {
                continue;
            }
            Point2D local = part.sceneToLocal(sceneX, sceneY);
            if (local != null && (part == frameGrip ? part.contains(local) : part.getBoundsInLocal().contains(local))) {
                return true;
            }
        }
        return false;
    }

    /** O título de uma seção fora da edição (para o roteiro de interface e os testes). */
    public Label getFrameTitle() {
        return frameTitle;
    }

    /** O anel da borda de uma seção, que recebe o clique (para os testes de picking). */
    public Region getFrameGrip() {
        return frameGrip;
    }

    /** Verdadeiro enquanto o texto do card está sendo editado. */
    public ReadOnlyBooleanProperty textFocusedProperty() {
        return textArea.focusedProperty();
    }

    public void focusText() {
        Platform.runLater(textArea::requestFocus);
    }

    public boolean isTextFocused() {
        return textArea.isFocused();
    }

    /**
     * Tamanho da fonte do texto (calculado por {@link CardTextFit}). A família
     * vai junto para ser exatamente a mesma usada na medição.
     */
    public void setTextFontSize(double size) {
        textArea.setStyle(String.format(Locale.ROOT, "-fx-font-family: \"%s\"; -fx-font-size: %.1fpx;",
                CardTextFit.fontFamily(), size));
    }

    public void setSelected(boolean value) {
        pseudoClassStateChanged(SELECTED, value);
    }

    /** Destaca o card como destino de uma conexão sendo criada. */
    public void setConnectionTarget(boolean value) {
        pseudoClassStateChanged(CONNECTION_TARGET, value);
    }

    @Override
    protected void layoutChildren() {
        body.autosize();
        panelButton.autosize();
        Point2D center = CardGeometry.panelButtonCenter(card.getShape(), card.getWidth(), card.getHeight());
        panelButton.relocate(center.getX() - panelButton.getWidth() / 2, center.getY() - panelButton.getHeight() / 2);
        boardButton.autosize();
        boardButton.relocate(center.getX() - boardButton.getWidth() / 2, center.getY() - boardButton.getHeight() / 2);
        for (CardMarkers.Spot spot : CardMarkers.layout(card)) {
            Button marker = markers.get(spot.kind());
            marker.resize(spot.size(), spot.size());
            marker.relocate(spot.x(), spot.y());
        }
        lockBadge.resize(LOCK_BADGE_SIZE, LOCK_BADGE_SIZE);
        lockBadge.relocate(-LOCK_BADGE_OUTSIDE, -LOCK_BADGE_OUTSIDE);
    }

    /**
     * Card travado: pseudo-classe {@code :locked} (o CSS troca o cursor de mover
     * pelo normal) e um cadeado pequeno no canto de cima à esquerda, metade para
     * fora, longe dos marcadores (em cima, à direita ou no meio), do botão do
     * painel (embaixo) e da alça de conexão (à direita). Não recebe cliques e
     * não aparece no PDF nem no PNG ({@code .board-export} no app.css).
     */
    private void setUpLock() {
        lockBadge.getStyleClass().add("card-lock-badge");
        lockBadge.setMouseTransparent(true);
        lockBadge.setManaged(false);
        SVGPath icon = Icons.create(Icons.LOCK);
        icon.setScaleX(0.55);
        icon.setScaleY(0.55);
        lockBadge.getChildren().add(icon);
        applyLocked(card.isLocked());
        card.lockedProperty().addListener((obs, was, locked) -> applyLocked(locked));
    }

    private void applyLocked(boolean locked) {
        pseudoClassStateChanged(LOCKED, locked);
        lockBadge.setVisible(locked);
    }

    /** Se o cadeado aparece no card (para os roteiros de teste de interface). */
    public boolean isLockBadgeVisible() {
        return lockBadge.isVisible();
    }

    /**
     * Marcadores na borda de cima (clipe do link, seta do link para outro board). Um botão por tipo,
     * criado sempre e mostrado só quando o card tem aquele marcador; a posição
     * vem de {@link CardMarkers#layout}, a mesma conta usada pelo texto e pelo PDF.
     * O tamanho é fixado aqui, não no CSS (o app.css venceria o código, 4.5).
     */
    private void setUpMarkers() {
        for (CardMarkers.Kind kind : CardMarkers.Kind.values()) {
            Button marker = new Button();
            marker.getStyleClass().addAll("card-marker", "card-marker-" + kind.name().toLowerCase(Locale.ROOT));
            marker.setGraphic(Icons.marker(kind));
            marker.setFocusTraversable(false);
            marker.setMinSize(CardMarkers.SIZE, CardMarkers.SIZE);
            marker.setPrefSize(CardMarkers.SIZE, CardMarkers.SIZE);
            marker.setMaxSize(CardMarkers.SIZE, CardMarkers.SIZE);
            marker.setVisible(false);
            Tooltip tooltip = new Tooltip();
            tooltip.textProperty().bind(switch (kind) {
                case LINK -> Bindings.concat("Abrir no navegador:\n", card.linkProperty());
                // O caminho do destino depende da árvore inteira: quem sabe é o controller.
                case BOARD_LINK -> boardLinkDescription;
            });
            marker.setTooltip(tooltip);
            markers.put(kind, marker);
        }
        for (Observable dependency : CardMarkers.dependencies(card)) {
            dependency.addListener(obs -> applyMarkers());
        }
        applyMarkers();
    }

    /**
     * Marca o marcador como "quebrado" (o destino não existe mais): ícone
     * riscado e pseudo-classe {@code :broken} (esmaecido, borda de alerta, no
     * app.css). Quem sabe se o destino existe é o controller (ou o
     * {@link BoardRenderer}, no PDF e no PNG).
     */
    public void setMarkerBroken(CardMarkers.Kind kind, boolean broken) {
        Button marker = markers.get(kind);
        if (marker.getPseudoClassStates().contains(BROKEN) == broken) {
            return;
        }
        marker.pseudoClassStateChanged(BROKEN, broken);
        marker.setGraphic(Icons.marker(kind, broken));
    }

    public boolean isMarkerBroken(CardMarkers.Kind kind) {
        return markers.get(kind).getPseudoClassStates().contains(BROKEN);
    }

    /** Texto da dica do marcador do link para outro board (ex.: o caminho do destino). */
    public void setBoardLinkDescription(String text) {
        boardLinkDescription.set(text);
    }

    private void applyMarkers() {
        List<CardMarkers.Kind> shown = CardMarkers.of(card);
        markers.forEach((kind, marker) -> marker.setVisible(shown.contains(kind)));
        requestLayout();
    }

    /**
     * Seção (frame): fundo translúcido num nó à parte que não recebe clique,
     * anel da borda que recebe, e o título em uma linha. Tudo só aparece se o
     * card é uma seção; nos outros cards esses nós ficam escondidos.
     */
    private void setUpFrame() {
        frameFill.getStyleClass().add("frame-fill");
        frameFill.setMouseTransparent(true);
        frameFill.setMinSize(0, 0);

        frameGrip.getStyleClass().add("frame-grip");
        // Só o anel da borda (CSS: borda transparente de 10 px) conta para o clique.
        frameGrip.setPickOnBounds(false);
        frameGrip.setMinSize(0, 0);

        frameTitle.getStyleClass().add("frame-title");
        frameTitle.setPickOnBounds(true); // a faixa inteira do título pega o clique
        frameTitle.setMinSize(0, 0);
        frameTitle.setMaxWidth(Double.MAX_VALUE);
        frameTitle.setPrefHeight(Frames.TITLE_HEIGHT);
        frameTitle.setMaxHeight(Frames.TITLE_HEIGHT);
        frameTitle.setAlignment(Pos.CENTER_LEFT);
        frameTitle.setWrapText(false);
        StackPane.setAlignment(frameTitle, Pos.TOP_LEFT);
        // Mesmo recuo lateral do campo de texto (que já desconta os marcadores).
        frameTitle.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(0, content.getPadding().getRight(), 0, content.getPadding().getLeft()),
                content.paddingProperty()));
        frameTitle.textProperty().bind(Bindings.createStringBinding(() -> {
            String title = card.getText().replaceAll("\\s*\\R\\s*", " ").strip();
            return title.isEmpty() ? FRAME_PLACEHOLDER : title;
        }, card.textProperty()));
        card.textProperty().addListener(obs -> applyFrame());
        card.shapeProperty().addListener(obs -> applyFrame());
        textArea.focusedProperty().addListener(obs -> applyFrame());
        applyFrame();
    }

    private void applyFrame() {
        boolean frame = card.isFrame();
        boolean editing = textArea.isFocused();
        pseudoClassStateChanged(FRAME, frame);
        frameFill.setVisible(frame);
        frameGrip.setVisible(frame);
        frameTitle.setVisible(frame && !editing);
        frameTitle.pseudoClassStateChanged(PLACEHOLDER, card.getText().isBlank());
        // Fora da edição o campo do título fica invisível e sem clique: o clique
        // na faixa do título seleciona e arrasta a seção (o duplo clique edita).
        textArea.setMouseTransparent(frame && !editing);
        textArea.setOpacity(frame && !editing ? 0 : 1);
        textArea.setWrapText(!frame);
    }

    /**
     * Imagem no topo do card, na largura toda (dentro da borda de 1 px); a
     * altura segue a proporção da imagem ({@link Card#imageHeight}). O recorte
     * arredonda os cantos como os do card: os de baixo só aparecem arredondados
     * quando não há legenda (o recorte vai até o fim do card).
     */
    private void setUpImage() {
        imageBox.getStyleClass().add("card-image");
        imageBox.setMouseTransparent(true); // cliques vão para o body: arrastar, selecionar
        imageBox.managedProperty().bind(imageBox.visibleProperty());
        StackPane.setAlignment(imageBox, Pos.TOP_CENTER);

        DoubleBinding boxWidth = Bindings.createDoubleBinding(
                () -> Math.max(0, card.getWidth() - 2 * IMAGE_INSET), card.widthProperty());
        DoubleBinding boxHeight = Bindings.createDoubleBinding(
                () -> Math.max(0, card.imageHeight(card.getWidth()) - 2 * IMAGE_INSET),
                card.widthProperty(), card.baseWidthProperty(), card.baseHeightProperty(), card.imageFileProperty());
        imageBox.minWidthProperty().bind(boxWidth);
        imageBox.prefWidthProperty().bind(boxWidth);
        imageBox.maxWidthProperty().bind(boxWidth);
        imageBox.minHeightProperty().bind(boxHeight);
        imageBox.prefHeightProperty().bind(boxHeight);
        imageBox.maxHeightProperty().bind(boxHeight);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(boxWidth);
        imageView.fitHeightProperty().bind(boxHeight);

        missingImage.getStyleClass().add("card-image-missing");
        missingImage.setWrapText(true);
        missingImage.setMinSize(0, 0);
        missingImage.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(IMAGE_CORNER * 2);
        clip.setArcHeight(IMAGE_CORNER * 2);
        clip.widthProperty().bind(boxWidth);
        clip.heightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, card.getHeight() - 2 * IMAGE_INSET), card.heightProperty()));
        imageBox.setClip(clip);

        imageBox.getChildren().addAll(imageView, missingImage);
        applyImage(card.getImageFile());
        card.imageFileProperty().addListener((obs, old, name) -> applyImage(name));
    }

    private void applyImage(String fileName) {
        boolean hasImage = fileName != null;
        pseudoClassStateChanged(HAS_IMAGE, hasImage);
        imageBox.setVisible(hasImage);
        Image image = hasImage ? imageLoader.load(fileName) : null;
        imageView.setImage(image);
        // Arquivo que sumiu ou não abre: o card continua (e a referência também), com um aviso.
        missingImage.setVisible(hasImage && image == null);
        missingImage.setTooltip(hasImage && image == null ? new Tooltip(fileName) : null);
    }

    /** Se o card mostra o aviso de imagem não encontrada no lugar da imagem. */
    public boolean isImageMissing() {
        return missingImage.isVisible();
    }

    private void applyPanelMode(PanelMode mode) {
        String icon = Icons.panelMode(mode);
        panelButton.setGraphic(icon == null ? null : Icons.create(icon));
    }

    /**
     * A cor vem do modelo, mas o estilo continua no CSS: aqui só definimos a
     * variável {@code -card-color}, que o app.css usa como fundo do card.
     */
    private void applyColor(String color) {
        String safe = isValidColor(color) ? color : Card.DEFAULT_COLOR;
        body.setStyle("-card-color: " + safe + ";");
    }

    private void applyShape(CardShape old, CardShape shape) {
        if (old != null) {
            body.getStyleClass().remove(shapeClass(old));
        }
        body.getStyleClass().add(shapeClass(shape));
    }

    private static String shapeClass(CardShape shape) {
        return SHAPE_CLASS_PREFIX + shape.name().toLowerCase();
    }

    private static boolean isValidColor(String color) {
        try {
            Color.web(color);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
