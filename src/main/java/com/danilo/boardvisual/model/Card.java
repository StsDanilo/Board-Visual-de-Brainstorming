package com.danilo.boardvisual.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um card do board: posição, tamanho, conteúdo e aparência.
 *
 * Não sabe nada sobre como é desenhado. Os campos são JavaFX properties para
 * que a view faça binding direto neles: mover um card é só alterar x/y aqui,
 * e tudo que depende disso (o próprio card na tela, as setas) se atualiza.
 *
 * Um card pode ser um "painel flutuante" ({@link #getPanelMode()} diferente de
 * NONE): além do texto que aparece no board, guarda um conteúdo maior (texto
 * detalhado ou lista) exibido num painel ao clicar no botão do card.
 *
 * Ou pode conter um board filho ({@link #getChildBoard()}): o botão do card
 * entra nesse board, que pode ter outros cards com boards filhos, sem limite.
 * O nome desse board é o texto do card.
 *
 * Tamanho: {@link #getBaseWidth()}/{@link #getBaseHeight()} guardam o tamanho
 * definido à mão (ao criar ou redimensionar); {@link #getWidth()}/{@link #getHeight()}
 * são o tamanho exibido, que pode ser maior quando o texto não cabe (o card
 * cresce sozinho e volta ao tamanho definido à mão quando o texto diminui).
 * Esse ajuste é feito fora do modelo, porque depende de medir o texto na tela.
 */
public class Card {

    /** Tamanho padrão de um card sem formato definido (o do retângulo). */
    public static final double DEFAULT_WIDTH = 200;
    public static final double DEFAULT_HEIGHT = 120;
    /** Cor de fundo padrão, em hex CSS. */
    public static final String DEFAULT_COLOR = "#FFFFFF";
    /** Valor de {@link #getFontSize()} que indica fonte automática (ajustada ao card). */
    public static final double AUTO_FONT_SIZE = 0;

    private final String id;
    private final DoubleProperty x = new SimpleDoubleProperty(this, "x");
    private final DoubleProperty y = new SimpleDoubleProperty(this, "y");
    private final DoubleProperty width = new SimpleDoubleProperty(this, "width", DEFAULT_WIDTH);
    private final DoubleProperty height = new SimpleDoubleProperty(this, "height", DEFAULT_HEIGHT);
    private final DoubleProperty baseWidth = new SimpleDoubleProperty(this, "baseWidth", DEFAULT_WIDTH);
    private final DoubleProperty baseHeight = new SimpleDoubleProperty(this, "baseHeight", DEFAULT_HEIGHT);
    private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize", AUTO_FONT_SIZE);
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StringProperty color = new SimpleStringProperty(this, "color", DEFAULT_COLOR);
    private final ObjectProperty<CardShape> shape = new SimpleObjectProperty<>(this, "shape", CardShape.RECTANGLE);

    private final ObjectProperty<PanelMode> panelMode = new SimpleObjectProperty<>(this, "panelMode", PanelMode.NONE);
    private final StringProperty detailText = new SimpleStringProperty(this, "detailText", "");
    private final ObservableList<ListItem> listItems = FXCollections.observableArrayList();
    private final ObservableList<ListItem> readOnlyListItems = FXCollections.unmodifiableObservableList(listItems);

    private final ObjectProperty<Board> childBoard = new SimpleObjectProperty<>(this, "childBoard");

    public Card(double x, double y) {
        this(UUID.randomUUID().toString(), x, y);
    }

    public Card(String id, double x, double y) {
        this.id = Objects.requireNonNull(id, "id");
        setX(x);
        setY(y);
    }

    /** Cria um card do formato informado, no tamanho padrão dele, centralizado no ponto. */
    public static Card create(CardShape shape, double centerX, double centerY) {
        Card card = new Card(centerX - shape.getDefaultWidth() / 2, centerY - shape.getDefaultHeight() / 2);
        card.setShape(shape);
        card.resize(shape.getDefaultWidth(), shape.getDefaultHeight());
        return card;
    }

    public String getId() {
        return id;
    }

    public DoubleProperty xProperty() { return x; }
    public double getX() { return x.get(); }
    public void setX(double value) { x.set(value); }

    public DoubleProperty yProperty() { return y; }
    public double getY() { return y.get(); }
    public void setY(double value) { y.set(value); }

    public DoubleProperty widthProperty() { return width; }
    public double getWidth() { return width.get(); }
    public void setWidth(double value) { width.set(value); }

    public DoubleProperty heightProperty() { return height; }
    public double getHeight() { return height.get(); }
    public void setHeight(double value) { height.set(value); }

    /** Tamanho definido à mão (ver a descrição da classe). */
    public DoubleProperty baseWidthProperty() { return baseWidth; }
    public double getBaseWidth() { return baseWidth.get(); }
    public void setBaseWidth(double value) { baseWidth.set(value); }

    public DoubleProperty baseHeightProperty() { return baseHeight; }
    public double getBaseHeight() { return baseHeight.get(); }
    public void setBaseHeight(double value) { baseHeight.set(value); }

    /** Define o tamanho à mão: muda o tamanho definido à mão e o exibido. */
    public void resize(double newWidth, double newHeight) {
        setWidth(newWidth);
        setHeight(newHeight);
        setBaseWidth(newWidth);
        setBaseHeight(newHeight);
    }

    /** Tamanho da fonte do texto, em px; {@link #AUTO_FONT_SIZE} = automático. */
    public DoubleProperty fontSizeProperty() { return fontSize; }
    public double getFontSize() { return fontSize.get(); }
    public void setFontSize(double value) { fontSize.set(value > 0 ? value : AUTO_FONT_SIZE); }

    public boolean isAutoFontSize() {
        return getFontSize() == AUTO_FONT_SIZE;
    }

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value == null ? "" : value); }

    public StringProperty colorProperty() { return color; }
    public String getColor() { return color.get(); }
    public void setColor(String value) { color.set(value == null ? DEFAULT_COLOR : value); }

    public ObjectProperty<CardShape> shapeProperty() { return shape; }
    public CardShape getShape() { return shape.get(); }
    public void setShape(CardShape value) { shape.set(value == null ? CardShape.RECTANGLE : value); }

    // ------------------------------------------------------ painel flutuante

    public ObjectProperty<PanelMode> panelModeProperty() { return panelMode; }
    public PanelMode getPanelMode() { return panelMode.get(); }
    public void setPanelMode(PanelMode value) { panelMode.set(value == null ? PanelMode.NONE : value); }

    public boolean isPanel() {
        return getPanelMode() != PanelMode.NONE;
    }

    /** Texto maior do modo {@link PanelMode#TEXT}. */
    public StringProperty detailTextProperty() { return detailText; }
    public String getDetailText() { return detailText.get(); }
    public void setDetailText(String value) { detailText.set(value == null ? "" : value); }

    /** Itens do modo {@link PanelMode#LIST} (somente leitura; altere pelos métodos abaixo). */
    public ObservableList<ListItem> getListItems() {
        return readOnlyListItems;
    }

    public ListItem addListItem(String text) {
        ListItem item = new ListItem(text);
        listItems.add(item);
        return item;
    }

    public void removeListItem(ListItem item) {
        listItems.remove(item);
    }

    /** Textos dos itens, na ordem. */
    public List<String> getListItemTexts() {
        return listItems.stream().map(ListItem::getText).toList();
    }

    /**
     * Define os itens pelos textos. Se a quantidade não muda, atualiza os
     * itens existentes no lugar (a tela não precisa recriar as linhas).
     */
    public void setListItemTexts(List<String> texts) {
        if (texts.size() == listItems.size()) {
            for (int i = 0; i < texts.size(); i++) {
                listItems.get(i).setText(texts.get(i));
            }
        } else {
            listItems.setAll(texts.stream().map(ListItem::new).toList());
        }
    }

    /** Se o painel tem algo escrito (texto detalhado ou algum item não vazio). */
    public boolean hasPanelContent() {
        return !getDetailText().isBlank()
                || listItems.stream().anyMatch(item -> !item.getText().isBlank());
    }

    /**
     * Troca o modo do painel. O conteúdo do modo anterior é apagado: texto
     * detalhado e lista não se convertem um no outro.
     */
    public void changePanelMode(PanelMode newMode) {
        if (newMode == null || newMode == getPanelMode()) {
            return;
        }
        setPanelMode(newMode);
        if (newMode != PanelMode.TEXT) {
            setDetailText("");
        }
        if (newMode != PanelMode.LIST) {
            listItems.clear();
        }
    }

    // ------------------------------------------------------ board aninhado

    /** Board filho aberto pelo botão do card, ou {@code null} se o card não contém um board. */
    public ObjectProperty<Board> childBoardProperty() { return childBoard; }
    public Board getChildBoard() { return childBoard.get(); }
    public void setChildBoard(Board value) { childBoard.set(value); }

    public boolean hasChildBoard() {
        return getChildBoard() != null;
    }

    // ---------------------------------------------------------------- formato

    /**
     * Troca o formato mantendo o card no mesmo centro. Se o tamanho definido à
     * mão ainda é o padrão do formato antigo, passa ao padrão do novo (a área
     * de texto de uma elipse ou losango é menor que a de um retângulo).
     */
    public void changeShape(CardShape newShape) {
        CardShape oldShape = getShape();
        if (newShape == null || newShape == oldShape) {
            return;
        }
        boolean hasDefaultSize = getBaseWidth() == oldShape.getDefaultWidth()
                && getBaseHeight() == oldShape.getDefaultHeight();
        double centerX = getCenterX();
        double centerY = getCenterY();
        setShape(newShape);
        if (hasDefaultSize) {
            resize(newShape.getDefaultWidth(), newShape.getDefaultHeight());
            setX(centerX - getWidth() / 2);
            setY(centerY - getHeight() / 2);
        }
    }

    /** Cópia com id novo e mesmo tamanho, texto e aparência, na mesma posição. */
    public Card copy() {
        Card copy = new Card(getX(), getY());
        copy.setWidth(getWidth());
        copy.setHeight(getHeight());
        copy.setBaseWidth(getBaseWidth());
        copy.setBaseHeight(getBaseHeight());
        copy.setFontSize(getFontSize());
        copy.setText(getText());
        copy.setColor(getColor());
        copy.setShape(getShape());
        copy.setPanelMode(getPanelMode());
        copy.setDetailText(getDetailText());
        copy.setListItemTexts(getListItemTexts());
        if (hasChildBoard()) {
            copy.setChildBoard(getChildBoard().deepCopy());
        }
        return copy;
    }

    public double getCenterX() {
        return getX() + getWidth() / 2;
    }

    public double getCenterY() {
        return getY() + getHeight() / 2;
    }

    @Override
    public String toString() {
        return "Card[" + id + "]";
    }
}
