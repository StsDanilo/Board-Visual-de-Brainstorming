package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.SelectionToolbarView;
import com.danilo.boardvisual.view.SelectionToolbarView.LockState;
import javafx.beans.InvalidationListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.MenuItem;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Travar e destravar cards (cadeado, estilo Canva): proteção contra mover ou
 * excluir sem querer.
 *
 * - Botão de cadeado na barra flutuante, item "Travar"/"Destravar" no clique
 *   direito e {@code Ctrl+L} ({@link KeyboardController}). Valem para a seleção
 *   inteira: se todos estão travados, destrava; senão (nenhum ou alguns), trava
 *   todos. Com seleção mista o botão aparece "misto" (tracejado).
 * - Cada travar/destravar é um passo no desfazer.
 *
 * O que um card travado não faz fica em quem faz a ação: não se move
 * ({@link CardDragController}), não mostra as alças de redimensionar
 * ({@link CardResizeController}), não troca de formato nem é excluído
 * ({@link SelectionActionsController}). Continua livre: selecionar, editar o
 * texto, cor, tamanho do texto, setas, links, painel, entrar no board, ordem
 * de empilhamento e duplicar (a cópia nasce destravada).
 */
public class LockController {

    private final BoardView view;
    private final SelectionToolbarView selectionToolbar;
    private final EditHistory history;

    /** Cards selecionados observados, para o botão refletir o estado (inclusive ao desfazer). */
    private final Set<Card> observed = new HashSet<>();
    private final InvalidationListener lockListener = obs -> refreshToolbar();

    public LockController(BoardView view, SelectionToolbarView selectionToolbar, EditHistory history,
                          CardContextMenuController contextMenu) {
        this.view = view;
        this.selectionToolbar = selectionToolbar;
        this.history = history;

        selectionToolbar.getLockButton().setOnAction(e -> toggleSelection());
        view.getSelection().addListener((SetChangeListener<Card>) change -> observeSelection());
        contextMenu.addContributor(this::menuItems);
        observeSelection();
    }

    /** Trava ou destrava os cards num passo do desfazer (nenhum passo se já estavam assim). */
    public static void setLocked(EditHistory history, Collection<Card> cards, boolean locked) {
        history.perform(() -> cards.forEach(card -> card.setLocked(locked)));
    }

    /** O que o botão (e o Ctrl+L) faz: todos travados → destrava; senão → trava todos. */
    public static boolean shouldLock(Collection<Card> cards) {
        return !cards.isEmpty() && !cards.stream().allMatch(Card::isLocked);
    }

    /** Travar/destravar a seleção (botão, menu e Ctrl+L). */
    public void toggleSelection() {
        List<Card> cards = List.copyOf(view.getSelection());
        if (!cards.isEmpty()) {
            setLocked(history, cards, shouldLock(cards));
        }
    }

    // ------------------------------------------------------- menu de contexto

    private List<MenuItem> menuItems(CardContextMenuController.MenuContext context) {
        boolean lock = shouldLock(view.getSelection());
        MenuItem item = new MenuItem(lock ? "Travar" : "Destravar");
        item.setOnAction(e -> toggleSelection());
        return List.of(item);
    }

    // ------------------------------------------------------- barra flutuante

    private void observeSelection() {
        observed.forEach(card -> card.lockedProperty().removeListener(lockListener));
        observed.clear();
        observed.addAll(view.getSelection());
        observed.forEach(card -> card.lockedProperty().addListener(lockListener));
        refreshToolbar();
    }

    private void refreshToolbar() {
        long locked = view.getSelection().stream().filter(Card::isLocked).count();
        LockState state = locked == 0 ? LockState.UNLOCKED
                : locked == view.getSelection().size() ? LockState.LOCKED : LockState.MIXED;
        selectionToolbar.showLock(state);
    }
}
