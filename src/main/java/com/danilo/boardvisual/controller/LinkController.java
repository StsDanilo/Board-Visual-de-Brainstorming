package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.WebLink;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.LinkEditorPopup;
import com.danilo.boardvisual.view.SelectionToolbarView;
import javafx.beans.InvalidationListener;
import javafx.collections.SetChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Link externo dos cards: um endereço web opcional em qualquer card.
 *
 * - Adicionar, editar, remover: botão "Link" da barra flutuante (com um card
 *   selecionado) ou clique direito no card ({@link CardContextMenuController}).
 *   Os dois abrem o {@link LinkEditorPopup}; toda mudança passa por
 *   {@code history.perform} (um passo no desfazer).
 * - Abrir: clique no marcador de clipe do card (ou "Abrir link" no menu). O
 *   endereço é conferido de novo ({@link WebLink#toBrowserUri}) antes de ir ao
 *   sistema, e quem abre de fato é o {@link Opener} recebido no construtor
 *   (na aplicação, {@code HostServices.showDocument}).
 */
public class LinkController {

    /** Abre um endereço (já conferido, só ASCII) no navegador padrão. */
    @FunctionalInterface
    public interface Opener {
        void open(String uri);
    }

    private static final String APP_NAME = "BoardVisual";

    private final BoardView view;
    private final SelectionToolbarView selectionToolbar;
    private final EditHistory history;
    private final Opener opener;
    private final LinkEditorPopup editor = new LinkEditorPopup();
    /** Na apresentação (tela cheia) os avisos não abrem diálogo: vão para o aviso discreto da tela. */
    private BooleanSupplier presenting = () -> false;
    private Consumer<String> notifier = text -> { };

    /** Card selecionado observado, para o botão "Link" refletir o link (inclusive ao desfazer). */
    private Card observed;
    private final InvalidationListener linkListener = obs -> refreshToolbar();

    public LinkController(BoardView view, SelectionToolbarView selectionToolbar, EditHistory history,
                          Opener opener, CardContextMenuController contextMenu) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;
        this.opener = opener;

        selectionToolbar.getLinkButton().setOnAction(e -> editFromToolbar());
        view.getSelection().addListener((SetChangeListener<Card>) change -> refreshToolbar());
        contextMenu.addContributor(this::menuItems);
        refreshToolbar();
    }

    public void attach(CardView cardView) {
        cardView.getMarker(CardMarkers.Kind.LINK).setOnAction(e -> open(cardView.getCard()));
    }

    /**
     * Na apresentação um aviso (link inválido, navegador que não abriu) não pode abrir um diálogo
     * por cima da tela cheia: vai para {@code notifier}.
     */
    public void setPresentation(BooleanSupplier presenting, Consumer<String> notifier) {
        this.presenting = presenting == null ? () -> false : presenting;
        this.notifier = notifier == null ? text -> { } : notifier;
    }

    /** O popup de edição (usado pelos roteiros de teste de interface). */
    public LinkEditorPopup getEditor() {
        return editor;
    }

    // -------------------------------------------------------------- ações

    /**
     * Define o link do card como um passo do desfazer. {@code raw} é o que o
     * usuário digitou; vazio tira o link.
     *
     * @return a mensagem de erro, ou {@code null} se deu certo (inclusive sem mudança)
     */
    public static String setLink(EditHistory history, Card card, String raw) {
        WebLink.Check check = WebLink.check(raw);
        if (!check.isValid()) {
            return check.error();
        }
        history.perform(() -> card.setLink(check.link()));
        return null;
    }

    public static void removeLink(EditHistory history, Card card) {
        history.perform(() -> card.setLink(""));
    }

    /**
     * Abre o link do card no navegador. O link é conferido de novo aqui: nada
     * que não seja http(s) chega ao sistema, mesmo que algo tenha escapado antes.
     */
    public void open(Card card) {
        Optional<String> uri = WebLink.toBrowserUri(card.getLink());
        if (uri.isEmpty()) {
            if (card.hasLink()) {
                warn("Este link não é um endereço web válido e não será aberto.", card.getLink());
            }
            return;
        }
        try {
            opener.open(uri.get());
        } catch (RuntimeException e) {
            warn("Não foi possível abrir o link no navegador.",
                    uri.get() + (e.getMessage() == null ? "" : "\n\n" + e.getMessage()));
        }
    }

    /** Abre o editor do link de um card, com o canto de cima à esquerda em (screenX, screenY). */
    public void edit(Card card, double screenX, double screenY) {
        editor.show(view, screenX, screenY, card.getLink(),
                link -> setLink(history, card, link),
                () -> removeLink(history, card));
    }

    private void editFromToolbar() {
        if (view.getSelection().size() != 1) {
            return;
        }
        Card card = view.getSelection().iterator().next();
        Button button = selectionToolbar.getLinkButton();
        Bounds bounds = button.localToScreen(button.getLayoutBounds());
        if (bounds == null) {
            return;
        }
        edit(card, bounds.getMinX(), bounds.getMaxY() + 6);
    }

    // ------------------------------------------------------- menu de contexto

    private List<MenuItem> menuItems(CardContextMenuController.MenuContext context) {
        Card card = context.card();
        List<MenuItem> items = new ArrayList<>();
        if (card.hasLink()) {
            items.add(item("Abrir link", () -> open(card)));
            items.add(item("Editar link...", () -> edit(card, context.screenX(), context.screenY())));
            items.add(item("Remover link", () -> removeLink(history, card)));
        } else {
            items.add(item("Adicionar link...", () -> edit(card, context.screenX(), context.screenY())));
        }
        return items;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    // ------------------------------------------------------- barra flutuante

    private void refreshToolbar() {
        Card single = view.getSelection().size() == 1 ? view.getSelection().iterator().next() : null;
        if (observed != single) {
            if (observed != null) {
                observed.linkProperty().removeListener(linkListener);
            }
            observed = single;
            if (observed != null) {
                observed.linkProperty().addListener(linkListener);
            }
        }
        selectionToolbar.showLink(single != null, single != null && single.hasLink());
    }

    private void warn(String header, String content) {
        if (presenting.getAsBoolean()) {
            notifier.accept(header);
            return;
        }
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
