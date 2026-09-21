package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
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
 */
public class EditHistory {

    private static final int MAX_STEPS = 200;

    private final Supplier<Board> board;
    private final Deque<BoardSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<BoardSnapshot> redoStack = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");

    /** Foto do início da sessão de edição em andamento, ou {@code null}. */
    private BoardSnapshot pendingEdit;

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
        undoStack.push(before);
        if (undoStack.size() > MAX_STEPS) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateFlags();
    }

    public void undo() {
        move(undoStack, redoStack);
    }

    public void redo() {
        move(redoStack, undoStack);
    }

    /** Esquece todo o histórico (ex.: ao abrir ou criar outro board). */
    public void clear() {
        pendingEdit = null;
        undoStack.clear();
        redoStack.clear();
        updateFlags();
    }

    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    private void move(Deque<BoardSnapshot> from, Deque<BoardSnapshot> to) {
        Board current = board.get();
        boolean editing = pendingEdit != null;
        if (editing) {
            endEdit(); // o que foi digitado até aqui vira um passo antes de desfazer
        }
        if (!from.isEmpty() && current != null) {
            to.push(current.snapshot());
            current.restore(from.pop());
            updateFlags();
        }
        if (editing) {
            beginEdit();
        }
    }

    private void updateFlags() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }
}
