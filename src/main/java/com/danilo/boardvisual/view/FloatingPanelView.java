package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.ListItem;
import com.danilo.boardvisual.model.PanelMode;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Painel aberto a partir de um card do tipo painel flutuante.
 *
 * <pre>
 * ┌───────────────────────────────┐
 * │ [ícone] Título do card    [✕] │  cabeçalho (o título é o texto do card)
 * ├───────────────────────────────┤
 * │ modo Texto: caixa de texto    │
 * │ modo Lista: • item      [✕]   │
 * │             • item      [✕]   │
 * │             [+] Adicionar...  │
 * └───────────────────────────────┘
 * </pre>
 *
 * Faz binding direto com o card (texto e itens); ações que mudam a estrutura
 * (adicionar/remover item, fechar) são repassadas ao controller. O corpo é
 * reconstruído quando o modo muda, inclusive por desfazer.
 */
public class FloatingPanelView extends VBox {

    private final Card card;
    private final Label modeIcon = new Label();
    private final StackPane bodyHolder = new StackPane();

    private Runnable onClose = () -> { };
    private Consumer<String> onAddItem = text -> { };
    private Consumer<ListItem> onRemoveItem = item -> { };

    /** Modo apresentação: só leitura (sem adicionar nem remover itens, campos sem edição). */
    private boolean readOnly;
    /** Campo que recebe o foco ao abrir (texto detalhado ou "adicionar item"); {@code null} se só leitura. */
    private Node primaryField;
    private VBox rowsBox;
    private TextField newItemField;

    private final ChangeListener<PanelMode> modeListener = (obs, old, mode) -> rebuildBody();
    private final ListChangeListener<ListItem> itemsListener = change -> rebuildRows();

    public FloatingPanelView(Card card) {
        this.card = card;
        getStyleClass().addAll("floating-panel", "card-panel");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Rolar dentro do painel não deve dar zoom no canvas por trás.
        addEventHandler(ScrollEvent.ANY, Event::consume);

        getChildren().addAll(createHeader(), bodyHolder);
        VBox.setVgrow(bodyHolder, Priority.ALWAYS);

        card.panelModeProperty().addListener(modeListener);
        card.getListItems().addListener(itemsListener);
        rebuildBody();
    }

    private HBox createHeader() {
        modeIcon.getStyleClass().add("card-panel-icon");

        Label title = new Label();
        title.getStyleClass().add("card-panel-title");
        title.textProperty().bind(Bindings.createStringBinding(() -> {
            String text = card.getText().strip();
            int lineBreak = text.indexOf('\n');
            String firstLine = lineBreak < 0 ? text : text.substring(0, lineBreak);
            return firstLine.isEmpty() ? "Sem título" : firstLine;
        }, card.textProperty()));
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        Button close = new Button();
        close.getStyleClass().addAll("tool-button", "card-panel-close");
        close.setGraphic(Icons.create(Icons.CLOSE));
        close.setTooltip(new Tooltip("Fechar  (Esc)"));
        close.setFocusTraversable(false);
        close.setOnAction(e -> onClose.run());

        HBox header = new HBox(modeIcon, title, close);
        header.getStyleClass().add("card-panel-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void rebuildBody() {
        String icon = Icons.panelMode(card.getPanelMode());
        modeIcon.setGraphic(icon == null ? null : Icons.create(icon));
        rowsBox = null;
        newItemField = null;
        Node body = card.getPanelMode() == PanelMode.LIST ? createListBody() : createTextBody();
        bodyHolder.getChildren().setAll(body);
    }

    // ------------------------------------------------------------ modo texto

    private Node createTextBody() {
        TextArea detail = new TextArea();
        detail.getStyleClass().add("card-panel-detail");
        detail.setWrapText(true);
        detail.setPromptText(readOnly ? "" : "Escreva os detalhes desta ideia...");
        detail.setPrefRowCount(10);
        detail.textProperty().bindBidirectional(card.detailTextProperty());
        if (readOnly) {
            detail.setEditable(false);
            detail.setFocusTraversable(false);
        }
        primaryField = readOnly ? null : detail;
        return detail;
    }

    // ------------------------------------------------------------ modo lista

    private Node createListBody() {
        rowsBox = new VBox();
        rowsBox.getStyleClass().add("card-panel-rows");

        ScrollPane scroll = new ScrollPane(rowsBox);
        scroll.getStyleClass().add("card-panel-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        newItemField = new TextField();
        newItemField.getStyleClass().add("card-panel-new-item");
        newItemField.setPromptText("Adicionar item e Enter...");
        newItemField.setOnAction(e -> addFromField());
        HBox.setHgrow(newItemField, Priority.ALWAYS);

        Button add = new Button();
        add.getStyleClass().addAll("tool-button", "card-panel-add");
        add.setGraphic(Icons.create(Icons.ADD));
        add.setTooltip(new Tooltip("Adicionar item"));
        add.setFocusTraversable(false);
        add.setOnAction(e -> addFromField());

        HBox addRow = new HBox(add, newItemField);
        addRow.getStyleClass().add("card-panel-add-row");
        addRow.setAlignment(Pos.CENTER_LEFT);

        rebuildRows();
        primaryField = readOnly ? null : newItemField;
        // Só leitura: sem a linha de "adicionar item" (e sem o campo, para o Enter de um item não ir até ele).
        VBox list = readOnly ? new VBox(scroll) : new VBox(scroll, addRow);
        if (readOnly) {
            newItemField = null;
        }
        list.getStyleClass().add("card-panel-list");
        return list;
    }

    private void addFromField() {
        String text = newItemField.getText().strip();
        if (!text.isEmpty()) {
            onAddItem.accept(text);
            newItemField.clear();
        }
        newItemField.requestFocus();
    }

    private void rebuildRows() {
        if (rowsBox == null) {
            return;
        }
        rowsBox.getChildren().clear();
        for (ListItem item : card.getListItems()) {
            rowsBox.getChildren().add(createRow(item));
        }
        if (card.getListItems().isEmpty()) {
            Label empty = new Label("Nenhum item ainda");
            empty.getStyleClass().add("card-panel-empty");
            rowsBox.getChildren().add(empty);
        }
    }

    private HBox createRow(ListItem item) {
        Label bullet = new Label("•");
        bullet.getStyleClass().add("card-panel-bullet");

        TextField field = new TextField();
        field.getStyleClass().add("card-panel-item");
        field.textProperty().bindBidirectional(item.textProperty());
        // Enter num item leva para o campo de novo item (digitação contínua).
        field.setOnAction(e -> {
            if (newItemField != null) {
                newItemField.requestFocus();
            }
        });
        HBox.setHgrow(field, Priority.ALWAYS);

        Button remove = new Button();
        remove.getStyleClass().addAll("tool-button", "card-panel-remove");
        remove.setGraphic(Icons.create(Icons.CLOSE));
        remove.setTooltip(new Tooltip("Remover item"));
        remove.setFocusTraversable(false);
        remove.setOnAction(e -> onRemoveItem.accept(item));

        if (readOnly) {
            field.setEditable(false);
            field.setFocusTraversable(false);
        }
        HBox row = readOnly ? new HBox(bullet, field) : new HBox(bullet, field, remove);
        row.getStyleClass().add("card-panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ------------------------------------------------------------ API

    public Card getCard() {
        return card;
    }

    /**
     * Painel só de leitura (modo apresentação): os textos não se editam e somem o "adicionar item" e o
     * ✕ de cada item. Reconstrói o corpo.
     */
    public void setReadOnly(boolean readOnly) {
        if (this.readOnly != readOnly) {
            this.readOnly = readOnly;
            getStyleClass().remove("read-only");
            if (readOnly) {
                getStyleClass().add("read-only");
            }
            rebuildBody();
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Coloca o cursor no campo principal do modo atual (nada, se o painel é só de leitura). */
    public void focusPrimaryField() {
        Platform.runLater(() -> {
            if (primaryField != null) {
                primaryField.requestFocus();
            }
        });
    }

    public void setOnClose(Runnable handler) {
        onClose = handler == null ? () -> { } : handler;
    }

    public void setOnAddItem(Consumer<String> handler) {
        onAddItem = handler == null ? text -> { } : handler;
    }

    public void setOnRemoveItem(Consumer<ListItem> handler) {
        onRemoveItem = handler == null ? item -> { } : handler;
    }

    /** Desliga os listeners do card (o painel é descartado ao fechar). */
    public void dispose() {
        card.panelModeProperty().removeListener(modeListener);
        card.getListItems().removeListener(itemsListener);
    }
}
