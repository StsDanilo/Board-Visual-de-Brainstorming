package com.danilo.boardvisual.export;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.view.CardGeometry;
import com.danilo.boardvisual.view.CardTextFit;
import com.danilo.boardvisual.view.CardTextLayout;
import com.danilo.boardvisual.view.FixedTextMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O SVG gerado a partir do modelo, lido de volta como XML: estrutura, camadas,
 * formas, setas, textos, links, imagens embutidas, escapes, números e gravação.
 * Sem JavaFX: o texto é medido com {@link FixedTextMetrics}.
 */
class SvgExporterTest {

    private static final String SVG = SvgExporter.SVG_NS;
    private static final String XLINK = SvgExporter.XLINK_NS;
    private static final String EVIL_TEXT = "<script>alert(\"x\")</script> & 'aspas' \u0001fim\uD800 😀";

    private final FixedTextMetrics metrics = new FixedTextMetrics();
    private final Map<String, byte[]> files = new HashMap<>();
    private final byte[] png = png(40, 30);

    /** Os cards do board completo, para os testes acharem cada um. */
    private Card rect;
    private Card ellipse;
    private Card diamond;
    private Card frame;
    private Card photo;
    private Card missing;
    private Card panel;
    private Card nested;
    private Card boardLink;
    private Card brokenLink;
    private Card evil;
    private Card longText;
    private final Map<ConnectionStyle, Connection> arrows = new HashMap<>();

    private SvgExporter exporter() {
        return new SvgExporter(metrics, files::get, id -> !id.equals("nao-existe"));
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0x00C000);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Como o CardTextFitController faz na tela: o card cresce até o texto caber. */
    private void fit(Card card) {
        CardTextFit.Fit fit = new CardTextFit(metrics).fit(card);
        card.setWidth(fit.width());
        card.setHeight(fit.height());
    }

    /** Um board com todos os tipos de elemento que existem hoje. */
    private Board fullBoard() {
        files.put("verde.png", png);
        Board board = new Board("Projeto");
        frame = Card.create(CardShape.FRAME, 300, 250);
        frame.setWidth(600);
        frame.setHeight(400);
        frame.setText("Seção A");
        frame.setColor("#CFE2FF");
        frame.setLink("https://exemplo.com/secao");
        rect = new Card(100, 100);
        rect.setText("Ideia principal");
        rect.setColor("#FFF1A8");
        rect.setLink("exemplo.com/a?b=1&c=2");
        ellipse = Card.create(CardShape.ELLIPSE, 500, 150);
        ellipse.setText("Elipse");
        diamond = Card.create(CardShape.DIAMOND, 800, 150);
        diamond.setText("Losango");
        photo = Card.createImage("verde.png", 150, 600, 160, 120);
        photo.setText("Legenda");
        missing = Card.createImage("sumiu.png", 400, 600, 160, 120);
        panel = new Card(600, 500);
        panel.setText("Painel");
        panel.setPanelMode(PanelMode.TEXT);
        nested = new Card(900, 500);
        nested.setText("Board filho");
        nested.setChildBoard(new Board("Filho"));
        boardLink = new Card(1200, 100);
        boardLink.setText("Atalho");
        boardLink.setBoardLink(nested.getChildBoard().getId());
        brokenLink = new Card(1200, 400);
        brokenLink.setText("Atalho quebrado");
        brokenLink.setBoardLink("nao-existe");
        evil = new Card(1200, 700);
        evil.setText(EVIL_TEXT);
        longText = new Card(1500, 100);
        longText.setText("uma ideia comprida ".repeat(20).trim());
        for (Card card : List.of(frame, rect, ellipse, diamond, photo, missing, panel, nested, boardLink, brokenLink,
                evil, longText)) {
            board.addCard(card);
            fit(card);
        }
        arrows.put(ConnectionStyle.ARROW, connect(board, rect, ellipse, ConnectionStyle.ARROW, "Causa"));
        arrows.put(ConnectionStyle.DASHED, connect(board, ellipse, diamond, ConnectionStyle.DASHED, "Talvez"));
        arrows.put(ConnectionStyle.DOUBLE, connect(board, panel, nested, ConnectionStyle.DOUBLE, "Troca"));
        arrows.put(ConnectionStyle.LINE, connect(board, photo, missing, ConnectionStyle.LINE, "Liga"));
        connect(board, ellipse, rect, ConnectionStyle.ARROW, "Volta"); // a contrária de rect → ellipse
        return board;
    }

    private static Connection connect(Board board, Card a, Card b, ConnectionStyle style, String label) {
        Connection connection = board.connect(a, b, style).orElseThrow();
        connection.setLabel(label);
        return connection;
    }

    private String svg(Board board, PngExporter.Background background) throws IOException {
        StringWriter out = new StringWriter();
        exporter().write(board, "Projeto › Teste", background, out);
        return out.toString();
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element byId(Document document, String id) {
        NodeList all = document.getElementsByTagNameNS(SVG, "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Element> descendants(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList list = parent.getElementsByTagNameNS(SVG, localName);
        for (int i = 0; i < list.getLength(); i++) {
            result.add((Element) list.item(i));
        }
        return result;
    }

    /** Texto de cada {@code <text>} dentro de {@code parent}: as linhas (tspans) juntas por "|". */
    private static List<String> texts(Element parent) {
        List<String> result = new ArrayList<>();
        for (Element text : descendants(parent, "text")) {
            List<String> lines = new ArrayList<>();
            for (Element span : descendants(text, "tspan")) {
                lines.add(span.getTextContent());
            }
            result.add(String.join("|", lines));
        }
        return result;
    }

    /** O grupo de um card no SVG (cards na ordem do board, sem as seções). */
    private Element cardGroup(Document document, Board board, Card card) {
        int index = 0;
        for (Card c : board.getCards()) {
            if (c.isFrame()) {
                continue;
            }
            index++;
            if (c == card) {
                return byId(document, "card-" + index);
            }
        }
        throw new AssertionError("card fora do board");
    }

    // ------------------------------------------------------------ documento

    @Test
    void documentIsTheContentAreaOfThePngAndPdfWithAMatchingViewBox() throws Exception {
        Board board = fullBoard();
        Box bounds = PdfExporter.contentBounds(board);

        Element root = parse(svg(board, PngExporter.Background.WHITE)).getDocumentElement();

        assertEquals("svg", root.getLocalName());
        assertEquals(SVG, root.getNamespaceURI());
        assertEquals(SvgExporter.num(bounds.width()), root.getAttribute("width"));
        assertEquals(SvgExporter.num(bounds.height()), root.getAttribute("height"));
        assertEquals("0 0 " + SvgExporter.num(bounds.width()) + " " + SvgExporter.num(bounds.height()),
                root.getAttribute("viewBox"));
        assertEquals("1.1", root.getAttribute("version"));
        assertEquals("Projeto › Teste", children(root).get(0).getTextContent(), "título do documento");
    }

    @Test
    void layersFollowTheScreenOrder() throws Exception {
        Element root = parse(svg(fullBoard(), PngExporter.Background.WHITE)).getDocumentElement();

        List<String> order = new ArrayList<>();
        for (Element child : children(root)) {
            order.add(child.getLocalName() + (child.hasAttribute("id") ? "#" + child.getAttribute("id") : ""));
        }
        assertEquals(List.of("title", "desc", "defs", "rect#fundo", "g#secoes", "g#setas", "g#textos-das-setas",
                "g#cards"), order, "seções atrás das setas, textos das setas acima das linhas, cards por cima");
    }

    @Test
    void cardsAreDrawnInTheSavedOrderAtTheirPositions() throws Exception {
        Board board = fullBoard();
        Box bounds = PdfExporter.contentBounds(board);
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        assertEquals(1, children(byId(document, "secoes")).size(), "uma seção");
        List<Element> cards = children(byId(document, "cards"));
        List<Card> expected = board.getCards().stream().filter(c -> !c.isFrame()).toList();
        assertEquals(expected.size(), cards.size());
        for (int i = 0; i < expected.size(); i++) {
            Card card = expected.get(i);
            assertEquals("card-" + (i + 1), cards.get(i).getAttribute("id"));
            assertEquals("translate(" + SvgExporter.num(card.getX() - bounds.x()) + " "
                    + SvgExporter.num(card.getY() - bounds.y()) + ")", cards.get(i).getAttribute("transform"));
        }
    }

    @Test
    void whiteBackgroundIsARectAndTransparentHasNone() throws Exception {
        Board board = fullBoard();
        Element white = byId(parse(svg(board, PngExporter.Background.WHITE)), "fundo");
        assertNotNull(white);
        assertEquals("#ffffff", white.getAttribute("fill"));
        assertNull(byId(parse(svg(board, PngExporter.Background.TRANSPARENT)), "fundo"));
    }

    // ------------------------------------------------------------ formas

    @Test
    void eachShapeIsDrawnWithItsColorBorderAndShadow() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element rectShape = descendants(cardGroup(document, board, rect), "rect").get(0);
        assertEquals("#fff1a8", rectShape.getAttribute("fill"));
        assertEquals(SvgExporter.BORDER_COLOR, rectShape.getAttribute("stroke"));
        assertEquals("7.5", rectShape.getAttribute("rx"), "cantos de 8 menos meia borda");
        assertEquals("url(#" + SvgExporter.SHADOW_ID + ")",
                ((Element) rectShape.getParentNode()).getAttribute("filter"));

        Element ellipsePath = descendants(cardGroup(document, board, ellipse), "path").get(0);
        assertEquals(SvgExporter.scalePath(CardGeometry.ELLIPSE_PATH, 0.5, 0.5, ellipse.getWidth() - 1,
                ellipse.getHeight() - 1), ellipsePath.getAttribute("d"), "o -fx-shape do app.css, escalado");
        Element diamondPath = descendants(cardGroup(document, board, diamond), "path").get(0);
        assertEquals(SvgExporter.scalePath(CardGeometry.DIAMOND_PATH, 0.5, 0.5, diamond.getWidth() - 1,
                diamond.getHeight() - 1), diamondPath.getAttribute("d"));
    }

    @Test
    void frameIsATranslucentAreaWithADarkerBorderAndABoldTitle() throws Exception {
        Document document = parse(svg(fullBoard(), PngExporter.Background.WHITE));
        Element group = byId(document, "secao-1");

        List<Element> rects = descendants(group, "rect");
        assertEquals("#cfe2ff", rects.get(0).getAttribute("fill"));
        assertEquals("0.45", rects.get(0).getAttribute("fill-opacity"));
        assertEquals("none", rects.get(1).getAttribute("fill"));
        assertEquals("1.5", rects.get(1).getAttribute("stroke-width"));
        assertEquals(SvgExporter.derive(SvgExporter.paint("#CFE2FF"), -0.30).hex(), rects.get(1).getAttribute("stroke"));
        assertFalse(descendants(group, "g").stream().anyMatch(g -> g.hasAttribute("filter")), "seção sem sombra");
        Element title = descendants(group, "text").get(0);
        assertEquals("bold", title.getAttribute("font-weight"));
        assertEquals("Seção A", title.getTextContent());
    }

    @Test
    void nestedBoardCardHasTheStackSheetAndTheEnterButton() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element group = cardGroup(document, board, nested);
        List<Element> rects = descendants(group, "rect");
        assertEquals(SvgExporter.BORDER_STRONG_COLOR, rects.get(0).getAttribute("fill"), "folha de trás");
        assertEquals("6", rects.get(0).getAttribute("x"));
        assertEquals(1, descendants(group, "circle").size(), "botão de entrar no board");
        assertEquals(1, descendants(cardGroup(document, board, panel), "circle").size(), "botão do painel");
        assertEquals(0, descendants(cardGroup(document, board, ellipse), "circle").size());
    }

    // ------------------------------------------------------------ setas

    @Test
    void arrowsComeInTheFourStyles() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        for (ConnectionStyle style : ConnectionStyle.values()) {
            int index = board.getConnections().indexOf(arrows.get(style)) + 1;
            Element group = byId(document, "seta-" + index);
            Element line = descendants(group, "line").get(0);
            assertEquals(SvgExporter.CONNECTION_COLOR, line.getAttribute("stroke"));
            assertEquals("2", line.getAttribute("stroke-width"));
            assertEquals(style.isDashed() ? "8 6" : "", line.getAttribute("stroke-dasharray"), style.name());
            int heads = (style.hasStartArrow() ? 1 : 0) + (style.hasEndArrow() ? 1 : 0);
            assertEquals(heads, descendants(group, "polygon").size(), style.name());
        }
    }

    @Test
    void arrowTextsAreAboveAllLinesWithAWhiteBox() throws Exception {
        Board board = fullBoard();
        Element labels = byId(parse(svg(board, PngExporter.Background.TRANSPARENT)), "textos-das-setas");

        assertEquals(List.of("Causa", "Talvez", "Troca", "Liga", "Volta"), texts(labels));
        for (Element box : descendants(labels, "rect")) {
            assertEquals(SvgExporter.LABEL_BACKGROUND, box.getAttribute("fill"), "fundo branco mesmo sem fundo");
        }
    }

    // ------------------------------------------------------------ texto

    @Test
    void cardTextKeepsTheScreenLinesFontAndPosition() throws Exception {
        Board board = fullBoard();
        Box bounds = PdfExporter.contentBounds(board);
        Document document = parse(svg(board, PngExporter.Background.WHITE));
        CardTextLayout.Block expected = new CardTextLayout(metrics).cardText(longText);

        Element text = descendants(cardGroup(document, board, longText), "text").get(0);
        List<Element> spans = descendants(text, "tspan");
        assertEquals(expected.lines().size(), spans.size());
        assertTrue(spans.size() > 3, "texto longo em várias linhas");
        assertEquals(SvgExporter.num(expected.fontSize()), text.getAttribute("font-size"));
        assertEquals("middle", text.getAttribute("text-anchor"));
        assertEquals(SvgExporter.FONT_FAMILY, text.getAttribute("font-family"));
        assertTrue(text.getAttribute("font-family").endsWith("sans-serif"));
        assertEquals("font-kerning:none", text.getAttribute("style"), "sem kerning, como o JavaFX");
        for (int i = 0; i < spans.size(); i++) {
            CardTextLayout.Line line = expected.lines().get(i);
            assertEquals(line.text(), spans.get(i).getTextContent());
            assertEquals(SvgExporter.num(line.x()), spans.get(i).getAttribute("x"), "coordenadas do card");
            assertEquals(SvgExporter.num(line.baseline()), spans.get(i).getAttribute("y"));
        }
        assertNotNull(bounds);
    }

    @Test
    void textFromTheUserNeverBecomesMarkup() throws Exception {
        Board board = fullBoard();
        String svg = svg(board, PngExporter.Background.WHITE);
        Document document = parse(svg); // XML válido apesar de < > & " ', controle e surrogate solto

        assertEquals(0, document.getElementsByTagNameNS("*", "script").getLength());
        assertFalse(svg.contains("<script"));
        // As linhas podem quebrar em qualquer ponto: compara sem os espaços.
        String shown = texts(cardGroup(document, board, evil)).get(0).replace("|", "").replaceAll("\\s", "");
        assertEquals("<script>alert(\"x\")</script>&'aspas'fim😀", shown,
                "o texto aparece como texto, sem o controle e o surrogate solto");
    }

    @Test
    void numbersUseADotAndNoExponentEvenInBrazilianPortuguese() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"));
            Board board = fullBoard();
            board.getCards().get(1).setX(0.1 + 0.2); // 0.30000000000000004
            board.getCards().get(2).setX(1e-9);
            String svg = svg(board, PngExporter.Background.WHITE);
            Matcher numbers = Pattern.compile(
                    " (?:x|y|x1|y1|x2|y2|width|height|rx|ry|cx|cy|r|font-size|stroke-width|opacity)=\"([^\"]*)\"")
                    .matcher(svg);
            int count = 0;
            while (numbers.find()) {
                count++;
                assertTrue(numbers.group(1).matches("-?\\d+(\\.\\d{1,3})?|\\d+%|-\\d+%"), numbers.group());
            }
            assertTrue(count > 50);
            String withoutImages = svg.replaceAll("data:[^\"]*", ""); // o base64 tem "4e5" por acaso
            assertFalse(withoutImages.matches("(?s).*\\d[eE][-+]?\\d.*"), "sem notação científica");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void numberFormatting() {
        assertEquals("0", SvgExporter.num(1e-7));
        assertEquals("0", SvgExporter.num(-0.0004));
        assertEquals("12.5", SvgExporter.num(12.5));
        assertEquals("0.3", SvgExporter.num(0.1 + 0.2));
        assertEquals("1234567.891", SvgExporter.num(1234567.8914));
        assertEquals("100000000000000000000", SvgExporter.num(1e20));
        assertEquals("0", SvgExporter.num(Double.NaN));
        assertEquals("0", SvgExporter.num(Double.POSITIVE_INFINITY));
    }

    @Test
    void escapeRemovesWhatXmlDoesNotAccept() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f", SvgExporter.escape("a&b<c>d\"e'f"));
        assertEquals("okfim", SvgExporter.escape("ok\u0000\u0008\u001F￾fim"));
        assertEquals("x", SvgExporter.escape("x\uDC00"), "surrogate solto");
        assertEquals("😀", SvgExporter.escape("😀"), "par de surrogates inteiro fica");
        assertEquals("a&#9;b&#10;c&#13;", SvgExporter.escape("a\tb\nc\r"));
    }

    // ------------------------------------------------------------ links

    @Test
    void externalLinkMakesTheWholeCardClickable() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element group = cardGroup(document, board, rect);
        Element link = descendants(group, "a").get(0);
        assertEquals("https://exemplo.com/a?b=1&c=2", link.getAttributeNS(XLINK, "href"), "o link normalizado");
        assertEquals("_blank", link.getAttribute("target"));
        assertEquals(children(group).get(0), link, "o card todo fica dentro do link");
        assertFalse(descendants(link, "rect").isEmpty());
    }

    @Test
    void frameLinkIsOnlyOnItsMarkerAndBoardLinksAreNotLinks() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        List<Element> links = descendants(document.getDocumentElement(), "a");
        assertEquals(2, links.size(), "o card com link e o marcador da seção; nada para os boards");
        Element frameLink = descendants(byId(document, "secao-1"), "a").get(0);
        assertEquals("https://exemplo.com/secao", frameLink.getAttributeNS(XLINK, "href"));
        assertEquals(0, descendants(frameLink, "rect").size(), "só o marcador, não a área da seção");
        assertEquals(1, descendants(frameLink, "circle").size());
    }

    @Test
    void boardLinkMarkerIsDrawnAndTheBrokenOneIsFadedAndRed() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element ok = descendants(cardGroup(document, board, boardLink), "circle").get(0);
        assertEquals(SvgExporter.BORDER_STRONG_COLOR, ok.getAttribute("stroke"));
        Element broken = descendants(cardGroup(document, board, brokenLink), "circle").get(0);
        assertEquals(SvgExporter.DANGER_COLOR, broken.getAttribute("stroke"));
        assertEquals("0.7", ((Element) broken.getParentNode()).getAttribute("opacity"));
    }

    @Test
    void dangerousLinkNeverReachesTheFile() throws Exception {
        Board board = fullBoard();
        rect.setLink("javascript:alert(1)"); // o modelo recusa: fica sem link
        String svg = svg(board, PngExporter.Background.WHITE);
        assertFalse(svg.contains("javascript"));
        assertEquals(1, descendants(parse(svg).getDocumentElement(), "a").size(), "só o da seção");
    }

    // ------------------------------------------------------------ imagens

    @Test
    void imageIsEmbeddedAsBase64AndClippedWithTheCardCorners() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element group = cardGroup(document, board, photo);
        Element image = descendants(group, "image").get(0);
        String href = image.getAttributeNS(XLINK, "href");
        assertTrue(href.startsWith("data:image/png;base64,"));
        assertArrayEquals(png, Base64.getDecoder().decode(href.substring(href.indexOf(',') + 1)));
        assertEquals("xMidYMid meet", image.getAttribute("preserveAspectRatio"), "proporção preservada");
        assertEquals("1", image.getAttribute("x"));
        assertEquals(SvgExporter.num(photo.getWidth() - 2), image.getAttribute("width"));
        String clip = ((Element) image.getParentNode()).getAttribute("clip-path");
        Element clipPath = byId(document, clip.substring(5, clip.length() - 1));
        assertNotNull(clipPath, "o recorte existe nos defs");
        assertEquals("clipPath", clipPath.getLocalName());
        assertEquals(List.of("Legenda"), texts(group), "legenda abaixo da imagem");
    }

    @Test
    void missingImageShowsTheSameNoticeAsTheScreen() throws Exception {
        Board board = fullBoard();
        Document document = parse(svg(board, PngExporter.Background.WHITE));

        Element group = cardGroup(document, board, missing);
        assertTrue(descendants(group, "image").isEmpty());
        assertTrue(descendants(group, "rect").stream()
                .anyMatch(r -> r.getAttribute("fill").equals(SvgExporter.MISSING_IMAGE_COLOR)));
        assertEquals(List.of(CardTextLayout.MISSING_IMAGE_TEXT), texts(group));
    }

    @Test
    void imagesPastTheSizeLimitAreLeftOutWithANotice() throws Exception {
        Board board = fullBoard();
        Card second = Card.createImage("verde.png", 150, 900, 160, 120);
        board.addCard(second);
        SvgExporter exporter = exporter();
        exporter.maxEmbeddedImageBytes = png.length; // cabe uma imagem só
        StringWriter out = new StringWriter();
        exporter.write(board, "t", PngExporter.Background.WHITE, out);
        Document document = parse(out.toString());

        assertEquals(1, document.getElementsByTagNameNS(SVG, "image").getLength());
        assertTrue(texts(cardGroup(document, board, second)).get(0).replace("|", " ")
                .startsWith("Imagem não incluída"));
    }

    @Test
    void imageTypeComesFromTheContentAndUnknownFilesAreMissing() throws IOException {
        assertEquals("image/png", SvgExporter.imageType(png));
        assertEquals("image/jpeg", SvgExporter.imageType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertNull(SvgExporter.imageType("<svg onload=x>".getBytes(StandardCharsets.UTF_8)));

        ByteArrayOutputStream bmp = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "bmp", bmp);
        SvgExporter.EmbeddedImage converted = SvgExporter.readImage(name -> bmp.toByteArray(), "a.bmp");
        assertTrue(converted.dataUri().startsWith("data:image/png;base64,"), "BMP vira PNG");

        assertEquals(SvgExporter.EmbeddedImage.MISSING, SvgExporter.readImage(name -> null, "x.png"));
        assertTrue(SvgExporter.EmbeddedImage.MISSING == SvgExporter.readImage(
                name -> new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3}, "corrompida.png"),
                "PNG que não abre: aviso, como na tela");
        assertTrue(SvgExporter.EmbeddedImage.MISSING == SvgExporter.readImage(name -> {
            throw new IOException("sem permissão");
        }, "x.png"));
    }

    // ------------------------------------------------------------ cores e formas

    @Test
    void colorsAreOnlyHexAnythingElseBecomesTheDefault() {
        assertEquals(new SvgExporter.Paint("#fff1a8", 1), SvgExporter.paint("#FFF1A8"));
        assertEquals(new SvgExporter.Paint("#aabbcc", 1), SvgExporter.paint("#abc"));
        assertEquals("#112233", SvgExporter.paint("#11223380").hex());
        assertEquals(128 / 255.0, SvgExporter.paint("#11223380").opacity(), 1e-9);
        assertEquals("#ffffff", SvgExporter.paint("red").hex());
        assertEquals("#ffffff", SvgExporter.paint("#fff\" onload=\"x").hex());
        assertEquals("#ffffff", SvgExporter.paint(null).hex());
    }

    @Test
    void frameBorderIsDarkenedLikeTheCssDerive() {
        assertEquals("#b3b3b3", SvgExporter.derive(SvgExporter.paint("#FFFFFF"), -0.30).hex());
        assertEquals("#000000", SvgExporter.derive(SvgExporter.paint("#000000"), -0.30).hex());
        // Amarelo da paleta: mesmo matiz, 70% do brilho.
        assertEquals("#b3a876", SvgExporter.derive(SvgExporter.paint("#FFF1A8"), -0.30).hex());
    }

    @Test
    void shapePathsAreScaledFromTheHundredSquare() {
        assertEquals("M 0 50 A 100 50 0 1 1 200 50 A 100 50 0 1 1 0 50 Z",
                SvgExporter.scalePath(CardGeometry.ELLIPSE_PATH, 0, 0, 200, 100));
        assertEquals("M 15 10 L 25 15 L 15 20 L 5 15 Z",
                SvgExporter.scalePath(CardGeometry.DIAMOND_PATH, 5, 10, 20, 10));
    }

    // ------------------------------------------------------------ gravação

    @Test
    void exportWritesAValidFileAndLeavesNoTemporaryFile(@TempDir Path folder) throws Exception {
        Board board = fullBoard();
        Path file = folder.resolve("board.svg");
        Files.writeString(file, "antigo");

        exporter().export(board, "Projeto", file, PngExporter.Background.WHITE);

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        parse(written);
        try (Stream<Path> listing = Files.list(folder)) {
            assertEquals(List.of(file), listing.toList(), "substituiu o arquivo e não sobrou .tmp");
        }
    }

    @Test
    void emptyBoardIsRefusedWithoutCreatingTheFile(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("vazio.svg");
        assertThrows(IllegalArgumentException.class,
                () -> exporter().export(new Board("vazio"), "vazio", file, PngExporter.Background.WHITE));
        try (Stream<Path> listing = Files.list(folder)) {
            assertEquals(0, listing.count());
        }
    }

    @Test
    void exportingDoesNotChangeTheBoard() throws IOException {
        Board board = fullBoard();
        BoardSnapshot before = board.snapshot();
        svg(board, PngExporter.Background.WHITE);
        svg(board, PngExporter.Background.TRANSPARENT);
        assertEquals(before, board.snapshot());
    }

    @Test
    void suggestedNameIsThePngNameWithSvgExtension() {
        assertEquals("Projeto.svg", SvgExporter.suggestedFileName(List.of("Projeto")));
        assertEquals("Projeto - Etapa_ 1.svg", SvgExporter.suggestedFileName(List.of("Projeto", "Etapa: 1")));
        assertEquals(PngExporter.suggestedFileName(List.of("A", "B")).replace(".png", ".svg"),
                SvgExporter.suggestedFileName(List.of("A", "B")));
        assertEquals(Path.of("x", "board.svg"), SvgExporter.withSvgExtension(Path.of("x", "board")));
        assertEquals(Path.of("x", "board.SVG"), SvgExporter.withSvgExtension(Path.of("x", "board.SVG")));
    }
}
