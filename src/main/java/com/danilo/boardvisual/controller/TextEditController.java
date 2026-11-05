package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.CardView;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Registra a edição de texto de um card no histórico.
 *
 * Enquanto o texto está sendo editado, Ctrl+Z desfaz letra a letra dentro do
 * próprio campo (comportamento nativo do TextArea). Ao sair da edição, tudo o
 * que foi digitado vira um único passo no desfazer do board.
 */
public class TextEditController {

    private final EditHistory history;

    public TextEditController(EditHistory history) {
        this.history = history;
    }

    public void attach(CardView cardView) {
        track(cardView.textFocusedProperty());
        // Card de imagem sem legenda não tem área de texto para clicar: duplo clique abre a legenda.
        // Seção: o título só vira campo de texto com o duplo clique (o clique simples arrasta).
        cardView.getBody().addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            Card card = cardView.getCard();
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && (card.hasImage() || card.isFrame())
                    && !cardView.isTextFocused()) {
                cardView.focusText();
                e.consume();
            }
        });
        // O título da seção é de uma linha: Enter termina a edição (como o Esc).
        cardView.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && cardView.getCard().isFrame() && cardView.isTextFocused()) {
                cardView.endTextEdit();
                e.consume();
            }
        });
    }

    /** Trata como uma sessão de edição o período em que {@code editing} for verdadeiro. */
    public void track(ObservableBooleanValue editing) {
        editing.addListener((obs, was, now) -> {
            if (now) {
                history.beginEdit();
            } else {
                history.endEdit();
            }
        });
    }
}
