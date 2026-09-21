package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Desfazer/refazer por pilha de estados: antes de cada ação do usuário,
 * guarda uma foto do board ({@link BoardSnapshot}); desfazer restaura a foto.
 *
 * Três formas de registrar uma ação:
 * - {@link #perform}: para ações instantâneas (criar, excluir, mudar cor...).
 * - {@link #capture} no início + {@link #record} no fim: para gestos que
 *   duram vários eventos (arrastar).
 * - {@link #beginEdit} / {@link #endEdit}: sessões de edição de texto, que
 *   podem ser "atravessadas" por ações instantâneas (ex.: digitando num
 *   painel, clicar para remover um item). Nesse caso o que foi digitado até
 *   ali vira um passo, a ação vira outro, e a sessão continua depois dela.
 * Ações que não mudam nada não geram passo no histórico.
 *
 * Com boards aninhados, cada board tem as próprias pilhas: desfazer age no
 * board que está na tela. Ao trocar de board, chame {@link #boardChanged()}.
 */
public class EditHistory {

    private static final int MAX_STEPS = 200;

    private final Supplier<Board> board;
    /** Pilhas por board. Fraco: board excluído (e sem referências) some daqui sozinho. */
    private final Map<Board, Stacks> stacksByBoard = new WeakHashMap<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");

    /** Foto do início da sessão de edição em andamento, ou {@code null}. */
    private BoardSnapshot pendingEdit;

    private static final class Stacks {
        final Deque<BoardSnapshot> undo = new ArrayDeque<>();
        final Deque<BoardSnapshot> redo = new ArrayDeque<>();
    }

    public EditHistory(Supplier<Board> board) {
        this.board = board;
    }

    /** Executa uma alteração no board registrando-a como um passo desfazível. */
    public void perform(Runnable change) {
        boolean editing = pendingEdit != null;
        if (editing) {
            endEdit();
        }
        BoardSnapshot before = capture();
        change.run();
        record(before);
        if (editing) {
            beginEdit();
        }
    }

    /** Início de uma edição de texto (campo ganhou foco). */
    public void beginEdit() {
        if (pendingEdit == null) {
            pendingEdit = capture();
        }
    }

    /** Fim da edição de texto (campo perdeu foco): registra o que mudou. */
    public void endEdit() {
        BoardSnapshot before = pendingEdit;
        pendingEdit = null;
        record(before);
    }

    /** Foto do estado atual, para uma ação que ainda vai acontecer. */
    public BoardSnapshot capture() {
        Board current = board.get();
        return current == null ? null : current.snapshot();
    }

    /** Registra o passo iniciado em {@code before}, se algo mudou desde então. */
    public void record(BoardSnapshot before) {
        Board current = board.get();
        if (before == null || current == null || before.equals(current.snapshot())) {
            return;
        }
        Stacks stacks = stacksOf(current);
        stacks.undo.push(before);
        if (stacks.undo.size() > MAX_STEPS) {
            stacks.undo.removeLast();
        }
        stacks.redo.clear();
        updateFlags();
    }

    public void undo() {
        move(true);
    }

    public void redo() {
        move(false);
    }

    /** O board na tela mudou (navegação entre boards aninhados). */
    public void boardChanged() {
        pendingEdit = null;
        updateFlags();
    }

    /** Esquece todo o histórico (ex.: ao abrir ou criar outro board). */
    public void clear() {
        pendingEdit = null;
        stacksByBoard.clear();
        updateFlags();
    }

    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    private void move(boolean undo) {
        Board current = board.get();
        boolean editing = pendingEdit != null;
        if (editing) {
            endEdit(); // o que foi digitado até aqui vira um passo antes de desfazer
        }
        if (current != null) {
            Stacks stacks = stacksOf(current);
            Deque<BoardSnapshot> from = undo ? stacks.undo : stacks.redo;
            Deque<BoardSnapshot> to = undo ? stacks.redo : stacks.undo;
            if (!from.isEmpty()) {
                to.push(current.snapshot());
                current.restore(from.pop());
                updateFlags();
            }
        }
        if (editing) {
            beginEdit();
        }
    }

    private Stacks stacksOf(Board target) {
        return stacksByBoard.computeIfAbsent(target, b -> new Stacks());
    }

    private void updateFlags() {
        Board current = board.get();
        Stacks stacks = current == null ? null : stacksByBoard.get(current);
        canUndo.set(stacks != null && !stacks.undo.isEmpty());
        canRedo.set(stacks != null && !stacks.redo.isEmpty());
    }
}
