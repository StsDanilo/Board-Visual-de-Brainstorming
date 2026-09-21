package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.FloatingPanelView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import javafx.beans.InvalidationListener;
import javafx.collections.SetChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Painéis flutuantes: abrir e fechar, editar a lista e trocar o modo.
 *
 * - O botão no card abre/fecha o painel. Só um painel fica aberto por vez.
 * - Fecha também no ✕, com Esc (via {@link KeyboardController}) ou ao
 *   clicar fora dele (exceto no próprio card, para poder editar o título).
 * - Trocar entre Texto e Lista apaga o conteúdo do modo anterior; se houver
 *   conteúdo, pede confirmação antes. Tudo passa pelo histórico.
 */
public class PanelController {

    private final BoardView view;
    private final SelectionToolbarView selectionToolbar;
    private final EditHistory history;
    private final TextEditController textEdit;

    private FloatingPanelView openPanel;

    /** Cards selecionados observados, para os botões de modo refletirem mudanças (ex.: desfazer). */
    private final Set<Card> observedCards = new HashSet<>();
    private final InvalidationListener modeListener = obs -> refreshModeButtons();

    public PanelController(BoardView view, SelectionToolbarView selectionToolbar, EditHistory history,
                           TextEditController textEdit) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;
        this.textEdit = textEdit;

        selectionToolbar.getTextModeButton().setOnAction(e -> switchMode(PanelMode.TEXT));
        selectionToolbar.getListModeButton().setOnAction(e -> switchMode(PanelMode.LIST));
        view.getSelection().addListener((SetChangeListener<Card>) change -> observeSelection());
        observeSelection();

        // O BoardView fecha o painel sozinho quando o card sai do board ou o board é trocado.
        view.panelAnchorProperty().addListener((obs, old, anchor) -> {
            if (openPanel != null && anchor != openPanel.getCard()) {
                openPanel.dispose();
                openPanel = null;
            }
        });

        // Clique fora do painel e fora do card dele fecha o painel (como uma combobox).
        view.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (openPanel == null || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Node target = e.getPickResult().getIntersectedNode();
            boolean insideAnchor = view.getCardView(openPanel.getCard())
                    .map(cardView -> isInside(target, cardView))
                    .orElse(false);
            if (!isInside(target, openPanel) && !insideAnchor) {
                close();
            }
        });
    }

    public void attach(CardView cardView) {
        cardView.getPanelButton().setOnAction(e -> toggle(cardView.getCard()));
    }

    public void toggle(Card card) {
        if (openPanel != null && openPanel.getCard() == card) {
            close();
        } else {
            open(card);
        }
    }

    public void open(Card card) {
        if (!card.isPanel()) {
            return;
        }
        FloatingPanelView panel = new FloatingPanelView(card);
        panel.setOnClose(this::close);
        panel.setOnAddItem(text -> history.perform(() -> card.addListItem(text)));
        panel.setOnRemoveItem(item -> history.perform(() -> card.removeListItem(item)));
        // Tudo que for digitado no painel (entre ações pontuais) vira um passo de desfazer.
        textEdit.track(panel.focusWithinProperty());

        view.showPanel(panel, card);
        openPanel = panel;
        view.select(card);
        panel.focusPrimaryField();
    }

    public void close() {
        if (openPanel == null) {
            return;
        }
        if (openPanel.isFocusWithin()) {
            view.requestFocus(); // encerra a edição antes de o painel sair da tela
        }
        view.hidePanel();
    }

    /** Fecha o painel se houver um aberto; usado pelo Esc. */
    public boolean closeIfOpen() {
        boolean wasOpen = openPanel != null;
        close();
        return wasOpen;
    }

    // ------------------------------------------------------------ modo

    private void switchMode(PanelMode mode) {
        List<Card> targets = view.getSelection().stream()
                .filter(Card::isPanel)
                .filter(card -> card.getPanelMode() != mode)
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        List<Card> withContent = targets.stream().filter(Card::hasPanelContent).toList();
        if (!withContent.isEmpty() && !confirmModeChange(mode, withContent.size())) {
            return;
        }
        history.perform(() -> targets.forEach(card -> card.changePanelMode(mode)));
        if (openPanel != null && targets.contains(openPanel.getCard())) {
            openPanel.focusPrimaryField();
        }
    }

    private boolean confirmModeChange(PanelMode mode, int panelsWithContent) {
        String target = mode == PanelMode.LIST ? "lista" : "texto";
        String lost = mode == PanelMode.LIST ? "O texto detalhado" : "A lista";
        String which = panelsWithContent == 1 ? "deste painel" : "de " + panelsWithContent + " painéis";

        ButtonType switchButton = new ButtonType("Alternar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", switchButton, cancelButton);
        if (view.getScene() != null) {
            alert.initOwner(view.getScene().getWindow());
        }
        alert.setTitle("Alternar modo do painel");
        alert.setHeaderText("Alternar para " + target + "?");
        alert.setContentText(lost + " " + which + " será apagado.\nVocê pode desfazer com Ctrl+Z.");
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == switchButton;
    }

    private void observeSelection() {
        observedCards.forEach(card -> card.panelModeProperty().removeListener(modeListener));
        observedCards.clear();
        observedCards.addAll(view.getSelection());
        observedCards.forEach(card -> card.panelModeProperty().addListener(modeListener));
        refreshModeButtons();
    }

    /** Botões de modo só com painéis selecionados; marcado se todos estão no mesmo modo. */
    private void refreshModeButtons() {
        Set<Card> selection = view.getSelection();
        boolean allPanels = !selection.isEmpty() && selection.stream().allMatch(Card::isPanel);
        Set<PanelMode> modes = selection.stream().map(Card::getPanelMode).collect(Collectors.toSet());
        selectionToolbar.showPanelMode(allPanels, modes.size() == 1 ? modes.iterator().next() : null);
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }
}
