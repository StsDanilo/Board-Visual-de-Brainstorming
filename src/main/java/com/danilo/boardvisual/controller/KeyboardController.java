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
 * - Ctrl+Shift+Z: refaz (alternativa ao Ctrl+Y do menu).
 * - Espaço (segurado): arrastar move a visão.
 * - Esc: sai da edição de texto; senão volta para Selecionar; senão limpa a seleção.
 *
 * Enquanto o texto de um card está sendo editado, só o Esc funciona: as
 * outras teclas pertencem ao texto (digitar "v" não pode trocar de ferramenta).
 * Ctrl+Z, Ctrl+Y, Ctrl+A e os atalhos de arquivo ficam nos menus.
 */
public class KeyboardController {

    private final Stage stage;
    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final SelectionActionsController selectionActions;
    private final CanvasController canvas;
    private final MainController main;

    public KeyboardController(Stage stage, BoardView view, ObjectProperty<Tool> activeTool,
                              SelectionActionsController selectionActions, CanvasController canvas,
                              MainController main) {
        this.stage = stage;
        this.view = view;
        this.activeTool = activeTool;
        this.selectionActions = selectionActions;
        this.canvas = canvas;
        this.main = main;
        // Handler (não filtro) na janela: recebe só o que o componente com foco não consumiu.
        stage.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                canvas.setSpaceDown(false);
            }
        });
        // Se a janela perde o foco com Espaço segurado, o "soltar" nunca chega.
        stage.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                canvas.setSpaceDown(false);
            }
        });
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
        boolean altOrMeta = e.isAltDown() || (e.isMetaDown() && !shortcutDown);
        if (altOrMeta) {
            return;
        }

        if (shortcutDown) {
            if (e.getCode() == KeyCode.D && !e.isShiftDown()) {
                selectionActions.duplicateSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.Z && e.isShiftDown()) {
                main.redo();
                e.consume();
            }
            return;
        }
        if (e.isShiftDown()) {
            return;
        }
        switch (e.getCode()) {
            case SPACE -> canvas.setSpaceDown(true);
            case DELETE, BACK_SPACE -> selectionActions.deleteSelection();
            default -> {
                for (Tool tool : Tool.values()) {
                    if (tool.getShortcut() == e.getCode()) {
                        activeTool.set(tool);
                        e.consume();
                    }
                }
                return;
            }
        }
        e.consume();
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
