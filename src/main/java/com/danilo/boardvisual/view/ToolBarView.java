package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.ConnectionStyle;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Barra de ferramentas vertical, flutuando sobre o canvas.
 *
 * - Ferramentas: a fonte da verdade é {@link #activeToolProperty()}; clicar
 *   num botão muda a propriedade, e mudar a propriedade por código (ex.:
 *   voltar para "Selecionar" depois de criar um card) atualiza os botões.
 * - Estilo das setas: ao lado da ferramenta Conexão, botões com os outros
 *   estilos ({@link #createConnectionRow}); o ícone da ferramenta mostra o
 *   estilo das setas novas ({@link #connectionStyleProperty()}).
 * - Cor: botão que abre a paleta. A cor escolhida vira a cor atual (usada
 *   nos próximos cards) e é repassada a {@link #setOnColorPicked}, para o
 *   controller aplicar também na seleção.
 */
public class ToolBarView extends VBox {

    private final ObjectProperty<Tool> activeTool = new SimpleObjectProperty<>(this, "activeTool", Tool.SELECT);
    private final ToggleGroup group = new ToggleGroup();
    private final Map<Tool, ToggleButton> buttons = new EnumMap<>(Tool.class);

    private final ReadOnlyStringWrapper currentColor = new ReadOnlyStringWrapper(this, "currentColor", Card.DEFAULT_COLOR);
    private final ColorPickerButton colorButton = new ColorPickerButton("Cor (novos cards e seleção)", Side.RIGHT);
    private Consumer<String> onColorPicked = color -> { };

    private final ConnectionStyleOrder styleOrder = new ConnectionStyleOrder();
    private final List<Button> styleButtons = new ArrayList<>();
    private final ReadOnlyObjectWrapper<ConnectionStyle> connectionStyle =
            new ReadOnlyObjectWrapper<>(this, "connectionStyle", ConnectionStyle.DEFAULT);
    private final BooleanProperty connectionSelected = new SimpleBooleanProperty(this, "connectionSelected");
    private Predicate<ConnectionStyle> onConnectionStylePicked = style -> false;

    public ToolBarView() {
        getStyleClass().add("tool-bar-view");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        for (Tool tool : Tool.values()) {
            ToggleButton button = createToolButton(tool);
            buttons.put(tool, button);
            getChildren().add(tool == Tool.CONNECTION ? createConnectionRow(button) : button);
        }

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // Clicar na ferramenta já ativa não deve deixar a barra sem nenhuma.
                oldToggle.setSelected(true);
            } else {
                activeTool.set((Tool) newToggle.getUserData());
            }
        });
        activeTool.addListener((obs, old, tool) -> buttons.get(tool).setSelected(true));
        buttons.get(activeTool.get()).setSelected(true);

        getChildren().addAll(new Separator(), createColorButton());
    }

    private ToggleButton createToolButton(Tool tool) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("tool-button");
        button.setGraphic(Icons.create(tool.getIconPath()));
        button.setTooltip(new Tooltip(tool.getLabel() + "  (" + tool.getShortcut().getName() + ")"));
        button.setUserData(tool);
        button.setToggleGroup(group);
        // Não roubar o foco do texto de um card ao trocar de ferramenta.
        button.setFocusTraversable(false);
        return button;
    }

    // ------------------------------------------------------ estilos de seta

    /**
     * A ferramenta Conexão com os botões de estilo ao lado. O ícone da
     * ferramenta mostra o estilo principal (o das setas novas); os botões
     * extras, os outros estilos, cada um desenhado no próprio estilo. Eles
     * aparecem enquanto a ferramenta Conexão está ativa, o mouse está sobre
     * ela (ou sobre eles) ou há uma seta selecionada. Clicar num extra troca de
     * lugar com o principal ({@link ConnectionStyleOrder}) e:
     * <ul>
     * <li>sem seta selecionada, ativa a ferramenta Conexão;</li>
     * <li>com seta selecionada, aplica o estilo nela ({@link #setOnConnectionStylePicked})
     *     e a ferramenta não muda. Nesse caso o ícone principal também aplica o
     *     estilo dele na seta, em vez de ativar a ferramenta (a tecla C continua ativando).</li>
     * </ul>
     */
    private HBox createConnectionRow(ToggleButton connectionButton) {
        HBox extras = new HBox();
        extras.getStyleClass().add("connection-style-extras");
        for (int i = 0; i < styleOrder.extras().size(); i++) {
            Button button = new Button();
            button.getStyleClass().addAll("tool-button", "connection-style-button");
            button.setFocusTraversable(false);
            button.setOnAction(e -> {
                ConnectionStyle style = (ConnectionStyle) button.getUserData();
                chooseConnectionStyle(style);
                if (!(connectionSelected.get() && onConnectionStylePicked.test(style))) {
                    activeTool.set(Tool.CONNECTION);
                }
            });
            styleButtons.add(button);
            extras.getChildren().add(button);
        }
        // Com seta selecionada, o ícone principal aplica o estilo dele na seta
        // (o filtro consome o clique antes de o botão "armar" e ativar a ferramenta).
        connectionButton.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && connectionSelected.get()
                    && onConnectionStylePicked.test(styleOrder.primary())) {
                e.consume();
            }
        });

        HBox row = new HBox(connectionButton, extras);
        row.getStyleClass().add("connection-tool-row");
        extras.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> activeTool.get() == Tool.CONNECTION || row.isHover() || connectionSelected.get(),
                activeTool, row.hoverProperty(), connectionSelected));
        extras.managedProperty().bind(extras.visibleProperty());
        refreshConnectionStyleButtons();
        return row;
    }

    /** Ícones e dicas conforme a ordem atual dos estilos. */
    private void refreshConnectionStyleButtons() {
        ToggleButton main = buttons.get(Tool.CONNECTION);
        ConnectionStyle primary = styleOrder.primary();
        main.setGraphic(Icons.connectionStyle(primary));
        main.getTooltip().setText(Tool.CONNECTION.getLabel() + ": " + primary.getLabel().toLowerCase()
                + "  (" + Tool.CONNECTION.getShortcut().getName() + ")");
        List<ConnectionStyle> extras = styleOrder.extras();
        for (int i = 0; i < styleButtons.size(); i++) {
            Button button = styleButtons.get(i);
            ConnectionStyle style = extras.get(i);
            button.setUserData(style);
            button.setGraphic(Icons.connectionStyle(style));
            button.setTooltip(new Tooltip(style.getLabel() + " (passa a ser o padrão das setas novas)"));
        }
        connectionStyle.set(primary);
    }

    /**
     * Põe o estilo no lugar principal (troca de lugar com o atual): as setas
     * novas, pela ferramenta Conexão ou pela alça do card, nascem nele.
     */
    public void chooseConnectionStyle(ConnectionStyle style) {
        if (styleOrder.choose(style)) {
            refreshConnectionStyleButtons();
        }
    }

    /**
     * Se há uma seta selecionada no canvas (quem liga a barra ao canvas
     * mantém isto em dia). Com seta selecionada, os botões de estilo mudam a
     * seta em vez de ativar a ferramenta.
     */
    public BooleanProperty connectionSelectedProperty() {
        return connectionSelected;
    }

    /**
     * Chamado quando o usuário escolhe um estilo com uma seta selecionada;
     * devolve se aplicou o estilo na seta (aí a ferramenta não muda).
     */
    public void setOnConnectionStylePicked(Predicate<ConnectionStyle> handler) {
        onConnectionStylePicked = handler == null ? style -> false : handler;
    }

    /** Estilo das setas novas (o do ícone da ferramenta Conexão). */
    public ReadOnlyObjectProperty<ConnectionStyle> connectionStyleProperty() {
        return connectionStyle.getReadOnlyProperty();
    }

    /** Ordem atual dos estilos: a posição 0 é a do ícone principal. */
    public List<ConnectionStyle> getConnectionStyleOrder() {
        return styleOrder.order();
    }

    /** Os botões extras, na ordem em que aparecem (cada um guarda o estilo em {@code getUserData()}). */
    public List<Button> getConnectionStyleButtons() {
        return List.copyOf(styleButtons);
    }

    /** O botão da ferramenta (usado pelos roteiros de teste de interface). */
    public ToggleButton getToolButton(Tool tool) {
        return buttons.get(tool);
    }

    private ColorPickerButton createColorButton() {
        colorButton.setColor(currentColor.get());
        currentColor.addListener((obs, old, color) -> colorButton.setColor(color));
        colorButton.setOnColorPicked(color -> {
            currentColor.set(color);
            onColorPicked.accept(color);
        });
        return colorButton;
    }

    public ObjectProperty<Tool> activeToolProperty() {
        return activeTool;
    }

    public Tool getActiveTool() {
        return activeTool.get();
    }

    public void setActiveTool(Tool tool) {
        activeTool.set(tool);
    }

    /** Cor usada nos próximos cards criados. */
    public ReadOnlyStringProperty currentColorProperty() {
        return currentColor.getReadOnlyProperty();
    }

    /** Chamado quando o usuário escolhe uma cor na paleta da barra. */
    public void setOnColorPicked(Consumer<String> handler) {
        onColorPicked = handler == null ? color -> { } : handler;
    }
}
