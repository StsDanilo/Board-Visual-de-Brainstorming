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
 * Novas ações entram como mais botões aqui.
 *
 * Os botões de modo (Texto | Lista) só aparecem quando todos os selecionados
 * são painéis flutuantes; os botões Link (clipe) e Link para outro board
 * (seta curva), só com um card selecionado.
 */
public class SelectionToolbarView extends HBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass MIXED = PseudoClass.getPseudoClass("mixed");

    private final ColorPickerButton colorButton = new ColorPickerButton("Cor do card", Side.BOTTOM);
    private final ShapePickerButton shapeButton = new ShapePickerButton("Formato do card", Side.BOTTOM);
    private final FontSizePickerButton fontSizeButton = new FontSizePickerButton("Tamanho do texto", Side.BOTTOM);
    /** Ordem de empilhamento (trazer para frente, enviar para trás...). */
    private final StackOrderButton stackOrderButton = new StackOrderButton(
            "Ordem: trazer para frente ou enviar para trás", Side.BOTTOM);
    /** Travar / destravar a seleção (cadeado). */
    private final Button lockButton = actionButton("Travar  (Ctrl+L)", Icons.UNLOCK);
    private final Button duplicateButton = actionButton("Duplicar  (Ctrl+D)", Icons.DUPLICATE);
    private final Button deleteButton = actionButton("Excluir  (Delete)", Icons.DELETE);
    private final Button textModeButton = actionButton("Painel: texto", Icons.PANEL_TEXT);
    private final Button listModeButton = actionButton("Painel: lista", Icons.PANEL_LIST);
    private final HBox panelModeGroup = new HBox();
    /** Link externo do card selecionado (só com exatamente um card selecionado). */
    private final Button linkButton = new Button();
    /** Link para outro board do card selecionado (idem). */
    private final Button boardLinkButton = new Button();

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
        // O formato some quando só há cards de imagem ou seções selecionados (não têm formato a escolher),
        // e o tamanho do texto, quando só há seções (o título tem fonte fixa).
        shapeButton.managedProperty().bind(shapeButton.visibleProperty());
        fontSizeButton.managedProperty().bind(fontSizeButton.visibleProperty());

        linkButton.getStyleClass().addAll("tool-button", "link-button");
        linkButton.setGraphic(Icons.link());
        linkButton.setTooltip(new Tooltip("Link: adicionar, editar ou remover o endereço web do card"));
        linkButton.setFocusTraversable(false);
        linkButton.managedProperty().bind(linkButton.visibleProperty());

        boardLinkButton.getStyleClass().addAll("tool-button", "link-button", "board-link-button");
        boardLinkButton.setGraphic(Icons.boardLink());
        boardLinkButton.setTooltip(new Tooltip(
                "Link para outro board: escolher, trocar ou remover o board de destino do card"));
        boardLinkButton.setFocusTraversable(false);
        boardLinkButton.managedProperty().bind(boardLinkButton.visibleProperty());

        lockButton.getStyleClass().add("lock-button");

        getChildren().addAll(colorButton, shapeButton, fontSizeButton, panelModeGroup, linkButton, boardLinkButton,
                new Separator(Orientation.VERTICAL), stackOrderButton, lockButton, duplicateButton, deleteButton);
    }

    public StackOrderButton getStackOrderButton() {
        return stackOrderButton;
    }

    /** Estado de travamento da seleção, mostrado pelo botão de cadeado. */
    public enum LockState { UNLOCKED, LOCKED, MIXED }

    /**
     * Botão de cadeado: aberto (nenhum travado), fechado e marcado (todos
     * travados) ou fechado e tracejado (seleção mista, como a cor "mista").
     */
    public void showLock(LockState state) {
        lockButton.setGraphic(Icons.create(state == LockState.UNLOCKED ? Icons.UNLOCK : Icons.LOCK));
        lockButton.pseudoClassStateChanged(SELECTED, state == LockState.LOCKED);
        lockButton.pseudoClassStateChanged(MIXED, state == LockState.MIXED);
        lockButton.getTooltip().setText(switch (state) {
            case UNLOCKED -> "Travar  (Ctrl+L): o card não se move, não muda de tamanho nem de formato "
                    + "e não é excluído por engano";
            case LOCKED -> "Destravar  (Ctrl+L)";
            case MIXED -> "Alguns cards estão travados: clique para travar todos  (Ctrl+L)";
        });
    }

    public Button getLockButton() {
        return lockButton;
    }

    /**
     * Botão "Link para outro board" (seta curva), ao lado do botão Link. Mesmas
     * regras: só com um card selecionado; marcado quando o card já tem destino.
     */
    public void showBoardLink(boolean visible, boolean cardHasBoardLink) {
        boardLinkButton.setVisible(visible);
        boardLinkButton.pseudoClassStateChanged(SELECTED, visible && cardHasBoardLink);
    }

    public Button getBoardLinkButton() {
        return boardLinkButton;
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
