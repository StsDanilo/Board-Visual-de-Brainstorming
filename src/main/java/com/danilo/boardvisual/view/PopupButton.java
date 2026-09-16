package com.danilo.boardvisual.view;

import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/**
 * Botão de barra que abre um pequeno painel flutuante (popup) ao lado.
 * Base do seletor de cor e do seletor de formato.
 */
abstract class PopupButton extends Button {

    private static final double POPUP_GAP = 8;

    private final Side popupSide;
    private final VBox popupContent = new VBox();
    private final Popup popup = new Popup();

    /**
     * @param popupSide lado do botão em que o painel abre
     * @param title     título pequeno exibido no topo do painel
     */
    PopupButton(String tooltip, Side popupSide, String title) {
        this.popupSide = popupSide;
        getStyleClass().add("tool-button");
        setTooltip(new Tooltip(tooltip));
        setFocusTraversable(false);
        setOnAction(e -> showPopup());

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-section-title");
        // O popup é outra janela: recebe a classe "root" para herdar as variáveis de cor do app.css.
        popupContent.getStyleClass().addAll("root", "floating-panel", "popup-panel");
        popupContent.getChildren().add(titleLabel);
        popup.getContent().add(popupContent);
        popup.setAutoHide(true);
    }

    /** Adiciona o conteúdo do painel (chamado pelas subclasses). */
    void setPopupBody(Node body) {
        popupContent.getChildren().add(body);
    }

    void hidePopup() {
        popup.hide();
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
