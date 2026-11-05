package com.danilo.boardvisual.view;

/**
 * Medida previsível, sem JavaFX, para os testes do texto (quebra de linhas,
 * exportação SVG): cada caractere tem meia fonte de largura (0,6 em negrito),
 * a linha tem 1,25 fonte de altura e a linha de base fica a 1 fonte do topo.
 * A altura do texto vem da própria quebra do {@link CardTextLayout}, então o
 * {@link CardTextFit} e a quebra concordam, como na tela.
 */
public final class FixedTextMetrics implements CardTextLayout.Metrics {

    public static final double CHAR = 0.5;
    public static final double BOLD_CHAR = 0.6;
    public static final double LINE = 1.25;

    @Override
    public double width(String text, double fontSize, boolean bold) {
        return text.codePointCount(0, text.length()) * fontSize * (bold ? BOLD_CHAR : CHAR);
    }

    @Override
    public double lineHeight(double fontSize) {
        return fontSize * LINE;
    }

    @Override
    public double ascent(double fontSize) {
        return fontSize;
    }
}
