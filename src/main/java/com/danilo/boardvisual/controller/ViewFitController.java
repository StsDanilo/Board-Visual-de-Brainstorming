package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.ViewFit;
import com.danilo.boardvisual.view.BoardView;

import java.util.function.Consumer;

/**
 * "Zoom para caber tudo" (Shift+1) e "caber a seleção" (Shift+2), do menu Exibir.
 *
 * Só muda a visão (pan e zoom) do board que está na tela; nada disso entra no
 * desfazer, e cada nível de board aninhado guarda a própria visão
 * ({@link NavigationController}). A conta está em {@link ViewFit}.
 *
 * Os atalhos são aceleradores dos itens do menu (como Ctrl+0): editando o texto
 * de um card ou da busca, o campo de texto fica com as teclas (digitar "!" não ajusta a visão).
 */
public class ViewFitController {

    private final BoardView view;
    private Consumer<String> notifier = message -> { };

    public ViewFitController(BoardView view) {
        this.view = view;
    }

    /** Quem mostra os avisos curtos (a barra de status). */
    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier == null ? message -> { } : notifier;
    }

    /** Mostra todos os cards do board; board vazio volta a 100%. */
    public void fitAll() {
        view.fitAll().ifPresent(this::warnIfTooBig);
    }

    /** Mostra só os cards selecionados; sem seleção, só avisa. */
    public void fitSelection() {
        if (view.getSelection().isEmpty()) {
            notifier.accept("Selecione um ou mais cards para ajustar a visão a eles.");
            return;
        }
        view.fitSelection().ifPresent(this::warnIfTooBig);
    }

    private void warnIfTooBig(ViewFit.Result result) {
        if (result.limit() == ViewFit.Limit.MIN_ZOOM) {
            notifier.accept("O conteúdo é maior do que cabe na tela no zoom mínimo (20%): mostrando o centro dele.");
        }
    }
}
