package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Barra de ferramentas vertical, flutuando sobre o canvas.
 *
 * - Ferramentas: a fonte da verdade é {@link #activeToolProperty()}; clicar
 *   num botão muda a propriedade, e mudar a propriedade por código (ex.:
 *   voltar para "Selecionar" depois de criar um card) atualiza os botões.
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

    public ToolBarView() {
        getStyleClass().add("tool-bar-view");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        for (Tool tool : Tool.values()) {
            ToggleButton button = createToolButton(tool);
            buttons.put(tool, button);
            getChildren().add(button);
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
        button.setTooltip(new Tooltip(tool.getLabel()));
        button.setUserData(tool);
        button.setToggleGroup(group);
        // Não roubar o foco do texto de um card ao trocar de ferramenta.
        button.setFocusTraversable(false);
        return button;
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
