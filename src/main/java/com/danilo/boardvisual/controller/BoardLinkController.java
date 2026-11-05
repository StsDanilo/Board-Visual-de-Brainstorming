package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.view.BoardLinkPickerPopup;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Link para outro board: um atalho, em qualquer card, para qualquer board da
 * árvore (mesmo em outro caminho). Continuação do link externo
 * ({@link LinkController}): mesmo marcador, mesmo menu, mesma barra.
 *
 * - Escolher, trocar, remover: botão de seta curva da barra flutuante (com um
 *   card selecionado) ou clique direito no card. Os dois abrem o
 *   {@link BoardLinkPickerPopup} com a árvore inteira; toda mudança passa por
 *   {@code history.perform} (um passo no desfazer).
 * - Ir: clique no marcador de seta curva (ou "Ir para o board de destino" no
 *   menu). O {@link NavigationController#jumpTo} monta o caminho real do
 *   destino, e o voltar leva de volta ao board de onde se saiu. Navegar não
 *   entra no desfazer.
 * - Destino excluído: o marcador fica "quebrado" (riscado, esmaecido) e o
 *   clique avisa. O link não é apagado: desfazer a exclusão traz o destino de
 *   volta com o mesmo id. A conferência é refeita quando o board da tela muda,
 *   quando cards entram ou saem dele e quando um link ou board filho muda.
 */
public class BoardLinkController {

    private static final String APP_NAME = "BoardVisual";
    static final String MISSING_TARGET = "Board de destino não existe mais";

    private final BoardView view;
    private final SelectionToolbarView selectionToolbar;
    private final EditHistory history;
    private final NavigationController navigation;
    private final BoardLinkPickerPopup picker = new BoardLinkPickerPopup();

    /** Card selecionado observado, para o botão refletir o link (inclusive ao desfazer). */
    private Card observed;
    private final InvalidationListener toolbarListener = obs -> refreshToolbar();
    /** Um link ou um board filho mudou em algum card do board da tela: confere de novo. */
    private final InvalidationListener markersListener = obs -> refreshMarkers();
    private final ListChangeListener<Card> cardsListener = change -> refreshMarkers();
    private Board shownBoard;

    public BoardLinkController(BoardView view, SelectionToolbarView selectionToolbar, EditHistory history,
                               NavigationController navigation, CardContextMenuController contextMenu) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;
        this.navigation = navigation;

        selectionToolbar.getBoardLinkButton().setOnAction(e -> editFromToolbar());
        view.getSelection().addListener((SetChangeListener<Card>) change -> refreshToolbar());
        contextMenu.addContributor(this::menuItems);
        navigation.setOnBoardShown(this::boardShown);
        refreshToolbar();
    }

    public void attach(CardView cardView) {
        Card card = cardView.getCard();
        cardView.getMarker(CardMarkers.Kind.BOARD_LINK).setOnAction(e -> go(card));
        // A dica é montada na hora: renomear o card do destino muda o caminho.
        cardView.getMarker(CardMarkers.Kind.BOARD_LINK).getTooltip()
                .setOnShowing(e -> cardView.setBoardLinkDescription(describe(card)));
        // O mesmo ouvinte, uma vez só por card (o card ganha um CardView novo a cada visita ao board).
        card.boardLinkProperty().removeListener(markersListener);
        card.boardLinkProperty().addListener(markersListener);
        card.childBoardProperty().removeListener(markersListener);
        card.childBoardProperty().addListener(markersListener);
        refreshMarker(cardView, card);
    }

    /** O popup de escolha (usado pelos roteiros de teste de interface). */
    public BoardLinkPickerPopup getPicker() {
        return picker;
    }

    // -------------------------------------------------------------- ações

    /** Define o destino do card (id do board) como um passo do desfazer. */
    public static void setBoardLink(EditHistory history, Card card, String boardId) {
        history.perform(() -> card.setBoardLink(boardId));
    }

    public static void removeBoardLink(EditHistory history, Card card) {
        history.perform(() -> card.setBoardLink(null));
    }

    /** Vai para o board de destino do card (com o caminho completo no topo). */
    public void go(Card card) {
        if (!card.hasBoardLink()) {
            return;
        }
        if (navigation.jumpTo(card.getBoardLink()) == NavigationPath.JumpResult.NOT_FOUND) {
            warn(MISSING_TARGET + ".",
                    "O card que continha esse board foi excluído. Desfazer a exclusão (Ctrl+Z, no board onde ela "
                            + "foi feita) traz o board de volta, e o link volta a funcionar. Para escolher outro "
                            + "destino, use o clique direito no card.");
        }
    }

    /** Abre a escolha do destino de um card, com o canto de cima à esquerda em (screenX, screenY). */
    public void edit(Card card, double screenX, double screenY) {
        picker.show(view, screenX, screenY, BoardTree.entries(navigation.getRoot()), navigation.getCurrent(),
                card.getBoardLink(),
                board -> setBoardLink(history, card, board.getId()),
                () -> removeBoardLink(history, card));
    }

    private void editFromToolbar() {
        if (view.getSelection().size() != 1) {
            return;
        }
        Card card = view.getSelection().iterator().next();
        Button button = selectionToolbar.getBoardLinkButton();
        Bounds bounds = button.localToScreen(button.getLayoutBounds());
        if (bounds == null) {
            return;
        }
        edit(card, bounds.getMinX(), bounds.getMaxY() + 6);
    }

    /** Dica do marcador: o caminho do destino, ou o aviso de que ele não existe mais. */
    private String describe(Card card) {
        List<BoardTree.Step> path = BoardTree.pathTo(navigation.getRoot(), card.getBoardLink());
        return path.isEmpty() ? MISSING_TARGET : "Ir para o board:\n" + BoardTree.label(path);
    }

    // ------------------------------------------------------- menu de contexto

    private List<MenuItem> menuItems(CardContextMenuController.MenuContext context) {
        Card card = context.card();
        List<MenuItem> items = new ArrayList<>();
        if (card.hasBoardLink()) {
            items.add(item("Ir para o board de destino", () -> go(card)));
            items.add(item("Trocar board de destino...", () -> edit(card, context.screenX(), context.screenY())));
            items.add(item("Remover link para board", () -> removeBoardLink(history, card)));
        } else {
            items.add(item("Link para outro board...", () -> edit(card, context.screenX(), context.screenY())));
        }
        return items;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    // ------------------------------------------------------ marcador quebrado

    private void boardShown() {
        if (shownBoard != null) {
            shownBoard.getCards().removeListener(cardsListener);
        }
        shownBoard = navigation.getCurrent();
        if (shownBoard != null) {
            shownBoard.getCards().addListener(cardsListener);
        }
        picker.hide();
        refreshMarkers();
    }

    /** Confere, para cada card do board da tela, se o destino do link ainda existe. */
    private void refreshMarkers() {
        Board board = view.getBoard();
        if (board == null) {
            return;
        }
        var ids = BoardTree.ids(navigation.getRoot());
        for (Card card : board.getCards()) {
            view.getCardView(card).ifPresent(cardView ->
                    cardView.setMarkerBroken(CardMarkers.Kind.BOARD_LINK,
                            card.hasBoardLink() && !ids.contains(card.getBoardLink())));
        }
    }

    private void refreshMarker(CardView cardView, Card card) {
        boolean broken = card.hasBoardLink() && !BoardTree.contains(navigation.getRoot(), card.getBoardLink());
        cardView.setMarkerBroken(CardMarkers.Kind.BOARD_LINK, broken);
    }

    // ------------------------------------------------------- barra flutuante

    private void refreshToolbar() {
        Card single = view.getSelection().size() == 1 ? view.getSelection().iterator().next() : null;
        if (observed != single) {
            if (observed != null) {
                observed.boardLinkProperty().removeListener(toolbarListener);
            }
            observed = single;
            if (observed != null) {
                observed.boardLinkProperty().addListener(toolbarListener);
            }
        }
        selectionToolbar.showBoardLink(single != null, single != null && single.hasBoardLink());
    }

    private void warn(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (view.getScene() != null) {
            alert.initOwner(view.getScene().getWindow());
        }
        alert.setTitle(APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
