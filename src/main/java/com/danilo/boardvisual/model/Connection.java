package com.danilo.boardvisual.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * Uma seta direcionada ligando dois cards do mesmo board.
 *
 * Origem, destino e id são fixos: "mudar a ponta" de uma seta é excluí-la e
 * criar outra. O texto ({@link #labelProperty()}) e o estilo
 * ({@link #styleProperty()}) mudam, e a tela acompanha por binding.
 */
public class Connection {

    /** Limite do texto digitado na seta (o campo de edição não aceita mais que isso). */
    public static final int MAX_LABEL_LENGTH = 60;

    private final String id;
    private final Card source;
    private final Card target;

    private final StringProperty label = new SimpleStringProperty(this, "label", "");
    private final ObjectProperty<ConnectionStyle> style =
            new SimpleObjectProperty<>(this, "style", ConnectionStyle.DEFAULT);

    public Connection(Card source, Card target) {
        this(UUID.randomUUID().toString(), source, target);
    }

    public Connection(String id, Card source, Card target) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
    }

    public String getId() {
        return id;
    }

    public Card getSource() {
        return source;
    }

    public Card getTarget() {
        return target;
    }

    /** Texto no meio da seta ("" = seta sem texto). Sempre uma linha só. */
    public StringProperty labelProperty() {
        return label;
    }

    public String getLabel() {
        return label.get();
    }

    /**
     * Define o texto. {@code null} vira "" e quebras de linha (de um arquivo
     * editado à mão) viram espaço: o texto da seta é de uma linha.
     */
    public void setLabel(String value) {
        label.set(normalizeLabel(value));
    }

    public boolean hasLabel() {
        return !getLabel().isBlank();
    }

    /** O texto como a seta guarda: sem {@code null} e sem quebras de linha. */
    public static String normalizeLabel(String value) {
        return value == null ? "" : value.replaceAll("\\R", " ");
    }

    public ObjectProperty<ConnectionStyle> styleProperty() {
        return style;
    }

    public ConnectionStyle getStyle() {
        return style.get();
    }

    /** {@code null} vira o estilo padrão. */
    public void setStyle(ConnectionStyle value) {
        style.set(value == null ? ConnectionStyle.DEFAULT : value);
    }

    @Override
    public String toString() {
        return "Connection[" + source.getId() + " -> " + target.getId() + "]";
    }
}
