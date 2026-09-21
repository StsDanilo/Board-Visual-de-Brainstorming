package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTextFitTest {

    /**
     * Medida previsível, sem JavaFX: cada caractere tem meia fonte de largura,
     * cada linha 1,25 fonte de altura, quebra por palavra.
     */
    static final class FixedMeasurer implements CardTextFit.TextMeasurer {

        @Override
        public double textHeight(String text, double fontSize, double wrapWidth) {
            int lines = 0;
            for (String paragraph : text.split("\n", -1)) {
                lines += linesOf(paragraph, Math.max(1, (int) (wrapWidth / (fontSize * 0.5))));
            }
            return lines * fontSize * 1.25;
        }

        private static int linesOf(String paragraph, int charsPerLine) {
            int lines = 1;
            int used = 0;
            for (String word : paragraph.split(" ")) {
                int length = word.length();
                int needed = used == 0 ? length : used + 1 + length;
                if (needed <= charsPerLine) {
                    used = needed;
                } else {
                    lines++;
                    used = length;
                    while (used > charsPerLine) { // palavra maior que a linha é quebrada
                        lines++;
                        used -= charsPerLine;
                    }
                }
            }
            return lines;
        }

        @Override
        public double wordWidth(String word, double fontSize) {
            return word.length() * fontSize * 0.5;
        }
    }

    private static final String LONG_TEXT = "uma ideia comprida ".repeat(40).trim();

    private final FixedMeasurer measurer = new FixedMeasurer();
    private final CardTextFit fit = new CardTextFit(measurer);

    private static Card rectangle(String text) {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60); // 200 × 120
        card.setText(text);
        return card;
    }

    @Test
    void shortTextUsesMaximumAutomaticFont() {
        CardTextFit.Fit result = fit.fit(rectangle("Oi"));

        assertEquals(CardTextFit.MAX_FONT_SIZE, result.fontSize());
        assertEquals(200, result.width());
        assertEquals(120, result.height());
    }

    @Test
    void automaticFontShrinksBeforeCardGrows() {
        CardTextFit.Fit result = fit.fit(rectangle("uma ideia comprida ".repeat(4).trim()));

        assertTrue(result.fontSize() < CardTextFit.MAX_FONT_SIZE);
        assertTrue(result.fontSize() > CardTextFit.MIN_FONT_SIZE);
        assertEquals(120, result.height(), "ainda cabe sem crescer");
    }

    @Test
    void automaticFontNeverBreaksAWordInTheMiddle() {
        // 20 letras: a 24 px ocupariam 240 px, mais que os 169 px de área útil (a 17 px, 170).
        CardTextFit.Fit result = fit.fit(rectangle("Paralelepipedozinhos"));

        assertEquals(16.5, result.fontSize());
    }

    @Test
    void rectangleGrowsOnlyInHeightAtMinimumFont() {
        CardTextFit.Fit result = fit.fit(rectangle(LONG_TEXT));

        assertEquals(CardTextFit.MIN_FONT_SIZE, result.fontSize());
        assertEquals(200, result.width());
        assertTrue(result.height() > 120);
        // O texto cabe na altura calculada (recuo 12 + 10, borda 2, folga 3; na largura, também a barra de rolagem: 6).
        double textHeight = measurer.textHeight(LONG_TEXT, CardTextFit.MIN_FONT_SIZE, 200 - 20 - 2 - 6 - 3);
        assertTrue(textHeight + 22 + 2 + 3 <= result.height());
    }

    @Test
    void deletingTextShrinksBackToManualSize() {
        Card card = rectangle(LONG_TEXT);
        card.resize(240, 150);
        assertTrue(fit.fit(card).height() > 150);

        card.setText("Oi");

        CardTextFit.Fit result = fit.fit(card);
        assertEquals(240, result.width());
        assertEquals(150, result.height());
    }

    @Test
    void ellipseGrowsKeepingProportion() {
        Card card = Card.create(CardShape.ELLIPSE, 0, 0);
        card.setText(LONG_TEXT);

        CardTextFit.Fit result = fit.fit(card);

        assertTrue(result.width() > card.getBaseWidth());
        assertEquals(card.getBaseWidth() / card.getBaseHeight(), result.width() / result.height(), 1e-9);
    }

    @Test
    void manualFontIsKeptAndCardGrowsWhenNeeded() {
        Card card = rectangle("Oi");
        card.setFontSize(14);
        assertEquals(new CardTextFit.Fit(14, 200, 120), fit.fit(card));

        card.setText("uma ideia comprida ".repeat(3).trim());
        card.setFontSize(48);
        CardTextFit.Fit result = fit.fit(card);
        assertEquals(48, result.fontSize());
        assertTrue(result.height() > 120);
    }

    @Test
    void emptyCardNeverGrows() {
        Card card = rectangle("");
        card.resize(60, 40);

        CardTextFit.Fit result = fit.fit(card);

        assertEquals(60, result.width());
        assertEquals(40, result.height());
    }

    @Test
    void buttonSpaceIsReserved() {
        String text = "uma ideia comprida ".repeat(4).trim();
        double plain = fit.fit(CardShape.RECTANGLE, 200, 120, false, text, Card.AUTO_FONT_SIZE).fontSize();
        double withButton = fit.fit(CardShape.RECTANGLE, 200, 120, true, text, Card.AUTO_FONT_SIZE).fontSize();

        assertTrue(withButton < plain);
    }
}
