package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * Atalhos de teclado do canvas.
 *
 * - Letra de cada ferramenta (definida em {@link Tool}): ativa a ferramenta.
 * - Delete / Backspace: exclui a seleção.
 * - Ctrl+D: duplica a seleção.
 * - Esc: sai da edição de texto; senão volta para Selecionar; senão limpa a seleção.
 *
 * Enquanto o texto de um card está sendo editado, só o Esc funciona: as
 * outras teclas pertencem ao texto (digitar "v" não pode trocar de ferramenta).
 * Atalhos de menu (Ctrl+S, Ctrl+O...) são tratados pelo próprio menu.
 */
public class KeyboardController {

    private final Stage stage;
    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final SelectionActionsController selectionActions;

    public KeyboardController(Stage stage, BoardView view, ObjectProperty<Tool> activeTool,
                              SelectionActionsController selectionActions) {
        this.stage = stage;
        this.view = view;
        this.activeTool = activeTool;
        this.selectionActions = selectionActions;
        // Handler (não filtro) na janela: recebe só o que o componente com foco não consumiu.
        stage.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            handleEscape();
            e.consume();
            return;
        }
        if (isEditingText()) {
            return;
        }
        boolean shortcutDown = e.isShortcutDown();
        boolean otherModifiers = e.isAltDown() || e.isShiftDown() || (e.isMetaDown() && !shortcutDown);

        if (shortcutDown && !otherModifiers && e.getCode() == KeyCode.D) {
            selectionActions.duplicateSelection();
            e.consume();
        } else if (!shortcutDown && !otherModifiers) {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                selectionActions.deleteSelection();
                e.consume();
                return;
            }
            for (Tool tool : Tool.values()) {
                if (tool.getShortcut() == e.getCode()) {
                    activeTool.set(tool);
                    e.consume();
                    return;
                }
            }
        }
    }

    private void handleEscape() {
        if (isEditingText()) {
            view.requestFocus();
        } else if (activeTool.get() != Tool.SELECT) {
            activeTool.set(Tool.SELECT);
        } else {
            view.clearSelection();
        }
    }

    private boolean isEditingText() {
        Scene scene = stage.getScene();
        return scene != null && scene.getFocusOwner() instanceof TextInputControl;
    }
}
