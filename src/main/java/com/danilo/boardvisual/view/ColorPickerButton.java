package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.function.Consumer;

/**
 * Bolinha colorida que abre a paleta num popup. Usada na barra de
 * ferramentas e na barra flutuante do card selecionado.
 *
 * {@link #setColor} só muda o que é exibido; {@link #setOnColorPicked} só
 * dispara quando o usuário escolhe uma cor.
 */
public class ColorPickerButton extends Button {

    private static final PseudoClass MIXED = PseudoClass.getPseudoClass("mixed");
    private static final double POPUP_GAP = 8;

    private final Side popupSide;
    private final Region chip = ColorSwatchGrid.colorChip(Card.DEFAULT_COLOR);
    private final ColorSwatchGrid grid = new ColorSwatchGrid();
    private final VBox popupContent = new VBox();
    private final Popup popup = new Popup();
    private Consumer<String> onColorPicked = color -> { };

    /** @param popupSide lado do botão em que a paleta abre */
    public ColorPickerButton(String tooltip, Side popupSide) {
        this.popupSide = popupSide;
        getStyleClass().addAll("tool-button", "color-button");
        setGraphic(chip);
        setTooltip(new Tooltip(tooltip));
        setFocusTraversable(false);
        setOnAction(e -> showPopup());

        Label title = new Label("Cor");
        title.getStyleClass().add("panel-section-title");
        // O popup é outra janela: recebe a classe "root" para herdar as variáveis de cor do app.css.
        popupContent.getStyleClass().addAll("root", "floating-panel", "color-popup");
        popupContent.getChildren().addAll(title, grid);
        popup.getContent().add(popupContent);
        popup.setAutoHide(true);

        grid.setOnColorPicked(color -> {
            popup.hide();
            onColorPicked.accept(color);
        });
    }

    /** Cor exibida; {@code null} indica seleção com cores diferentes. */
    public void setColor(String hex) {
        chip.pseudoClassStateChanged(MIXED, hex == null);
        ColorSwatchGrid.setChipColor(chip, hex == null ? Card.DEFAULT_COLOR : hex);
        grid.setHighlightedColor(hex);
    }

    public void setOnColorPicked(Consumer<String> handler) {
        onColorPicked = handler == null ? color -> { } : handler;
    }

    private void showPopup() {
        if (getScene() == null) {
            return;
        }
        popupContent.getStylesheets().setAll(getScene().getStylesheets());
        Bounds bounds = localToScreen(getLayoutBounds());
        if (popupSide == Side.RIGHT) {
            popup.show(this, bounds.getMaxX() + POPUP_GAP + 2, bounds.getMinY() - POPUP_GAP);
        } else {
            popup.show(this, bounds.getMinX() - POPUP_GAP, bounds.getMaxY() + POPUP_GAP);
        }
    }
}
