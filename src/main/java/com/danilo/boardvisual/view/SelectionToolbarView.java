package com.danilo.boardvisual.view;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * Barra flutuante que aparece logo acima do card selecionado (estilo Canva).
 *
 * O posicionamento é feito pelo {@link BoardView}; as ações, pelo controller.
 * Novas ações (formato, bloquear...) entram como mais botões aqui.
 */
public class SelectionToolbarView extends HBox {

    private final ColorPickerButton colorButton = new ColorPickerButton("Cor do card", Side.BOTTOM);
    private final Button duplicateButton = actionButton("Duplicar", Icons.DUPLICATE);
    private final Button deleteButton = actionButton("Excluir", Icons.DELETE);

    public SelectionToolbarView() {
        getStyleClass().addAll("floating-panel", "selection-toolbar");
        deleteButton.getStyleClass().add("danger");

        getChildren().addAll(colorButton, new Separator(Orientation.VERTICAL), duplicateButton, deleteButton);
    }

    private static Button actionButton(String tooltip, String iconPath) {
        Button button = new Button();
        button.getStyleClass().add("tool-button");
        button.setGraphic(Icons.create(iconPath));
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    public ColorPickerButton getColorButton() {
        return colorButton;
    }

    public Button getDuplicateButton() {
        return duplicateButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }
}
