package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.PanelMode;
import javafx.css.PseudoClass;
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
 * Novas ações (bloquear...) entram como mais botões aqui.
 *
 * Os botões de modo (Texto | Lista) só aparecem quando todos os selecionados
 * são painéis flutuantes; o botão Link, só com um card selecionado.
 */
public class SelectionToolbarView extends HBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final ColorPickerButton colorButton = new ColorPickerButton("Cor do card", Side.BOTTOM);
    private final ShapePickerButton shapeButton = new ShapePickerButton("Formato do card", Side.BOTTOM);
    private final FontSizePickerButton fontSizeButton = new FontSizePickerButton("Tamanho do texto", Side.BOTTOM);
    private final Button duplicateButton = actionButton("Duplicar  (Ctrl+D)", Icons.DUPLICATE);
    private final Button deleteButton = actionButton("Excluir  (Delete)", Icons.DELETE);
    private final Button textModeButton = actionButton("Painel: texto", Icons.PANEL_TEXT);
    private final Button listModeButton = actionButton("Painel: lista", Icons.PANEL_LIST);
    private final HBox panelModeGroup = new HBox();
    /** Link externo do card selecionado (só com exatamente um card selecionado). */
    private final Button linkButton = new Button();

    public SelectionToolbarView() {
        getStyleClass().addAll("floating-panel", "selection-toolbar");
        deleteButton.getStyleClass().add("danger");

        textModeButton.getStyleClass().add("panel-mode-button");
        listModeButton.getStyleClass().add("panel-mode-button");
        panelModeGroup.getStyleClass().add("panel-mode-group");
        panelModeGroup.getChildren().addAll(new Separator(Orientation.VERTICAL), textModeButton, listModeButton);
        // Invisível também não ocupa espaço na barra.
        panelModeGroup.managedProperty().bind(panelModeGroup.visibleProperty());
        panelModeGroup.setVisible(false);
        // O formato some quando só há cards de imagem selecionados (eles são sempre retângulos).
        shapeButton.managedProperty().bind(shapeButton.visibleProperty());

        linkButton.getStyleClass().addAll("tool-button", "link-button");
        linkButton.setGraphic(Icons.link());
        linkButton.setTooltip(new Tooltip("Link: adicionar, editar ou remover o endereço web do card"));
        linkButton.setFocusTraversable(false);
        linkButton.managedProperty().bind(linkButton.visibleProperty());

        getChildren().addAll(colorButton, shapeButton, fontSizeButton, panelModeGroup, linkButton,
                new Separator(Orientation.VERTICAL), duplicateButton, deleteButton);
    }

    /**
     * Botão "Link". Aparece só com um card selecionado (o link é de um card) e
     * fica marcado quando esse card já tem link.
     */
    public void showLink(boolean visible, boolean cardHasLink) {
        linkButton.setVisible(visible);
        linkButton.pseudoClassStateChanged(SELECTED, visible && cardHasLink);
    }

    public Button getLinkButton() {
        return linkButton;
    }

    /**
     * Mostra os botões de modo do painel e marca o modo atual
     * ({@code null} = seleção com modos diferentes, nenhum marcado).
     */
    public void showPanelMode(boolean visible, PanelMode mode) {
        panelModeGroup.setVisible(visible);
        textModeButton.pseudoClassStateChanged(SELECTED, mode == PanelMode.TEXT);
        listModeButton.pseudoClassStateChanged(SELECTED, mode == PanelMode.LIST);
    }

    public Button getTextModeButton() {
        return textModeButton;
    }

    public Button getListModeButton() {
        return listModeButton;
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

    public ShapePickerButton getShapeButton() {
        return shapeButton;
    }

    public FontSizePickerButton getFontSizeButton() {
        return fontSizeButton;
    }

    public Button getDuplicateButton() {
        return duplicateButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }
}
