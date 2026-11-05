package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.StackMove;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import com.danilo.boardvisual.view.StackOrderButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ordem de empilhamento: quem fica por cima de quem. A ordem é a da lista
 * {@code board.getCards()} ({@link Board#reorder}), então é salva no arquivo,
 * volta no desfazer e é a mesma no PDF e no PNG.
 *
 * - Botão "Ordem" (camadas) na barra flutuante, com as quatro ações.
 * - Clique direito no card: as mesmas quatro, num grupo próprio.
 * - Atalhos ({@link #moveFor}): Ctrl+] / Ctrl+[ (um nível), Ctrl+Shift+] /
 *   Ctrl+Shift+[ (topo e fundo), e Ctrl+PgUp / Ctrl+PgDn (com Shift: topo e
 *   fundo) como alternativa que não depende do layout do teclado.
 *
 * Age sobre a seleção inteira, mantendo a ordem relativa entre os
 * selecionados. Cada ação é um passo no desfazer; uma ação que não muda nada
 * (o card já está no topo) não cria passo.
 */
public class StackOrderController {

    /**
     * Atalhos. Os de colchete são {@link KeyCharacterCombination}: o JavaFX acha
     * a tecla pelo <b>caractere</b> no layout atual. No ABNT2 a tecla "[" chega
     * como {@code KeyCode.CLOSE_BRACKET} e a "]" como {@code BACK_SLASH} (mapa
     * de posições do teclado americano); comparar o KeyCode trocaria as ações.
     */
    private static final Map<StackMove, List<KeyCombination>> SHORTCUTS = Map.of(
            StackMove.FORWARD, List.of(
                    new KeyCharacterCombination("]", KeyCombination.SHORTCUT_DOWN),
                    new KeyCodeCombination(KeyCode.PAGE_UP, KeyCombination.SHORTCUT_DOWN)),
            StackMove.BACKWARD, List.of(
                    new KeyCharacterCombination("[", KeyCombination.SHORTCUT_DOWN),
                    new KeyCodeCombination(KeyCode.PAGE_DOWN, KeyCombination.SHORTCUT_DOWN)),
            StackMove.TO_FRONT, List.of(
                    new KeyCharacterCombination("]", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                    new KeyCodeCombination(KeyCode.PAGE_UP, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
            StackMove.TO_BACK, List.of(
                    new KeyCharacterCombination("[", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                    new KeyCodeCombination(KeyCode.PAGE_DOWN, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)));

    private static final StackMove[] MENU_ORDER = {
        StackMove.FORWARD, StackMove.TO_FRONT, StackMove.BACKWARD, StackMove.TO_BACK};

    private final BoardView view;
    private final EditHistory history;

    public StackOrderController(BoardView view, SelectionToolbarView selectionToolbar, EditHistory history,
                                CardContextMenuController contextMenu) {
        this.view = view;
        this.history = history;

        StackOrderButton button = selectionToolbar.getStackOrderButton();
        button.setOnMove(this::reorderSelection);
        button.setAvailability(this::canReorderSelection);
        contextMenu.addContributor(this::menuItems);
    }

    /**
     * Muda a ordem dos cards num passo do desfazer (nenhum passo se nada mudar).
     *
     * @return se a ordem mudou
     */
    public static boolean reorder(EditHistory history, Board board, Collection<Card> cards, StackMove move) {
        if (board == null || !board.canReorder(cards, move)) {
            return false;
        }
        history.perform(() -> board.reorder(cards, move));
        return true;
    }

    /** Aplica o movimento à seleção. */
    public boolean reorderSelection(StackMove move) {
        return reorder(history, view.getBoard(), List.copyOf(view.getSelection()), move);
    }

    private boolean canReorderSelection(StackMove move) {
        Board board = view.getBoard();
        return board != null && board.canReorder(view.getSelection(), move);
    }

    /** O movimento ligado à tecla, se ela for um dos atalhos da ordem. */
    public static Optional<StackMove> moveFor(KeyEvent event) {
        for (Map.Entry<StackMove, List<KeyCombination>> entry : SHORTCUTS.entrySet()) {
            for (KeyCombination combination : entry.getValue()) {
                if (combination.match(event)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------- menu de contexto

    /** As quatro ações; as que não mudariam nada aparecem desabilitadas. */
    private List<MenuItem> menuItems(CardContextMenuController.MenuContext context) {
        return Arrays.stream(MENU_ORDER).map(move -> {
            MenuItem item = new MenuItem(StackOrderButton.label(move));
            item.setOnAction(e -> reorderSelection(move));
            item.setDisable(!canReorderSelection(move));
            return item;
        }).toList();
    }
}
