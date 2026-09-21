package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.PanelMode;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.Locale;

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
 *  │   └─ content             área do texto, recuada para caber no formato
 *  │       └─ textArea
 *  ├─ panelButton             abre o painel flutuante (só em cards com painel)
 *  ├─ boardButton             entra no board filho (só em cards com board aninhado)
 *  └─ connectorHandle         alça para criar conexões
 * </pre>
 */
public class CardView extends Region {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass CONNECTION_TARGET = PseudoClass.getPseudoClass("connection-target");
    private static final PseudoClass PANEL_OPEN = PseudoClass.getPseudoClass("panel-open");
    private static final PseudoClass HAS_BOARD = PseudoClass.getPseudoClass("has-board");
    private static final String SHAPE_CLASS_PREFIX = "shape-";
    /** Texto de exemplo do card vazio (também usado para ajustar a fonte automática). */
    public static final String PROMPT_TEXT = "Escreva uma ideia...";
    /** Distância da alça de conexão para fora da borda direita. */
    private static final double CONNECTOR_OFFSET = 14;

    private final Card card;
    private final StackPane body = new StackPane();
    private final VBox content = new VBox();
    private final TextArea textArea = new TextArea();
    private final Circle connectorHandle = new Circle(6);
    private final Button panelButton = new Button();
    private final Button boardButton = new Button();

    public CardView(Card card) {
        this.card = card;
        getStyleClass().add("card-view");
        setPickOnBounds(false);

        textArea.getStyleClass().add("card-text");
        textArea.setWrapText(true);
        textArea.setPromptText(PROMPT_TEXT);
        textArea.setPrefRowCount(1);
        textArea.setPrefColumnCount(1);
        textArea.setMinSize(0, 0);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        textArea.textProperty().bindBidirectional(card.textProperty());

        // O recuo depende do formato e do tamanho, por isso é calculado aqui e
        // não no CSS (e .card-content não deve definir -fx-padding no app.css).
        content.getStyleClass().add("card-content");
        content.paddingProperty().bind(Bindings.createObjectBinding(
                () -> CardGeometry.contentInsets(card.getShape(), card.getWidth(), card.getHeight(),
                        card.isPanel() || card.hasChildBoard()),
                card.shapeProperty(), card.widthProperty(), card.heightProperty(),
                card.panelModeProperty(), card.childBoardProperty()));
        content.getChildren().add(textArea);
        // A área ocupa o card todo (o recuo fica por dentro); só o texto deve
        // ser clicável aqui, o resto cai no body, que respeita a silhueta.
        content.setPickOnBounds(false);

        body.getStyleClass().add("card");
        body.getChildren().add(content);
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

        getChildren().addAll(body, panelButton, boardButton, connectorHandle);

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

    /** Verdadeiro enquanto o texto do card está sendo editado. */
    public ReadOnlyBooleanProperty textFocusedProperty() {
        return textArea.focusedProperty();
    }

    public void focusText() {
        Platform.runLater(textArea::requestFocus);
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
