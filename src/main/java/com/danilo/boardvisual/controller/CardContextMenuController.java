package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.CardView;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Menu de contexto dos cards: clique direito num card, <b>sem arrastar</b>.
 *
 * O botão direito continua sendo pan quando arrasta (o pan só começa ao mover,
 * 4.3 do DOCUMENTACAO.md): aqui só se olha o soltar, e só se o mouse ficou
 * parado desde o apertar. O card clicado passa a ser a seleção (se ainda não
 * estava nela), como nos editores gráficos.
 *
 * O menu é montado na hora, a partir de quem se registrou com
 * {@link #addContributor}: cada controller entra com os seus itens (link
 * externo hoje; link para outro board, ordem de empilhamento... depois), e os
 * grupos são separados por uma linha. Sem itens, o menu não abre.
 *
 * O menu nativo do campo de texto (Copiar/Colar) não aparece por cima: o
 * clique direito sempre tira o foco do texto (começo do pan), então ele não
 * teria efeito.
 */
public class CardContextMenuController {

    /** Contexto de um clique direito: o card e onde abrir o que vier depois (tela). */
    public record MenuContext(Card card, CardView cardView, double screenX, double screenY) {
    }

    /** Quem põe itens no menu do card. */
    @FunctionalInterface
    public interface Contributor {
        /** Itens para este card, na ordem; lista vazia = nada a oferecer. */
        List<MenuItem> itemsFor(MenuContext context);
    }

    private final BoardView view;
    private final List<Contributor> contributors = new ArrayList<>();
    private final ContextMenu menu = new ContextMenu();

    public CardContextMenuController(BoardView view) {
        this.view = view;
        menu.getStyleClass().add("card-context-menu");

        view.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY && e.isStillSincePress()) {
                view.cardViewAt(e.getSceneX(), e.getSceneY())
                        .ifPresent(cardView -> show(cardView, e.getScreenX(), e.getScreenY()));
            }
        });
        // O pedido de menu do sistema (vem depois do soltar, mesmo após um pan) não
        // deve abrir o menu do campo de texto do card por cima do nosso.
        view.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (!e.isKeyboardTrigger() && insideCard(e.getPickResult().getIntersectedNode())) {
                e.consume();
            }
        });
    }

    /** Registra quem contribui com itens (na ordem em que aparecem no menu). */
    public void addContributor(Contributor contributor) {
        contributors.add(contributor);
    }

    /** O menu (para os roteiros de teste de interface e para fechar de fora). */
    public ContextMenu getMenu() {
        return menu;
    }

    /** Abre o menu do card na posição de tela informada. */
    public void show(CardView cardView, double screenX, double screenY) {
        Card card = cardView.getCard();
        if (!view.getSelection().contains(card)) {
            view.select(card);
        }
        MenuContext context = new MenuContext(card, cardView, screenX, screenY);
        List<MenuItem> items = new ArrayList<>();
        for (Contributor contributor : contributors) {
            List<MenuItem> group = contributor.itemsFor(context);
            if (group.isEmpty()) {
                continue;
            }
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            items.addAll(group);
        }
        menu.hide();
        if (items.isEmpty()) {
            return;
        }
        menu.getItems().setAll(items);
        menu.show(view, screenX, screenY);
    }

    private boolean insideCard(Node node) {
        return cardViewOf(node).isPresent();
    }

    private Optional<CardView> cardViewOf(Node node) {
        for (Node n = node; n != null && n != view; n = n.getParent()) {
            if (n instanceof CardView cardView) {
                return Optional.of(cardView);
            }
        }
        return Optional.empty();
    }
}
