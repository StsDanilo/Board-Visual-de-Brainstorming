package com.danilo.boardvisual.view;

import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

/**
 * Mede texto com a fonte que os cards usam na tela (JavaFX), para o
 * {@link CardTextLayout}: assim a exportação SVG quebra as linhas e escolhe a
 * fonte exatamente como a tela.
 *
 * A altura do texto e a largura das palavras (o que o {@link CardTextFit} usa
 * para escolher a fonte) vêm do mesmo medidor da tela; largura de linha, altura
 * da linha e linha de base vêm de um {@link Text} fora da tela. Precisa do
 * JavaFX iniciado (a aplicação; nos testes, {@code Platform.startup}).
 */
public final class ScreenTextMetrics implements CardTextLayout.Metrics {

    private final CardTextFit.JavaFxMeasurer screen = new CardTextFit.JavaFxMeasurer();
    private final Text text = new Text();
    /** Uma linha medida como o TextArea e os Label medem (ver {@link #lineHeight}). */
    private final Text line = new Text("Ág");

    public ScreenTextMetrics() {
        line.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
    }

    private static Font font(double size, boolean bold) {
        return Font.font(CardTextFit.fontFamily(), bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
    }

    @Override
    public double width(String value, double fontSize, boolean bold) {
        text.setFont(font(fontSize, bold));
        text.setWrappingWidth(0);
        text.setText(value);
        return text.getLayoutBounds().getWidth();
    }

    /**
     * O texto do {@code TextArea} do card e o dos {@code Label} usam os limites
     * {@code LOGICAL_VERTICAL_CENTER} (conferido no app: Segoe UI 16 px dá linhas
     * de 19 px com a linha de base a 15 px do topo; a fonte em negrito tem as
     * mesmas medidas na vertical).
     */
    @Override
    public double lineHeight(double fontSize) {
        return lineBounds(fontSize).getHeight();
    }

    @Override
    public double ascent(double fontSize) {
        // Origem na linha de base (o padrão do Text): o topo da linha fica em -ascent.
        return -lineBounds(fontSize).getMinY();
    }

    private Bounds lineBounds(double fontSize) {
        line.setFont(font(fontSize, false));
        return line.getLayoutBounds();
    }

    @Override
    public double textHeight(String value, double fontSize, double wrapWidth) {
        return screen.textHeight(value, fontSize, wrapWidth);
    }

    @Override
    public double wordWidth(String word, double fontSize) {
        return screen.wordWidth(word, fontSize);
    }
}
