package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Representação visual de um {@link Card}.
 *
 * Só faz binding com o modelo: posição, tamanho, texto, cor e formato vêm do
 * card. Não trata eventos de mouse; os controllers se penduram nos nós
 * expostos aqui ({@link #getBody()}, {@link #getConnectorHandle()}).
 */
public class CardView extends Region {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass CONNECTION_TARGET = PseudoClass.getPseudoClass("connection-target");
    private static final String SHAPE_CLASS_PREFIX = "shape-";

    private final Card card;
    private final VBox body = new VBox();
    private final TextArea textArea = new TextArea();
    private final Circle connectorHandle = new Circle(6);

    public CardView(Card card) {
        this.card = card;
        getStyleClass().add("card-view");
        setPickOnBounds(false);

        Region header = new Region();
        header.getStyleClass().add("card-header");

        textArea.getStyleClass().add("card-text");
        textArea.setWrapText(true);
        textArea.setPromptText("Escreva uma ideia...");
        textArea.setPrefRowCount(1);
        textArea.setPrefColumnCount(1);
        textArea.setMinSize(0, 0);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        textArea.textProperty().bindBidirectional(card.textProperty());

        body.getStyleClass().add("card");
        body.getChildren().addAll(header, textArea);
        body.prefWidthProperty().bind(card.widthProperty());
        body.prefHeightProperty().bind(card.heightProperty());
        body.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        body.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // Alça na borda direita: arrastar a partir dela cria uma conexão.
        connectorHandle.getStyleClass().add("connector-handle");
        connectorHandle.centerXProperty().bind(card.widthProperty());
        connectorHandle.centerYProperty().bind(card.heightProperty().divide(2));

        getChildren().addAll(body, connectorHandle);

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

    /** Indica se o nó faz parte do campo de texto (onde o mouse deve editar, não arrastar). */
    public boolean isTextNode(Node node) {
        for (Node n = node; n != null && n != this; n = n.getParent()) {
            if (n == textArea) {
                return true;
            }
        }
        return false;
    }

    public void focusText() {
        Platform.runLater(textArea::requestFocus);
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
