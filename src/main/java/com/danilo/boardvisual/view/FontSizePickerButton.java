package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * Botão com o tamanho de fonte atual ("Auto" ou o número) que abre as opções:
 * automático ou um dos tamanhos de {@link CardTextFit#MANUAL_FONT_SIZES}.
 * Mesma separação dos outros seletores: {@link #setFontSize} só exibe,
 * {@link #setOnFontSizePicked} só dispara com escolha do usuário.
 */
public class FontSizePickerButton extends PopupButton {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /** Opções por valor; {@link Card#AUTO_FONT_SIZE} é o "Automático". */
    private final Map<Double, Button> options = new LinkedHashMap<>();
    private DoubleConsumer onFontSizePicked = size -> { };

    public FontSizePickerButton(String tooltip, Side popupSide) {
        super(tooltip, popupSide, "Tamanho do texto");
        getStyleClass().add("font-size-button");

        Button auto = option("Automático", Card.AUTO_FONT_SIZE);
        auto.getStyleClass().add("font-size-auto");
        auto.setMaxWidth(Double.MAX_VALUE);

        TilePane sizes = new TilePane();
        sizes.getStyleClass().add("font-size-options");
        sizes.setPrefColumns(4);
        for (int size : CardTextFit.MANUAL_FONT_SIZES) {
            sizes.getChildren().add(option(Integer.toString(size), size));
        }

        VBox body = new VBox(auto, sizes);
        body.getStyleClass().add("font-size-body");
        setPopupBody(body);
        setFontSize(Card.AUTO_FONT_SIZE);
    }

    private Button option(String label, double size) {
        Button option = new Button(label);
        option.getStyleClass().addAll("tool-button", "font-size-option");
        option.setFocusTraversable(false);
        option.setOnAction(e -> {
            hidePopup();
            onFontSizePicked.accept(size);
        });
        options.put(size, option);
        return option;
    }

    /** Tamanho exibido; {@code NaN} indica seleção com tamanhos diferentes. */
    public void setFontSize(double size) {
        if (Double.isNaN(size)) {
            setText("–");
        } else if (size == Card.AUTO_FONT_SIZE) {
            setText("Auto");
        } else {
            setText(Integer.toString((int) Math.round(size)));
        }
        options.forEach((value, option) -> option.pseudoClassStateChanged(SELECTED, value == size));
    }

    public void setOnFontSizePicked(DoubleConsumer handler) {
        onFontSizePicked = handler == null ? size -> { } : handler;
    }
}
