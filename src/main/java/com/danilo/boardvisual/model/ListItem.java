package com.danilo.boardvisual.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Um item da lista de um painel flutuante.
 *
 * É um objeto (e não uma String solta) para que cada linha da tela possa
 * fazer binding no próprio texto: editar um item não recria a lista.
 */
public class ListItem {

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    public ListItem(String text) {
        setText(text);
    }

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value == null ? "" : value); }
}
