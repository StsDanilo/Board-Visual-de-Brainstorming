package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.WebLink;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Pequeno popup para digitar o endereço web de um card.
 *
 * <pre>
 * Link do card
 * [ https://...                          ]
 * (mensagem de erro, se houver)
 * [Remover]                [Cancelar] [Salvar]
 * </pre>
 *
 * Enter salva, Esc (ou clicar fora) cancela. Salvar com o campo vazio tira o
 * link. O endereço é conferido com {@link WebLink} ao salvar e, depois do
 * primeiro erro, a cada tecla (o aviso some assim que fica válido). Quem
 * chama recebe só o link já normalizado.
 *
 * As teclas digitadas aqui não chegam aos atalhos da janela: o popup consome
 * todas no fim (senão "r" ou Delete, por exemplo, agiriam no board).
 */
public class LinkEditorPopup {

    private final Popup popup = new Popup();
    private final VBox content = new VBox();
    private final TextField field = new TextField();
    private final Label error = new Label();
    private final Button removeButton = new Button("Remover");
    private final Button cancelButton = new Button("Cancelar");
    private final Button saveButton = new Button("Salvar");

    private Consumer<String> onSave = link -> { };
    private Runnable onRemove = () -> { };
    private boolean showErrorsWhileTyping;

    public LinkEditorPopup() {
        Label title = new Label("Link do card");
        title.getStyleClass().add("panel-section-title");

        field.getStyleClass().add("link-editor-field");
        field.setPromptText("Cole ou digite o endereço (https://...)");
        field.setPrefColumnCount(28);
        field.setOnAction(e -> save());
        field.textProperty().addListener((obs, old, text) -> {
            if (showErrorsWhileTyping) {
                showError(WebLink.check(text).error());
            }
        });

        error.getStyleClass().add("link-editor-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);

        removeButton.getStyleClass().addAll("link-editor-button", "danger");
        cancelButton.getStyleClass().add("link-editor-button");
        saveButton.getStyleClass().addAll("link-editor-button", "primary");
        removeButton.managedProperty().bind(removeButton.visibleProperty());
        removeButton.setOnAction(e -> {
            hide();
            onRemove.run();
        });
        cancelButton.setOnAction(e -> hide());
        saveButton.setOnAction(e -> save());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(removeButton, spacer, cancelButton, saveButton);
        buttons.getStyleClass().add("link-editor-buttons");
        buttons.setAlignment(Pos.CENTER_RIGHT);

        // O popup é outra janela: recebe a classe "root" para herdar as variáveis de cor do app.css.
        content.getStyleClass().addAll("root", "floating-panel", "popup-panel", "link-editor");
        content.getChildren().addAll(title, field, error, buttons);
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
     * @param owner    nó da janela principal (o popup copia o CSS da cena dele)
     * @param current  link atual do card ("" = sem link; aí não há "Remover")
     * @param onSave   recebe o link normalizado ("" = tirar o link)
     * @param onRemove tirar o link
     */
    public void show(Node owner, double screenX, double screenY, String current,
                     Consumer<String> onSave, Runnable onRemove) {
        Scene scene = owner.getScene();
        if (scene == null) {
            return;
        }
        this.onSave = onSave;
        this.onRemove = onRemove;
        showErrorsWhileTyping = false;
        showError(null);
        field.setText(current == null ? "" : current);
        removeButton.setVisible(current != null && !current.isEmpty());
        content.getStylesheets().setAll(scene.getStylesheets());

        Window window = scene.getWindow();
        popup.show(owner, screenX, screenY);
        keepInside(window);
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Campo do endereço (usado pelos roteiros de teste de interface). */
    public TextField getField() {
        return field;
    }

    /** Mensagem de erro exibida ({@code null} se não há). */
    public String getError() {
        return error.isVisible() ? error.getText() : null;
    }

    private void save() {
        WebLink.Check check = WebLink.check(field.getText());
        if (!check.isValid()) {
            showErrorsWhileTyping = true;
            showError(check.error());
            field.requestFocus();
            return;
        }
        hide();
        onSave.accept(check.link());
    }

    private void showError(String message) {
        error.setText(message == null ? "" : message);
        error.setVisible(message != null);
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
}
