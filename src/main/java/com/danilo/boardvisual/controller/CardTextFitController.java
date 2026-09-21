package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.CardTextFit;
import com.danilo.boardvisual.view.CardView;
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
        apply(cardView);
    }

    private static void apply(CardView cardView) {
        Card card = cardView.getCard();
        CardTextFit.Fit fit = CardTextFit.forScreen().fit(card);
        cardView.setTextFontSize(fit.fontSize());
        // O topo fica parado (cresce para baixo); na largura, elipse e losango
        // crescem para os dois lados, mantendo o centro.
        card.setX(card.getX() + (card.getWidth() - fit.width()) / 2);
        card.setWidth(fit.width());
        card.setHeight(fit.height());
    }
}
