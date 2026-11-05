package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.StackMove;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Atalhos de teclado do canvas.
 *
 * - Letra de cada ferramenta (definida em {@link Tool}): ativa a ferramenta.
 * - Delete / Backspace: exclui a seta selecionada, se houver
 *   ({@link ConnectionEditController}); senão, a seleção de cards (menos os travados).
 * - Ctrl+L: trava / destrava a seleção ({@link LockController}).
 * - Ctrl+D: duplica a seleção.
 * - Ctrl+] / Ctrl+[ (ou Ctrl+PgUp / Ctrl+PgDn): trazer para frente / enviar
 *   para trás um nível; com Shift, para o topo / para o fundo
 *   ({@link StackOrderController}).
 * - Ctrl+Shift+Z: refaz (alternativa ao Ctrl+Y do menu).
 * - Espaço (segurado): arrastar move a visão.
 * - Alt+←: volta ao board anterior (boards aninhados; depois de um link para
 *   outro board, ao board de onde se saiu).
 * - Na apresentação (F5) este controller não age: as teclas são do {@link PresentationController}.
 * - Esc: sai da edição de texto; senão fecha a busca (Ctrl+F) aberta; senão fecha o painel
 *   flutuante aberto; senão volta para Selecionar; senão limpa a seleção (de cards e de seta).
 *   No campo do texto da seta, o Esc é do próprio campo (cancela a edição); no campo da
 *   busca, também (fecha a busca: {@link SearchController}).
 *
 * Enquanto o texto de um card (ou de uma seta) está sendo editado, só o Esc
 * funciona: as outras teclas pertencem ao texto (digitar "v" não pode trocar de ferramenta).
 * Ctrl+Z, Ctrl+Y, Ctrl+A, Ctrl+X/C/V (recortar, copiar e colar cards, ou a imagem
 * do sistema: {@link ClipboardController}), Ctrl+F (busca), Shift+1 / Shift+2 (zoom para caber
 * tudo / a seleção, {@link ViewFitController}) e os atalhos de arquivo ficam nos menus.
 */
public class KeyboardController {

    private final Stage stage;
    private final BoardView view;
    private final ObjectProperty<Tool> activeTool;
    private final SelectionActionsController selectionActions;
    private final CanvasController canvas;
    private final MainController main;
    private final PanelController panels;
    private final NavigationController navigation;
    private final StackOrderController stackOrder;
    private final LockController locks;
    private final ConnectionEditController connectionEdits;
    private final SearchController search;

    public KeyboardController(Stage stage, BoardView view, ObjectProperty<Tool> activeTool,
                              SelectionActionsController selectionActions, CanvasController canvas,
                              PanelController panels, NavigationController navigation,
                              StackOrderController stackOrder, LockController locks,
                              ConnectionEditController connectionEdits, SearchController search,
                              MainController main) {
        this.stage = stage;
        this.search = search;
        this.connectionEdits = connectionEdits;
        this.stackOrder = stackOrder;
        this.locks = locks;
        this.view = view;
        this.activeTool = activeTool;
        this.selectionActions = selectionActions;
        this.canvas = canvas;
        this.main = main;
        this.panels = panels;
        this.navigation = navigation;
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
        if (view.isPresenting()) {
            return; // na apresentação as teclas são do PresentationController (filtro da janela)
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            handleEscape();
            e.consume();
            return;
        }
        if (isEditingText()) {
            return;
        }
        boolean shortcutDown = e.isShortcutDown();
        if (e.isAltDown() && !shortcutDown && e.getCode() == KeyCode.LEFT) {
            navigation.back();
            e.consume();
            return;
        }
        boolean altOrMeta = e.isAltDown() || (e.isMetaDown() && !shortcutDown);
        if (altOrMeta) {
            return;
        }

        if (shortcutDown) {
            Optional<StackMove> move = StackOrderController.moveFor(e);
            if (move.isPresent()) {
                stackOrder.reorderSelection(move.get());
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.D && !e.isShiftDown()) {
                selectionActions.duplicateSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.L && !e.isShiftDown()) {
                locks.toggleSelection();
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
            case DELETE, BACK_SPACE -> {
                // Seta selecionada: exclui a seta (seta e cards não ficam selecionados juntos).
                if (!connectionEdits.deleteSelected()) {
                    selectionActions.deleteSelection();
                }
            }
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
        } else if (search.closeIfOpen()) {
            return; // a busca aberta (com o foco no canvas): o Esc fecha ela e tira os destaques
        } else if (panels.closeIfOpen()) {
            return;
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
