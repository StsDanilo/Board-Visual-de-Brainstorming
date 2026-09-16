package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.view.CardView;

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
        BoardSnapshot[] before = new BoardSnapshot[1];
        cardView.textFocusedProperty().addListener((obs, wasFocused, focused) -> {
            if (focused) {
                before[0] = history.capture();
            } else {
                history.record(before[0]);
                before[0] = null;
            }
        });
    }
}
