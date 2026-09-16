package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardShape;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Botão com o ícone do formato atual que abre as opções de formato.
 * Mesma separação do seletor de cor: {@link #setShape} só exibe,
 * {@link #setOnShapePicked} só dispara com escolha do usuário.
 */
public class ShapePickerButton extends PopupButton {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final String MIXED_ICON = "M6 12 H6.01 M12 12 H12.01 M18 12 H18.01";

    private final Map<CardShape, Button> options = new EnumMap<>(CardShape.class);
    private Consumer<CardShape> onShapePicked = shape -> { };

    public ShapePickerButton(String tooltip, Side popupSide) {
        super(tooltip, popupSide, "Formato");
        getStyleClass().add("shape-button");

        HBox optionsBox = new HBox();
        optionsBox.getStyleClass().add("shape-options");
        for (Tool tool : Tool.values()) {
            CardShape shape = tool.getShape();
            if (shape == null) {
                continue;
            }
            Button option = new Button();
            option.getStyleClass().addAll("tool-button", "shape-option");
            option.setGraphic(Icons.create(Icons.shape(shape)));
            option.setTooltip(new Tooltip(tool.getLabel()));
            option.setFocusTraversable(false);
            option.setOnAction(e -> {
                hidePopup();
                onShapePicked.accept(shape);
            });
            options.put(shape, option);
            optionsBox.getChildren().add(option);
        }
        setPopupBody(optionsBox);
        setShape(CardShape.RECTANGLE);
    }

    /** Formato exibido; {@code null} indica seleção com formatos diferentes. */
    public void setShape(CardShape shape) {
        setGraphic(Icons.create(shape == null ? MIXED_ICON : Icons.shape(shape)));
        options.forEach((optionShape, option) -> option.pseudoClassStateChanged(SELECTED, optionShape == shape));
    }

    public void setOnShapePicked(Consumer<CardShape> handler) {
        onShapePicked = handler == null ? shape -> { } : handler;
    }
}
