package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.PanelMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quebra de linhas e posição do texto, sem JavaFX (medida fixa: {@link FixedTextMetrics}). */
class CardTextLayoutTest {

    private final FixedTextMetrics metrics = new FixedTextMetrics();
    private final CardTextLayout layout = new CardTextLayout(metrics);

    /** Como o CardTextFitController faz na tela: o card cresce até o texto caber. */
    private void fit(Card card) {
        CardTextFit.Fit fit = new CardTextFit(metrics).fit(card);
        card.setWidth(fit.width());
        card.setHeight(fit.height());
    }

    private List<String> wrap(String text, double width) {
        return CardTextLayout.wrap(metrics, text, 10, false, width); // 5 px por caractere
    }

    // ------------------------------------------------------------ quebra

    @Test
    void eachParagraphStartsANewLineAndEmptyParagraphsAreEmptyLines() {
        assertEquals(List.of("a", "", "b"), wrap("a\n\nb", 100));
        assertEquals(List.of("a", "b"), wrap("a\r\nb", 100));
    }

    @Test
    void breaksAtTheLastSpaceThatStillFits() {
        assertEquals(List.of("uma ideia", "comprida"), wrap("uma ideia comprida", 50));
    }

    @Test
    void wordLongerThanTheLineIsBrokenInTheMiddle() {
        assertEquals(List.of("abcdefghij", "klmnopqrst", "uvwxyz"), wrap("abcdefghijklmnopqrstuvwxyz", 50));
    }

    @Test
    void theSpaceThatPassesTheWidthHangsAtTheEndOfTheLine() {
        assertEquals(List.of("abc", "def"), wrap("abc def", 20));
        assertEquals(List.of("abc", "def"), wrap("abc  def", 20));
    }

    @Test
    void severalSpacesAtTheBreakGoToTheNextLineLikeJavaFx() {
        // Parado num espaço, o JavaFX volta até o começo da palavra anterior; como ela
        // é o começo da linha, corta onde passou da largura: os espaços restantes descem.
        assertEquals(List.of("abc", "", "def"), wrap("abc   def", 20));
    }

    @Test
    void doesNotBreakAfterAHyphenLikeJavaFx() {
        assertEquals(List.of("guarda-c", "huva"), wrap("guarda-chuva", 40));
        assertEquals(List.of("um", "guarda-chuva"), wrap("um guarda-chuva", 60));
    }

    @Test
    void zeroWidthMeansNoAutomaticWrapLikeJavaFx() {
        assertEquals(List.of("uma ideia comprida", "x"), wrap("uma ideia comprida\nx", 0));
    }

    @Test
    void atLeastOneCharacterPerLineEvenIfNothingFits() {
        assertEquals(List.of("a", "b"), wrap("ab", 1));
    }

    @Test
    void neverSplitsASurrogatePair() {
        String emoji = "😀"; // um caractere fora do BMP (dois char)
        for (String line : wrap(emoji.repeat(5), 12)) {
            assertEquals(line.codePointCount(0, line.length()) * 2, line.length(), "par inteiro: " + line);
        }
    }

    @Test
    void longTextLinesNeverPassTheWidthAndKeepEveryWord() {
        String text = "uma ideia comprida ".repeat(40).trim();
        List<String> lines = wrap(text, 178);
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(metrics.width(line, 10, false) <= 178, line);
        }
        assertEquals(text, String.join(" ", lines));
    }

    @Test
    void ellipsizeKeepsTheLongestStartThatFitsWithTheDots() {
        assertEquals("Título comprido", CardTextLayout.ellipsize(metrics, "Título comprido", 10, false, 100));
        assertEquals("Títul...", CardTextLayout.ellipsize(metrics, "Título comprido", 10, false, 40));
        assertEquals("", CardTextLayout.ellipsize(metrics, "Título comprido", 10, false, 10));
    }

    // ------------------------------------------------------------ posição no card

    @Test
    void shortTextUsesTheScreenFontAndIsCenteredAtTheTopOfTheTextArea() {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60); // 200 × 120
        card.setText("Ideia");
        fit(card);

        CardTextLayout.Block block = layout.cardText(card);

        assertEquals(CardTextFit.MAX_FONT_SIZE, block.fontSize(), "a mesma fonte automática da tela");
        assertEquals(CardTextLayout.Anchor.MIDDLE, block.anchor());
        assertEquals(1, block.lines().size());
        CardTextLayout.Line line = block.lines().get(0);
        assertEquals("Ideia", line.text());
        assertEquals(100, line.x(), 1e-9, "meio da área do texto = meio do card");
        assertEquals(1 + 12 + 24, line.baseline(), 1e-9, "borda + recuo de cima + linha de base");
    }

    @Test
    void wrappedLineIsCenteredWithItsHangingSpaceLikeJavaFx() {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60); // 200 × 120: área do texto de 178
        card.setFontSize(10); // 5 px por caractere
        card.setText("a".repeat(20) + " " + "b".repeat(20));
        fit(card);

        List<CardTextLayout.Line> lines = layout.cardText(card).lines();

        assertEquals("a".repeat(20), lines.get(0).text());
        assertEquals(100 - 5 / 2.0, lines.get(0).x(), 1e-9, "o espaço pendurado conta no centro (meio espaço à esquerda)");
        assertEquals(100, lines.get(1).x(), 1e-9, "a última linha não tem espaço pendurado");
    }

    @Test
    void manualFontSizeIsKept() {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60);
        card.setText("Ideia");
        card.setFontSize(16);
        fit(card);
        assertEquals(16, layout.cardText(card).fontSize());
    }

    @Test
    void longTextFitsInsideTheGrownCard() {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60);
        String text = "uma ideia comprida ".repeat(40).trim();
        card.setText(text);
        fit(card);

        CardTextLayout.Block block = layout.cardText(card);

        assertEquals(CardTextFit.MIN_FONT_SIZE, block.fontSize());
        assertTrue(block.lines().size() > 5);
        double wrapWidth = card.getWidth() - 2 - 20;
        for (CardTextLayout.Line line : block.lines()) {
            assertTrue(metrics.width(line.text(), block.fontSize(), false) <= wrapWidth, line.text());
        }
        CardTextLayout.Line last = block.lines().get(block.lines().size() - 1);
        double bottom = last.baseline() - metrics.ascent(block.fontSize()) + metrics.lineHeight(block.fontSize());
        assertTrue(bottom <= card.getHeight() - 1 - 10, "o texto inteiro cabe no card que cresceu");
        assertEquals(text, String.join(" ", block.lines().stream().map(CardTextLayout.Line::text).toList()));
    }

    @Test
    void ellipseAndDiamondCenterTheTextInsideTheShape() {
        for (CardShape shape : List.of(CardShape.ELLIPSE, CardShape.DIAMOND)) {
            Card card = Card.create(shape, 100, 60);
            card.setText("Ideia");
            fit(card);
            CardTextLayout.Line line = layout.cardText(card).lines().get(0);
            assertEquals(card.getWidth() / 2, line.x(), 1e-9, shape.name());
            CardGeometry.Padding padding = CardGeometry.contentPadding(shape, card.getWidth(), card.getHeight(),
                    false, 0, 0);
            // Formato com -fx-shape: a borda não entra no recuo, e o JavaFX arredonda a posição.
            assertEquals(Math.round(padding.top()) + layout.cardText(card).fontSize(), line.baseline(), 1e-9);
        }
    }

    @Test
    void panelButtonAndMarkersPushTheTextAwayLikeOnScreen() {
        Card plain = Card.create(CardShape.RECTANGLE, 100, 60);
        plain.setText("Ideia");
        fit(plain);
        Card withLink = Card.create(CardShape.RECTANGLE, 100, 60);
        withLink.setText("Ideia");
        withLink.setLink("https://exemplo.com");
        withLink.setPanelMode(PanelMode.TEXT);
        fit(withLink);

        double plainTop = layout.cardText(plain).lines().get(0).baseline();
        double linkTop = layout.cardText(withLink).lines().get(0).baseline();
        assertTrue(linkTop > plainTop, "o texto começa abaixo do marcador");
    }

    @Test
    void imageCaptionStartsBelowTheImage() {
        Card photo = Card.createImage("foto.png", 100, 100, 160, 120);
        photo.setText("Legenda");
        fit(photo);

        CardTextLayout.Block block = layout.cardText(photo);

        assertEquals(CardTextFit.CAPTION_FONT_SIZE, block.fontSize());
        CardTextLayout.Line line = block.lines().get(0);
        assertEquals(1 + 120 + 6 + 14, line.baseline(), 1e-9, "borda + imagem + espaço + linha de base");
        assertEquals(80, line.x(), 1e-9);
    }

    @Test
    void emptyCardHasNoTextBecauseThePromptIsNotExported() {
        Card card = Card.create(CardShape.RECTANGLE, 100, 60);
        assertTrue(layout.cardText(card).isEmpty());
    }

    @Test
    void frameTitleIsOneBoldLineInTheTitleBand() {
        Card frame = Card.create(CardShape.FRAME, 200, 150);
        frame.setWidth(300);
        frame.setHeight(200);
        frame.setText("Plano\n de ação");

        CardTextLayout.Block block = layout.cardText(frame);

        assertTrue(block.bold());
        assertEquals(CardTextFit.FRAME_TITLE_FONT_SIZE, block.fontSize());
        assertEquals(CardTextLayout.Anchor.START, block.anchor());
        assertEquals(1, block.lines().size());
        CardTextLayout.Line line = block.lines().get(0);
        assertEquals("Plano de ação", line.text());
        assertEquals(2 + 16, line.x(), 1e-9, "borda de 1,5 arredondada para 2 + recuo");
        assertEquals(2 + Math.round((34 - 15 * 1.25) / 2) + 15, line.baseline(), 1e-9, "centralizado na faixa do título");
    }

    @Test
    void longFrameTitleEndsWithDotsAndBlankTitleIsNotExported() {
        Card frame = Card.create(CardShape.FRAME, 200, 150);
        frame.setWidth(120);
        frame.setHeight(200);
        frame.setText("Título bem comprido da seção");
        assertEquals("Título...", layout.cardText(frame).lines().get(0).text());

        frame.setText("   ");
        assertTrue(layout.cardText(frame).isEmpty(), "o \"Seção\" esmaecido é só da tela");
    }

    @Test
    void missingImageNoticeIsCenteredInTheImageArea() {
        Card photo = Card.createImage("sumiu.png", 100, 100, 160, 120);

        CardTextLayout.Block block = layout.missingImageText(photo);

        assertEquals(CardTextLayout.MISSING_IMAGE_FONT_SIZE, block.fontSize());
        CardTextLayout.Line line = block.lines().get(0);
        assertEquals(CardTextLayout.MISSING_IMAGE_TEXT, line.text());
        assertEquals(80, line.x(), 1e-9);
        assertEquals(1 + Math.round(6 + (118 - 12 - 15) / 2.0) + 12, line.baseline(), 1e-9);
    }

    // ------------------------------------------------------------ texto da seta

    @Test
    void connectionLabelBoxIsTheTextPlusPaddingInTheMiddleOfTheArrow() {
        Board board = new Board("b");
        Card a = new Card(0, 0);
        Card b = new Card(400, 0);
        board.addCard(a);
        board.addCard(b);
        Connection connection = board.connect(a, b).orElseThrow();
        assertNull(layout.connectionLabel(connection, false), "sem texto, sem caixa");

        connection.setLabel("Causa");
        CardTextLayout.LabelBox box = layout.connectionLabel(connection, false);

        assertEquals(5 * 6 + 10, box.width(), 1e-9);
        assertEquals(15 + 2, box.height(), 1e-9);
        double middleX = (200 + 400) / 2.0;
        assertEquals(middleX - box.width() / 2, box.x(), 1e-9);
        assertEquals(60 - box.height() / 2, box.y(), 1e-9);
        CardTextLayout.Line line = box.text().lines().get(0);
        assertEquals("Causa", line.text());
        assertEquals(middleX, line.x(), 1e-9);
        assertEquals(box.y() + 1 + 12, line.baseline(), 1e-9);
    }

    @Test
    void longConnectionLabelIsCutWithDotsAtTheMaximumWidth() {
        Board board = new Board("b");
        Card a = new Card(0, 0);
        Card b = new Card(900, 0);
        board.addCard(a);
        board.addCard(b);
        Connection connection = board.connect(a, b).orElseThrow();
        connection.setLabel("x".repeat(Connection.MAX_LABEL_LENGTH));

        CardTextLayout.LabelBox box = layout.connectionLabel(connection, false);

        assertEquals(ConnectionView.LABEL_MAX_WIDTH, box.width(), 1e-9);
        String shown = box.text().lines().get(0).text();
        assertTrue(shown.endsWith(CardTextLayout.ELLIPSIS), shown);
        assertTrue(metrics.width(shown, 12, false) <= box.width() - 10);
    }
}
