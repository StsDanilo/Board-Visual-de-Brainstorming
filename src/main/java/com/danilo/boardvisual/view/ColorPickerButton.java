package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * Bolinha colorida que abre a paleta. Usada na barra de ferramentas e na
 * barra flutuante do card selecionado.
 *
 * {@link #setColor} só muda o que é exibido; {@link #setOnColorPicked} só
 * dispara quando o usuário escolhe uma cor.
 */
public class ColorPickerButton extends PopupButton {

    private static final PseudoClass MIXED = PseudoClass.getPseudoClass("mixed");

    private final Region chip = ColorSwatchGrid.colorChip(Card.DEFAULT_COLOR);
    private final ColorSwatchGrid grid = new ColorSwatchGrid();
    private Consumer<String> onColorPicked = color -> { };

    /** @param popupSide lado do botão em que a paleta abre */
    public ColorPickerButton(String tooltip, Side popupSide) {
        super(tooltip, popupSide, "Cor");
        getStyleClass().add("color-button");
        setGraphic(chip);
        setPopupBody(grid);

        grid.setOnColorPicked(color -> {
            hidePopup();
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
}
