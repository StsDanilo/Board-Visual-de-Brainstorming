package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup para escolher o board de destino de um "link para outro board".
 *
 * <pre>
 * Link para outro board
 * Escolha o board de destino:
 * ┌──────────────────────────────────────┐
 * │ Principal              (este board)  │  ← o board do card: desabilitado
 * │   Projeto A                          │
 * │   Principal › Projeto A              │  ← caminho completo, em cinza
 * │     Orçamento                        │
 * │ ...                                  │
 * └──────────────────────────────────────┘
 * [Remover link]                [Cancelar]
 * </pre>
 *
 * A lista é a árvore inteira ({@link BoardTree#entries}), indentada pela
 * profundidade, com o nome do board e o caminho completo embaixo. Clicar numa
 * linha (ou Enter na linha marcada pelas setas) confirma o destino na hora;
 * Esc ou clicar fora cancela. O destino atual vem marcado.
 *
 * Como o {@link LinkEditorPopup}, o popup consome todas as teclas no fim do
 * caminho: Delete ou letras de atalho não agem no board enquanto se escolhe.
 */
public class BoardLinkPickerPopup {

    private static final PseudoClass CURRENT_TARGET = PseudoClass.getPseudoClass("current-target");
    /** Recuo por nível da árvore. */
    private static final double INDENT = 16;
    /** Altura de uma linha (nome + caminho). */
    private static final double ROW_HEIGHT = 42;
    private static final int VISIBLE_ROWS = 8;

    private final Popup popup = new Popup();
    private final VBox content = new VBox();
    private final Label missing = new Label("O board de destino atual não existe mais (o card que o continha foi excluído).");
    private final ListView<BoardTree.Entry> list = new ListView<>();
    private final Button removeButton = new Button("Remover link");
    private final Button cancelButton = new Button("Cancelar");

    private Board current;
    private String currentTargetId;
    private Consumer<Board> onChoose = board -> { };
    private Runnable onRemove = () -> { };

    public BoardLinkPickerPopup() {
        Label title = new Label("Link para outro board");
        title.getStyleClass().add("panel-section-title");
        Label hint = new Label("Escolha o board de destino:");
        hint.getStyleClass().add("board-picker-hint");

        missing.getStyleClass().add("link-editor-error");
        missing.setWrapText(true);
        missing.setMaxWidth(Double.MAX_VALUE);
        missing.managedProperty().bind(missing.visibleProperty());

        list.getStyleClass().add("board-picker-list");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setCellFactory(view -> new EntryCell());
        list.setFocusTraversable(true);
        // Enter confirma a linha marcada (setas mudam a marca; o clique já confirma sozinho).
        list.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                choose(list.getSelectionModel().getSelectedItem());
                e.consume();
            }
        });

        removeButton.getStyleClass().addAll("link-editor-button", "danger");
        cancelButton.getStyleClass().add("link-editor-button");
        removeButton.managedProperty().bind(removeButton.visibleProperty());
        removeButton.setOnAction(e -> {
            hide();
            onRemove.run();
        });
        cancelButton.setOnAction(e -> hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(removeButton, spacer, cancelButton);
        buttons.getStyleClass().add("link-editor-buttons");
        buttons.setAlignment(Pos.CENTER_RIGHT);

        // O popup é outra janela: recebe a classe "root" para herdar as variáveis de cor do app.css.
        content.getStyleClass().addAll("root", "floating-panel", "popup-panel", "link-editor", "board-picker");
        content.getChildren().addAll(title, hint, missing, list, buttons);
        // Esc fecha; e nenhuma tecla daqui segue para os atalhos da janela de baixo.
        content.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            }
        });
        content.addEventHandler(KeyEvent.ANY, KeyEvent::consume);

        popup.getContent().add(content);
        popup.setAutoHide(true);
        popup.setHideOnEscape(false); // o filtro acima trata o Esc
    }

    /**
     * Abre o popup com o canto de cima à esquerda em ({@code screenX}, {@code screenY}).
     *
     * @param owner           nó da janela principal (o popup copia o CSS da cena dele)
     * @param entries         a árvore de boards ({@link BoardTree#entries})
     * @param current         o board onde o card está (aparece desabilitado)
     * @param currentTargetId destino atual do card ({@code null} = sem link; aí não há "Remover")
     * @param onChoose        recebe o board escolhido
     * @param onRemove        tirar o link
     */
    public void show(Node owner, double screenX, double screenY, List<BoardTree.Entry> entries, Board current,
                     String currentTargetId, Consumer<Board> onChoose, Runnable onRemove) {
        Scene scene = owner.getScene();
        if (scene == null) {
            return;
        }
        this.current = current;
        this.currentTargetId = currentTargetId;
        this.onChoose = onChoose;
        this.onRemove = onRemove;
        list.getItems().setAll(entries);
        list.setPrefHeight(Math.min(entries.size(), VISIBLE_ROWS) * ROW_HEIGHT + 2);
        removeButton.setVisible(currentTargetId != null);
        missing.setVisible(currentTargetId != null
                && entries.stream().noneMatch(entry -> entry.board().getId().equals(currentTargetId)));
        content.getStylesheets().setAll(scene.getStylesheets());

        int marked = indexOf(currentTargetId);
        if (marked < 0) {
            marked = firstChoosable();
        }
        list.getSelectionModel().clearSelection();
        if (marked >= 0) {
            list.getSelectionModel().select(marked);
            list.scrollTo(Math.max(0, marked - 2));
        }

        Window window = scene.getWindow();
        popup.show(owner, screenX, screenY);
        keepInside(window);
        Platform.runLater(list::requestFocus);
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** A lista (usada pelos roteiros de teste de interface). */
    public ListView<BoardTree.Entry> getList() {
        return list;
    }

    /** Se a linha pode ser escolhida (o board do próprio card não pode). */
    public boolean isChoosable(BoardTree.Entry entry) {
        return entry != null && entry.board() != current;
    }

    /** Confirma o destino (o que o clique na linha faz); usado também pelos roteiros de teste. */
    public void choose(BoardTree.Entry entry) {
        if (!isChoosable(entry)) {
            return;
        }
        hide();
        onChoose.accept(entry.board());
    }

    private int indexOf(String boardId) {
        if (boardId == null) {
            return -1;
        }
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i).board().getId().equals(boardId)) {
                return i;
            }
        }
        return -1;
    }

    private int firstChoosable() {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (isChoosable(list.getItems().get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Se o popup passou da borda direita ou de baixo da janela, puxa para dentro. */
    private void keepInside(Window window) {
        if (window == null) {
            return;
        }
        double maxX = window.getX() + window.getWidth() - popup.getWidth() - 8;
        double maxY = window.getY() + window.getHeight() - popup.getHeight() - 8;
        if (popup.getX() > maxX) {
            popup.setX(Math.max(window.getX() + 8, maxX));
        }
        if (popup.getY() > maxY) {
            popup.setY(Math.max(window.getY() + 8, maxY));
        }
    }

    /** Linha da lista: nome (indentado pela profundidade) e, embaixo, o caminho completo. */
    private final class EntryCell extends ListCell<BoardTree.Entry> {
        private final Label name = new Label();
        private final Label note = new Label();
        private final Label path = new Label();
        private final VBox box = new VBox();

        EntryCell() {
            getStyleClass().add("board-picker-cell");
            name.getStyleClass().add("board-picker-name");
            note.getStyleClass().add("board-picker-note");
            path.getStyleClass().add("board-picker-path");
            name.setMinWidth(0);
            path.setMinWidth(0);
            HBox top = new HBox(6, name, note);
            top.setAlignment(Pos.BASELINE_LEFT);
            box.getChildren().addAll(top, path);
            box.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && !isEmpty()) {
                    choose(getItem());
                }
            });
        }

        @Override
        protected void updateItem(BoardTree.Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setDisable(false);
                pseudoClassStateChanged(CURRENT_TARGET, false);
                return;
            }
            name.setText(entry.name());
            path.setText(entry.label());
            boolean here = entry.board() == current;
            boolean target = entry.board().getId().equals(currentTargetId);
            note.setText(here ? "(este board)" : target ? "(destino atual)" : "");
            box.setPadding(new Insets(0, 0, 0, entry.depth() * INDENT));
            setDisable(here);
            pseudoClassStateChanged(CURRENT_TARGET, target);
            setGraphic(box);
        }
    }
}
