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
 * Duas formas de registrar uma ação:
 * - {@link #perform}: para ações instantâneas (criar, excluir, mudar cor...).
 * - {@link #capture} no início + {@link #record} no fim: para ações que
 *   duram vários eventos (arrastar, editar texto).
 * Ações que não mudam nada não geram passo no histórico.
 */
public class EditHistory {

    private static final int MAX_STEPS = 200;

    private final Supplier<Board> board;
    private final Deque<BoardSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<BoardSnapshot> redoStack = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");

    public EditHistory(Supplier<Board> board) {
        this.board = board;
    }

    /** Executa uma alteração no board registrando-a como um passo desfazível. */
    public void perform(Runnable change) {
        BoardSnapshot before = capture();
        change.run();
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
        if (from.isEmpty() || current == null) {
            return;
        }
        to.push(current.snapshot());
        current.restore(from.pop());
        updateFlags();
    }

    private void updateFlags() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }
}
