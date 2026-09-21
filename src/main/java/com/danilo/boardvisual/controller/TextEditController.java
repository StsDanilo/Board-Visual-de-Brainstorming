package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.view.CardView;
import javafx.beans.value.ObservableBooleanValue;

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
