package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.view.CardTextFit;
import com.danilo.boardvisual.view.CardView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.InvalidationListener;

/**
 * Mantém o texto de cada card inteiro à vista: ajusta a fonte (automática) e
 * faz o card crescer quando o texto não cabe (ver {@link CardTextFit}).
 *
 * Recalcula quando muda algo que afeta o espaço do texto: o texto, a fonte
 * escolhida, o tamanho definido à mão, o formato ou o botão embaixo do card.
 *
 * O crescimento muda o tamanho exibido no modelo. Durante a digitação isso
 * entra na mesma sessão de edição do desfazer; ao desfazer, a foto traz o
 * tamanho de volta e o recálculo chega ao mesmo resultado.
 */
public class CardTextFitController {

    public void attach(CardView cardView) {
        Card card = cardView.getCard();
        InvalidationListener refit = obs -> apply(cardView);
        card.textProperty().addListener(refit);
        card.fontSizeProperty().addListener(refit);
        card.baseWidthProperty().addListener(refit);
        card.baseHeightProperty().addListener(refit);
        card.shapeProperty().addListener(refit);
        card.panelModeProperty().addListener(refit);
        card.childBoardProperty().addListener(refit);
        card.imageFileProperty().addListener(refit);
        // Marcadores (link) ocupam a borda de cima: o texto começa abaixo deles.
        for (Observable dependency : CardMarkers.dependencies(card)) {
            dependency.addListener(refit);
        }
        // Legenda vazia de um card de imagem: não há espaço para o texto, então
        // enquanto ela é editada o card abre uma linha (com o texto de exemplo).
        // Ao ganhar o foco, espera o TextEditController tirar a foto do início da
        // edição; ao perder, encolhe antes de ele registrar: sem passo à toa no desfazer.
        cardView.textFocusedProperty().addListener((obs, was, focused) -> {
            if (card.hasImage()) {
                if (focused) {
                    Platform.runLater(() -> apply(cardView));
                } else {
                    apply(cardView);
                }
            }
        });
        apply(cardView);
    }

    private static void apply(CardView cardView) {
        Card card = cardView.getCard();
        String text = card.getText();
        if (card.hasImage() && text.isEmpty() && cardView.isTextFocused()) {
            text = CardView.CAPTION_PROMPT_TEXT;
        }
        CardTextFit.Fit fit = CardTextFit.forScreen().fit(card, card.getBaseWidth(), card.getBaseHeight(), text);
        cardView.setTextFontSize(fit.fontSize());
        // O topo fica parado (cresce para baixo); na largura, elipse e losango
        // crescem para os dois lados, mantendo o centro.
        card.setX(card.getX() + (card.getWidth() - fit.width()) / 2);
        card.setWidth(fit.width());
        card.setHeight(fit.height());
    }
}
