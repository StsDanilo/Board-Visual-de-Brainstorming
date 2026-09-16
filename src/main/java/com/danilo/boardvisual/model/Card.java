package com.danilo.boardvisual.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * Um card do board: posição, tamanho, conteúdo e aparência.
 *
 * Não sabe nada sobre como é desenhado. Os campos são JavaFX properties para
 * que a view faça binding direto neles: mover um card é só alterar x/y aqui,
 * e tudo que depende disso (o próprio card na tela, as setas) se atualiza.
 */
public class Card {

    public static final double DEFAULT_WIDTH = 200;
    public static final double DEFAULT_HEIGHT = 120;
    /** Cor de fundo em hex CSS. Cores/categorias entram na próxima etapa. */
    public static final String DEFAULT_COLOR = "#FFFFFF";

    private final String id;
    private final DoubleProperty x = new SimpleDoubleProperty(this, "x");
    private final DoubleProperty y = new SimpleDoubleProperty(this, "y");
    private final DoubleProperty width = new SimpleDoubleProperty(this, "width", DEFAULT_WIDTH);
    private final DoubleProperty height = new SimpleDoubleProperty(this, "height", DEFAULT_HEIGHT);
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StringProperty color = new SimpleStringProperty(this, "color", DEFAULT_COLOR);
    private final ObjectProperty<CardShape> shape = new SimpleObjectProperty<>(this, "shape", CardShape.RECTANGLE);

    public Card(double x, double y) {
        this(UUID.randomUUID().toString(), x, y);
    }

    public Card(String id, double x, double y) {
        this.id = Objects.requireNonNull(id, "id");
        setX(x);
        setY(y);
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

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value == null ? "" : value); }

    public StringProperty colorProperty() { return color; }
    public String getColor() { return color.get(); }
    public void setColor(String value) { color.set(value == null ? DEFAULT_COLOR : value); }

    public ObjectProperty<CardShape> shapeProperty() { return shape; }
    public CardShape getShape() { return shape.get(); }
    public void setShape(CardShape value) { shape.set(value == null ? CardShape.RECTANGLE : value); }

    /** Cópia com id novo e mesmo tamanho, texto e aparência, na mesma posição. */
    public Card copy() {
        Card copy = new Card(getX(), getY());
        copy.setWidth(getWidth());
        copy.setHeight(getHeight());
        copy.setText(getText());
        copy.setColor(getColor());
        copy.setShape(getShape());
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
