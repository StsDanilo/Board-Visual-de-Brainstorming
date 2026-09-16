package com.danilo.boardvisual.view;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Grade de amostras da {@link CardPalette}. Usada tanto na barra de
 * ferramentas quanto no painel de propriedades.
 *
 * Separa escolha de exibição: {@link #setOnColorPicked} só dispara em clique
 * do usuário, e {@link #setHighlightedColor} só marca a amostra na tela.
 */
public class ColorSwatchGrid extends TilePane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Map<String, Button> swatches = new LinkedHashMap<>();
    private Consumer<String> onColorPicked = color -> { };

    public ColorSwatchGrid() {
        getStyleClass().add("color-swatch-grid");
        setPrefColumns(4);

        for (CardPalette.Entry entry : CardPalette.COLORS) {
            Button swatch = new Button();
            swatch.getStyleClass().add("color-swatch");
            swatch.setGraphic(colorChip(entry.hex()));
            swatch.setTooltip(new Tooltip(entry.name()));
            swatch.setFocusTraversable(false);
            swatch.setOnAction(e -> onColorPicked.accept(entry.hex()));
            swatches.put(CardPalette.normalize(entry.hex()), swatch);
            getChildren().add(swatch);
        }
    }

    public void setOnColorPicked(Consumer<String> handler) {
        onColorPicked = handler == null ? color -> { } : handler;
    }

    /** Marca a amostra da cor informada; {@code null} (ou cor fora da paleta) desmarca todas. */
    public void setHighlightedColor(String hex) {
        String key = CardPalette.normalize(hex);
        swatches.forEach((swatchHex, swatch) -> swatch.pseudoClassStateChanged(SELECTED, swatchHex.equals(key)));
    }

    /** Bolinha de cor; a cor vai como variável CSS, o visual fica no app.css. */
    static Region colorChip(String hex) {
        Region chip = new Region();
        chip.getStyleClass().add("color-chip");
        setChipColor(chip, hex);
        return chip;
    }

    static void setChipColor(Region chip, String hex) {
        chip.setStyle("-swatch-color: " + hex + ";");
    }
}
