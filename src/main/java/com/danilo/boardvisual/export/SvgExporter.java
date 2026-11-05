package com.danilo.boardvisual.export;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionGeometry;
import com.danilo.boardvisual.model.ConnectionGeometry.Point;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.model.WebLink;
import com.danilo.boardvisual.view.CardGeometry;
import com.danilo.boardvisual.view.CardTextLayout;
import com.danilo.boardvisual.view.Icons;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exporta um único board (o que está na tela) para SVG: um desenho vetorial,
 * nítido em qualquer tamanho e editável (Inkscape, Figma, navegador).
 *
 * O JavaFX não sabe gerar SVG, então o arquivo é escrito à mão a partir do
 * modelo, card por card e seta por seta, com as mesmas contas da tela:
 * formatos ({@link CardGeometry}, os mesmos paths do app.css), setas
 * ({@link ConnectionGeometry}), marcadores ({@link CardMarkers}) e o texto
 * quebrado em linhas como na tela ({@link CardTextLayout}, com o medidor de
 * texto recebido de fora). As imagens vão embutidas no arquivo (base64).
 * Esta classe não usa a cena nem o toolkit do JavaFX: o medidor de texto e as
 * imagens chegam por interface, como o desenho do PDF e do PNG.
 *
 * <pre>
 * &lt;svg&gt;   área = a mesma do PNG e do PDF (os cards e uma margem), 1 unidade do board = 1 px
 *   defs              sombra dos cards, recortes das imagens
 *   fundo             retângulo branco (sem ele: fundo transparente)
 *   secoes            seções (frames), atrás de tudo
 *   setas             linhas e pontas
 *   textos-das-setas  acima de todas as linhas (como na tela)
 *   cards             na ordem do board (a mesma da tela e do PNG)
 * </pre>
 *
 * Decisões (DOCUMENTACAO.md, 3.28): a fonte é a lista do app.css terminando na
 * genérica {@code sans-serif}, e as quebras de linha são as da tela (fixas no
 * arquivo); a sombra é aproximada; o link externo vira {@code <a>} clicável
 * (só http/https já conferido pelo {@link WebLink}); o link para outro board só
 * aparece como marcador (não há destino no SVG); painéis flutuantes, cadeado e
 * elementos de interface (seleção, alças, barras) ficam de fora.
 */
public final class SvgExporter {

    /** De onde vêm os arquivos das imagens dos cards (a pasta do save ou a temporária). */
    @FunctionalInterface
    public interface ImageSource {
        /** O conteúdo do arquivo {@code fileName} (o nome gravado no card), ou {@code null} se ele não existe. */
        byte[] read(String fileName) throws IOException;
    }

    // ------------------------------------------------- aparência (do app.css)

    /**
     * Fonte: a mesma lista do {@code .root} no app.css, terminando na genérica
     * {@code sans-serif}. Quem abre o arquivo num computador sem essas fontes vê
     * outra (com métrica um pouco diferente); as quebras de linha continuam as
     * da tela, porque cada linha é gravada separada.
     */
    static final String FONT_FAMILY = "'Segoe UI', 'Helvetica Neue', sans-serif";
    /** O JavaFX não aplica kerning: o SVG também não (ver {@code writeText}). */
    static final String TEXT_STYLE = "font-kerning:none";
    /** {@code -bv-text}, {@code -bv-text-muted}, {@code -bv-border}, {@code -bv-border-strong}... */
    static final String TEXT_COLOR = "#2a2a28";
    static final String MUTED_TEXT_COLOR = "#7c7c75";
    static final String BORDER_COLOR = "#d8d8d2";
    static final String BORDER_STRONG_COLOR = "#b9b9b1";
    static final String SURFACE_COLOR = "#ffffff";
    static final String ACCENT_COLOR = "#4f7cff";
    static final String DANGER_COLOR = "#d64545";
    static final String CONNECTION_COLOR = "#8c8c85";
    /** Fundo do aviso de imagem não encontrada ({@code .card-image-missing}). */
    static final String MISSING_IMAGE_COLOR = "#ececea";
    /** Fundo do texto da seta na exportação ({@code .board-export .connection-label}). */
    static final String LABEL_BACKGROUND = "#ffffff";
    /** Fundo branco do documento (o padrão; o outro é transparente). */
    static final String PAGE_COLOR = "#ffffff";

    /** {@code .card}: cantos e borda. */
    static final double CARD_RADIUS = 8;
    static final double CARD_BORDER_WIDTH = 1;
    /** {@code .card.shape-frame} e {@code .frame-fill}. */
    static final double FRAME_BORDER_WIDTH = 1.5;
    static final double FRAME_FILL_RADIUS = 7;
    static final double FRAME_FILL_OPACITY = 0.45;
    /** Borda da seção: {@code derive(-card-color, -30%)}. */
    static final double FRAME_BORDER_DERIVE = -0.30;
    /** {@code .card-view:has-board .card}: a "folha" atrás do card de board aninhado. */
    static final double STACK_OFFSET = 6;
    /** {@code .connection-line}. */
    static final double CONNECTION_WIDTH = 2;
    static final String CONNECTION_DASHES = "8 6";
    /** {@code .connection-label}: cantos. */
    static final double LABEL_RADIUS = 4;
    /** {@code .panel-button}: diâmetro. */
    static final double BUTTON_SIZE = 26;
    /** {@code .tool-icon} e {@code .card-marker .tool-icon}: espessura dos traços. */
    static final double ICON_STROKE_WIDTH = 1.7;
    static final double MARKER_ICON_STROKE_WIDTH = 2.4;
    /** O ícone de 24 px encolhe para caber no marcador de 18 px ({@code Icons.marker}). */
    static final double MARKER_ICON_SCALE = 0.62;
    /** O clipe do link aparece inclinado ({@code Icons.link}). */
    static final double LINK_ICON_ROTATION = 45;
    /** {@code .card-marker:broken}. */
    static final double BROKEN_MARKER_OPACITY = 0.7;
    /** Centro da área 24×24 dos ícones. */
    static final double ICON_CENTER = 12;
    /**
     * Sombra aproximada de {@code dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2)}:
     * o raio 10 da gaussiana do JavaFX equivale a um desvio de ~3,3.
     */
    static final double SHADOW_BLUR = 10 / 3.0;
    static final double SHADOW_OFFSET_Y = 2;
    static final double SHADOW_OPACITY = 0.08;

    // ------------------------------------------------- limites

    /**
     * Máximo de imagem embutida num arquivo (bytes dos arquivos, somando cada
     * vez que uma imagem aparece). É vetorial, então não há limite de pixels;
     * mas um board com muitas fotos geraria um SVG enorme, que os editores
     * mal abrem. Passou do limite, as imagens seguintes saem com o aviso
     * {@link #IMAGE_SKIPPED_TEXT}.
     */
    static final long MAX_EMBEDDED_IMAGE_BYTES = 100L * 1024 * 1024;
    static final String IMAGE_SKIPPED_TEXT = "Imagem não incluída (SVG grande demais)";

    static final String SVG_NS = "http://www.w3.org/2000/svg";
    static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    static final String SHADOW_ID = "bv-sombra";
    static final String CLIP_ID_PREFIX = "bv-recorte-";

    private final CardTextLayout layout;
    private final ImageSource images;
    private final Predicate<String> boardExists;
    /** Limite de imagem embutida (um campo, para os testes usarem um menor). */
    long maxEmbeddedImageBytes = MAX_EMBEDDED_IMAGE_BYTES;

    /**
     * @param metrics     mede o texto na fonte da tela (na aplicação, {@code ScreenTextMetrics})
     * @param images      lê os arquivos das imagens dos cards
     * @param boardExists diz se um board (pelo id) ainda está na árvore: o link para
     *                    um board que não existe mais sai com o marcador "quebrado", como na tela
     */
    public SvgExporter(CardTextLayout.Metrics metrics, ImageSource images, Predicate<String> boardExists) {
        this.layout = new CardTextLayout(metrics);
        this.images = images == null ? name -> null : images;
        this.boardExists = boardExists == null ? id -> true : boardExists;
    }

    /**
     * Grava o SVG do board (num arquivo temporário e depois no lugar, como o
     * salvamento, o PDF e o PNG: nunca fica um arquivo pela metade).
     *
     * @param title título do documento (o caminho até o board)
     * @throws IllegalArgumentException se o board não tem cards (não há o que mostrar)
     */
    public void export(Board board, String title, Path file, PngExporter.Background background) throws IOException {
        if (PngExporter.isEmpty(board)) {
            throw new IllegalArgumentException("O board está vazio: não há nada para exportar.");
        }
        Path absolute = file.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".boardvisual-", ".svg.tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                write(board, title, background, out);
            }
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Escreve o SVG em {@code out} (separado de {@link #export} para os testes). */
    void write(Board board, String title, PngExporter.Background background, Writer out) throws IOException {
        if (PngExporter.isEmpty(board)) {
            throw new IllegalArgumentException("O board está vazio: não há nada para exportar.");
        }
        new Document(board, title, background, out).write();
    }

    /** Área do documento: a mesma do PNG e do PDF (os cards e uma margem). */
    public static Box bounds(Board board) {
        return PdfExporter.contentBounds(board);
    }

    /** Nome sugerido: o mesmo do PNG ({@link PngExporter#suggestedFileName}), com ".svg". */
    public static String suggestedFileName(List<String> path) {
        return PngExporter.baseFileName(path) + ".svg";
    }

    /** O arquivo escolhido, com ".svg" no fim se o usuário não digitou. */
    public static Path withSvgExtension(Path chosen) {
        return PngExporter.withExtension(chosen, ".svg");
    }

    // ================================================================ documento

    /** Um SVG sendo escrito: o board, a origem da área e o que já foi usado (ids, imagens). */
    private final class Document {

        private final Board board;
        private final String title;
        private final PngExporter.Background background;
        private final Xml xml;
        private final Box bounds;
        private final Map<Card, String> clipIds = new IdentityHashMap<>();
        private final Map<String, EmbeddedImage> imageCache = new HashMap<>();
        private long embeddedBytes;

        Document(Board board, String title, PngExporter.Background background, Writer out) {
            this.board = board;
            this.title = title == null || title.isBlank() ? board.getName() : title;
            this.background = background;
            this.xml = new Xml(out);
            this.bounds = bounds(board);
        }

        void write() throws IOException {
            xml.raw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.open("svg", "xmlns", SVG_NS, "xmlns:xlink", XLINK_NS, "version", "1.1",
                    "width", num(bounds.width()), "height", num(bounds.height()),
                    "viewBox", "0 0 " + num(bounds.width()) + " " + num(bounds.height()));
            xml.element("title", title == null ? "" : title);
            xml.element("desc", "Board exportado pelo BoardVisual.");
            writeDefs();
            if (background != PngExporter.Background.TRANSPARENT) {
                xml.empty("rect", "id", "fundo", "x", "0", "y", "0",
                        "width", num(bounds.width()), "height", num(bounds.height()), "fill", PAGE_COLOR);
            }
            // Como na tela (BoardView, BoardRenderer): seções atrás das setas, setas,
            // textos das setas acima de todas as linhas e os cards na ordem salva.
            List<Card> frames = new ArrayList<>();
            List<Card> cards = new ArrayList<>();
            for (Card card : board.getCards()) {
                (card.isFrame() ? frames : cards).add(card);
            }
            xml.open("g", "id", "secoes");
            int n = 0;
            for (Card frame : frames) {
                writeFrame(frame, "secao-" + ++n);
            }
            xml.close("g");
            xml.open("g", "id", "setas");
            n = 0;
            for (Connection connection : board.getConnections()) {
                writeConnection(connection, "seta-" + ++n);
            }
            xml.close("g");
            xml.open("g", "id", "textos-das-setas");
            for (Connection connection : board.getConnections()) {
                writeConnectionLabel(connection);
            }
            xml.close("g");
            xml.open("g", "id", "cards");
            n = 0;
            for (Card card : cards) {
                writeCard(card, "card-" + ++n);
            }
            xml.close("g");
            xml.close("svg");
        }

        /** Sombra dos cards e os recortes de cantos arredondados das imagens (um por card de imagem). */
        private void writeDefs() throws IOException {
            xml.open("defs");
            xml.open("filter", "id", SHADOW_ID, "x", "-25%", "y", "-25%", "width", "150%", "height", "150%",
                    "color-interpolation-filters", "sRGB");
            xml.empty("feGaussianBlur", "in", "SourceAlpha", "stdDeviation", num(SHADOW_BLUR));
            xml.empty("feOffset", "dx", "0", "dy", num(SHADOW_OFFSET_Y), "result", "deslocada");
            xml.empty("feFlood", "flood-color", "#000000", "flood-opacity", num(SHADOW_OPACITY));
            xml.empty("feComposite", "in2", "deslocada", "operator", "in");
            xml.open("feMerge");
            xml.empty("feMergeNode");
            xml.empty("feMergeNode", "in", "SourceGraphic");
            xml.close("feMerge");
            xml.close("filter");
            int n = 0;
            for (Card card : board.getCards()) {
                if (!card.hasImage() || card.isFrame()) {
                    continue;
                }
                String id = CLIP_ID_PREFIX + ++n;
                clipIds.put(card, id);
                // Como o CardView: cantos do card, na largura toda, até o fim do card (com
                // legenda, os cantos de baixo da imagem ficam retos). Coordenadas do card.
                double inset = CardGeometry.IMAGE_INSET;
                xml.open("clipPath", "id", id);
                xml.empty("rect", "x", num(inset), "y", num(inset),
                        "width", num(Math.max(0, card.getWidth() - 2 * inset)),
                        "height", num(Math.max(0, card.getHeight() - 2 * inset)),
                        "rx", num(CardGeometry.IMAGE_CORNER), "ry", num(CardGeometry.IMAGE_CORNER));
                xml.close("clipPath");
            }
            xml.close("defs");
        }

        // ------------------------------------------------------------ cards

        private void writeCard(Card card, String id) throws IOException {
            double w = card.getWidth();
            double h = card.getHeight();
            CardShape shape = card.getShape();
            Paint color = paint(card.getColor());
            Optional<String> uri = WebLink.toBrowserUri(card.getLink());

            xml.open("g", "id", id, "transform", translate(card.getX() - bounds.x(), card.getY() - bounds.y()));
            if (uri.isPresent()) {
                // O card todo é clicável e abre o site (como o card todo no PDF).
                openLink(uri.get());
            }
            xml.open("g", "filter", "url(#" + SHADOW_ID + ")");
            if (card.hasChildBoard()) {
                // A "folha" atrás do card de board aninhado: uma borda e um fundo, deslocados.
                writeShape(shape, STACK_OFFSET, STACK_OFFSET, w, h, CARD_RADIUS, new Paint(BORDER_STRONG_COLOR, 1),
                        null, 0);
                writeShape(shape, STACK_OFFSET + 1, STACK_OFFSET + 1, w - 2, h - 2, CARD_RADIUS - 1,
                        new Paint(SURFACE_COLOR, 1), null, 0);
            }
            // Fundo e borda numa forma só: a borda (1 px, por dentro) tem o centro a meio pixel da beirada.
            double half = CARD_BORDER_WIDTH / 2;
            writeShape(shape, half, half, w - CARD_BORDER_WIDTH, h - CARD_BORDER_WIDTH, CARD_RADIUS - half,
                    color, BORDER_COLOR, CARD_BORDER_WIDTH);
            xml.close("g");

            if (card.hasImage()) {
                writeImage(card);
            }
            writeText(layout.cardText(card), 0, 0, TEXT_COLOR);
            writeButtons(card);
            writeMarkers(card, Optional.empty());
            if (uri.isPresent()) {
                xml.close("a");
            }
            xml.close("g");
        }

        /**
         * Seção: fundo translúcido, borda um pouco mais escura que a cor, título em
         * negrito na faixa de cima. Sem sombra (como na tela). O link externo vai só
         * no marcador: a área inteira cobriria os cards de dentro (como no PDF).
         */
        private void writeFrame(Card frame, String id) throws IOException {
            double w = frame.getWidth();
            double h = frame.getHeight();
            Paint color = paint(frame.getColor());
            xml.open("g", "id", id, "transform", translate(frame.getX() - bounds.x(), frame.getY() - bounds.y()));
            double border = FRAME_BORDER_WIDTH;
            writeShape(CardShape.FRAME, border, border, w - 2 * border, h - 2 * border, FRAME_FILL_RADIUS,
                    new Paint(color.hex(), color.opacity() * FRAME_FILL_OPACITY), null, 0);
            double half = border / 2;
            writeShape(CardShape.FRAME, half, half, w - border, h - border, CARD_RADIUS - half, null,
                    derive(color, FRAME_BORDER_DERIVE).hex(), border);
            writeText(layout.cardText(frame), 0, 0, TEXT_COLOR);
            writeButtons(frame);
            writeMarkers(frame, WebLink.toBrowserUri(frame.getLink()));
            xml.close("g");
        }

        /**
         * Retângulo de cantos arredondados, ou o path do formato (o mesmo
         * {@code -fx-shape} do app.css) escalado para a caixa.
         */
        private void writeShape(CardShape shape, double x, double y, double w, double h, double radius,
                                Paint fill, String stroke, double strokeWidth) throws IOException {
            double width = Math.max(0, w);
            double height = Math.max(0, h);
            List<String> attributes = new ArrayList<>();
            switch (shape) {
                case RECTANGLE, FRAME -> {
                    double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
                    attributes.addAll(List.of("x", num(x), "y", num(y), "width", num(width), "height", num(height),
                            "rx", num(r), "ry", num(r)));
                }
                case ELLIPSE -> attributes.addAll(List.of("d", scalePath(CardGeometry.ELLIPSE_PATH, x, y, width, height)));
                case DIAMOND -> attributes.addAll(List.of("d", scalePath(CardGeometry.DIAMOND_PATH, x, y, width, height)));
            }
            paintAttributes(attributes, fill, stroke, strokeWidth);
            xml.empty(shape == CardShape.RECTANGLE || shape == CardShape.FRAME ? "rect" : "path",
                    attributes.toArray(new String[0]));
        }

        private void paintAttributes(List<String> attributes, Paint fill, String stroke, double strokeWidth) {
            if (fill == null) {
                attributes.addAll(List.of("fill", "none"));
            } else {
                attributes.addAll(List.of("fill", fill.hex()));
                if (fill.opacity() < 1) {
                    attributes.addAll(List.of("fill-opacity", num(fill.opacity())));
                }
            }
            if (stroke != null) {
                attributes.addAll(List.of("stroke", stroke, "stroke-width", num(strokeWidth)));
            }
        }

        /**
         * Imagem no topo do card, na largura toda e com a proporção dela, recortada
         * com os cantos do card. Arquivo que falta (ou não abre) vira o aviso, como
         * na tela.
         */
        private void writeImage(Card card) throws IOException {
            double inset = CardGeometry.IMAGE_INSET;
            double boxWidth = Math.max(0, card.getWidth() - 2 * inset);
            double boxHeight = Math.max(0, card.imageHeight(card.getWidth()) - 2 * inset);
            String clip = "url(#" + clipIds.get(card) + ")";
            EmbeddedImage image = embed(card.getImageFile());
            xml.open("g", "clip-path", clip);
            if (image != null && image != EmbeddedImage.SKIPPED) {
                xml.empty("image", "x", num(inset), "y", num(inset), "width", num(boxWidth), "height", num(boxHeight),
                        "preserveAspectRatio", "xMidYMid meet", "xlink:href", image.dataUri());
                xml.close("g");
                return;
            }
            xml.empty("rect", "x", num(inset), "y", num(inset), "width", num(boxWidth), "height", num(boxHeight),
                    "fill", MISSING_IMAGE_COLOR);
            xml.close("g");
            writeText(image == null ? layout.missingImageText(card) : layout.imageNotice(card, IMAGE_SKIPPED_TEXT),
                    0, 0, MUTED_TEXT_COLOR);
        }

        /** A imagem pronta para embutir, ou {@code null} (falta) ou {@link EmbeddedImage#SKIPPED} (limite). */
        private EmbeddedImage embed(String fileName) {
            EmbeddedImage image = imageCache.computeIfAbsent(fileName, name -> readImage(images, name));
            if (image == EmbeddedImage.MISSING) {
                return null;
            }
            if (embeddedBytes + image.size() > maxEmbeddedImageBytes) {
                return EmbeddedImage.SKIPPED;
            }
            embeddedBytes += image.size();
            return image;
        }

        /** Botão do painel flutuante e/ou de entrar no board aninhado, embaixo no meio (como na tela). */
        private void writeButtons(Card card) throws IOException {
            List<String> icons = new ArrayList<>();
            String panelIcon = Icons.panelMode(card.getPanelMode());
            if (panelIcon != null) {
                icons.add(panelIcon);
            }
            if (card.hasChildBoard()) {
                icons.add(Icons.ENTER);
            }
            if (icons.isEmpty()) {
                return;
            }
            Point center = CardGeometry.panelButtonPoint(card.getShape(), card.getWidth(), card.getHeight());
            for (String icon : icons) {
                xml.open("g");
                xml.empty("circle", "cx", num(center.x()), "cy", num(center.y()), "r", num(BUTTON_SIZE / 2 - 0.5),
                        "fill", SURFACE_COLOR, "stroke", BORDER_COLOR, "stroke-width", "1");
                writeIcon(icon, translate(center.x() - ICON_CENTER, center.y() - ICON_CENTER), TEXT_COLOR,
                        ICON_STROKE_WIDTH);
                xml.close("g");
            }
        }

        /**
         * Marcadores na borda de cima (clipe do link, seta do link para outro board),
         * desenhados como na tela. O link para outro board não tem destino no SVG:
         * fica só o desenho (esmaecido e riscado se o board não existe mais).
         *
         * @param link o endereço para pôr só no marcador do link (seção); vazio se o card todo já é o link
         */
        private void writeMarkers(Card card, Optional<String> link) throws IOException {
            for (CardMarkers.Spot spot : CardMarkers.layout(card)) {
                boolean broken = spot.kind() == CardMarkers.Kind.BOARD_LINK && !boardExists.test(card.getBoardLink());
                boolean linked = spot.kind() == CardMarkers.Kind.LINK && link.isPresent();
                String icon = switch (spot.kind()) {
                    case LINK -> Icons.LINK;
                    case BOARD_LINK -> broken ? Icons.BOARD_LINK + Icons.STRIKE : Icons.BOARD_LINK;
                };
                double rotation = spot.kind() == CardMarkers.Kind.LINK ? LINK_ICON_ROTATION : 0;
                if (linked) {
                    openLink(link.get());
                }
                if (broken) {
                    xml.open("g", "opacity", num(BROKEN_MARKER_OPACITY));
                } else {
                    xml.open("g");
                }
                xml.empty("circle", "cx", num(spot.centerX()), "cy", num(spot.centerY()),
                        "r", num(spot.size() / 2 - 0.5), "fill", SURFACE_COLOR,
                        "stroke", broken ? DANGER_COLOR : BORDER_STRONG_COLOR, "stroke-width", "1");
                String transform = translate(spot.centerX(), spot.centerY())
                        + (rotation != 0 ? " rotate(" + num(rotation) + ")" : "")
                        + " scale(" + num(MARKER_ICON_SCALE) + ") " + translate(-ICON_CENTER, -ICON_CENTER);
                writeIcon(icon, transform, broken ? DANGER_COLOR : ACCENT_COLOR, MARKER_ICON_STROKE_WIDTH);
                xml.close("g");
                if (linked) {
                    xml.close("a");
                }
            }
        }

        private void writeIcon(String path, String transform, String color, double strokeWidth) throws IOException {
            xml.empty("path", "d", path, "transform", transform, "fill", "none", "stroke", color,
                    "stroke-width", num(strokeWidth), "stroke-linecap", "round", "stroke-linejoin", "round");
        }

        /** {@code <a>} que abre o endereço (já conferido: só http/https, só ASCII) numa aba nova. */
        private void openLink(String uri) throws IOException {
            xml.open("a", "xlink:href", uri, "target", "_blank");
            xml.element("title", uri);
        }

        // ------------------------------------------------------------ setas

        private void writeConnection(Connection connection, String id) throws IOException {
            ConnectionStyle style = connection.getStyle();
            Point[] line = ConnectionGeometry.line(connection);
            xml.open("g", "id", id);
            List<String> attributes = new ArrayList<>(List.of(
                    "x1", num(line[0].x() - bounds.x()), "y1", num(line[0].y() - bounds.y()),
                    "x2", num(line[1].x() - bounds.x()), "y2", num(line[1].y() - bounds.y()),
                    "stroke", CONNECTION_COLOR, "stroke-width", num(CONNECTION_WIDTH),
                    // O Line do JavaFX termina em "quadrado" (passa 1 px da ponta), também nos traços.
                    "stroke-linecap", "square"));
            if (style.isDashed()) {
                attributes.addAll(List.of("stroke-dasharray", CONNECTION_DASHES));
            }
            xml.empty("line", attributes.toArray(new String[0]));
            Point start = ConnectionGeometry.start(connection);
            Point tip = ConnectionGeometry.tip(connection);
            if (style.hasEndArrow()) {
                writeArrowHead(ConnectionGeometry.arrowHead(tip, start));
            }
            if (style.hasStartArrow()) {
                writeArrowHead(ConnectionGeometry.arrowHead(start, tip));
            }
            xml.close("g");
        }

        private void writeArrowHead(Point[] corners) throws IOException {
            StringBuilder points = new StringBuilder();
            for (Point p : corners) {
                if (!points.isEmpty()) {
                    points.append(' ');
                }
                points.append(num(p.x() - bounds.x())).append(',').append(num(p.y() - bounds.y()));
            }
            xml.empty("polygon", "points", points.toString(), "fill", CONNECTION_COLOR);
        }

        private void writeConnectionLabel(Connection connection) throws IOException {
            CardTextLayout.LabelBox box = layout.connectionLabel(connection, board.hasReverse(connection));
            if (box == null) {
                return;
            }
            xml.open("g");
            xml.empty("rect", "x", num(box.x() - bounds.x()), "y", num(box.y() - bounds.y()),
                    "width", num(box.width()), "height", num(box.height()),
                    "rx", num(LABEL_RADIUS), "ry", num(LABEL_RADIUS), "fill", LABEL_BACKGROUND);
            writeText(box.text(), -bounds.x(), -bounds.y(), TEXT_COLOR);
            xml.close("g");
        }

        // ------------------------------------------------------------ texto

        /**
         * Um bloco de texto: um {@code <text>} com um {@code <tspan>} por linha, cada
         * um na posição calculada (linhas em branco não geram nada). Tudo numa
         * linha do arquivo: espaços entre os tspans virariam texto.
         */
        private void writeText(CardTextLayout.Block block, double dx, double dy, String color) throws IOException {
            if (block.isEmpty()) {
                return;
            }
            StringBuilder spans = new StringBuilder();
            for (CardTextLayout.Line line : block.lines()) {
                String clean = cleanText(line.text());
                if (clean.isBlank()) {
                    continue;
                }
                spans.append("<tspan x=\"").append(num(line.x() + dx)).append("\" y=\"")
                        .append(num(line.baseline() + dy)).append("\" xml:space=\"preserve\">")
                        .append(escape(clean)).append("</tspan>");
            }
            if (spans.isEmpty()) {
                return;
            }
            StringBuilder text = new StringBuilder("<text font-family=\"").append(escape(FONT_FAMILY))
                    .append("\" font-size=\"").append(num(block.fontSize())).append('"');
            if (block.bold()) {
                text.append(" font-weight=\"bold\"");
            }
            text.append(" fill=\"").append(color).append('"');
            if (block.anchor() == CardTextLayout.Anchor.MIDDLE) {
                text.append(" text-anchor=\"middle\"");
            }
            // Sem kerning, como o JavaFX: as linhas ficam com a largura medida (os navegadores
            // aproximam pares como "Va" e a linha sairia mais estreita que na tela).
            text.append(" style=\"").append(TEXT_STYLE).append('"');
            text.append('>').append(spans).append("</text>");
            xml.line(text.toString());
        }

        private String translate(double x, double y) {
            return "translate(" + num(x) + " " + num(y) + ")";
        }
    }

    // ================================================================ imagens

    /** Uma imagem pronta para embutir (data URI) e o tamanho que ela soma ao arquivo. */
    record EmbeddedImage(String dataUri, long size) {
        /** O arquivo não existe, não abre ou não é uma imagem conhecida. */
        static final EmbeddedImage MISSING = new EmbeddedImage("", 0);
        /** A imagem existe, mas passaria do limite de tamanho do SVG. */
        static final EmbeddedImage SKIPPED = new EmbeddedImage("", 0);
    }

    /**
     * Lê e confere a imagem: o tipo vem do conteúdo do arquivo (não do nome) e
     * ela precisa abrir de verdade (como na tela, onde uma imagem que não abre
     * mostra o aviso). PNG, JPEG e GIF vão como estão; BMP (que nem todo editor
     * de SVG mostra) é convertido para PNG.
     */
    static EmbeddedImage readImage(ImageSource source, String fileName) {
        try {
            byte[] bytes = source.read(fileName);
            if (bytes == null || bytes.length == 0) {
                return EmbeddedImage.MISSING;
            }
            String type = imageType(bytes);
            if (type == null) {
                return EmbeddedImage.MISSING;
            }
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) {
                return EmbeddedImage.MISSING;
            }
            if (type.equals("image/bmp")) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, "png", png)) {
                    return EmbeddedImage.MISSING;
                }
                bytes = png.toByteArray();
                type = "image/png";
            }
            String data = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return new EmbeddedImage(data, bytes.length);
        } catch (IOException | RuntimeException e) {
            return EmbeddedImage.MISSING;
        }
    }

    /** Tipo da imagem pelos primeiros bytes do arquivo, ou {@code null} se não é um tipo conhecido. */
    static String imageType(byte[] bytes) {
        if (startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (startsWith(bytes, 'B', 'M')) {
            return "image/bmp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ cores

    /** Uma cor do SVG: {@code #rrggbb} e a opacidade (0 a 1). */
    record Paint(String hex, double opacity) {
    }

    private static final Pattern HEX_COLOR = Pattern.compile("#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    /**
     * A cor de um card. O app grava {@code #RRGGBB}; aceita também {@code #RGB},
     * {@code #RGBA} e {@code #RRGGBBAA}. Qualquer outra coisa (arquivo editado à
     * mão) vira a cor padrão: nada do arquivo chega cru ao SVG.
     */
    static Paint paint(String value) {
        String color = value == null ? "" : value.strip();
        Matcher m = HEX_COLOR.matcher(color);
        if (!m.matches()) {
            return paint(Card.DEFAULT_COLOR);
        }
        String digits = m.group(1);
        if (digits.length() <= 4) {
            StringBuilder longForm = new StringBuilder();
            for (char c : digits.toCharArray()) {
                longForm.append(c).append(c);
            }
            digits = longForm.toString();
        }
        double opacity = digits.length() == 8 ? Integer.parseInt(digits.substring(6, 8), 16) / 255.0 : 1;
        return new Paint("#" + digits.substring(0, 6).toLowerCase(Locale.ROOT), opacity);
    }

    /**
     * {@code derive(cor, deslocamento)} do CSS do JavaFX, para escurecer
     * (deslocamento negativo, como a borda da seção): a mesma conta do JavaFX
     * (brilho percebido para o ajuste fino, depois o brilho no HSB).
     */
    static Paint derive(Paint color, double brightness) {
        int rgb = Integer.parseInt(color.hex().substring(1), 16);
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double perceived = 0.3 * r + 0.59 * g + 0.11 * b;
        double amount = brightness;
        if (amount > 0) {
            // Clarear: não usado aqui (seria o outro ramo da conta do JavaFX).
            throw new IllegalArgumentException("Só escurecer é suportado.");
        }
        if (perceived < 0.2) {
            amount *= 0.6;
        }
        amount = Math.max(-1, amount);
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double saturation = max == 0 ? 0 : (max - min) / max;
        double hue = 0;
        if (saturation != 0) {
            double rc = (max - r) / (max - min);
            double gc = (max - g) / (max - min);
            double bc = (max - b) / (max - min);
            hue = r == max ? bc - gc : g == max ? 2 + rc - bc : 4 + gc - rc;
            hue /= 6;
            if (hue < 0) {
                hue += 1;
            }
        }
        double value = Math.max(0, Math.min(1, max * (amount + 1)));
        // O JavaFX passa o matiz em graus inteiros para Color.hsb.
        double h = ((int) (hue * 360) % 360) / 360.0 * 6;
        double f = h - Math.floor(h);
        double p = value * (1 - saturation);
        double q = value * (1 - saturation * f);
        double t = value * (1 - saturation * (1 - f));
        double[] out = switch ((int) h) {
            case 0 -> new double[] {value, t, p};
            case 1 -> new double[] {q, value, p};
            case 2 -> new double[] {p, value, t};
            case 3 -> new double[] {p, q, value};
            case 4 -> new double[] {t, p, value};
            default -> new double[] {value, p, q};
        };
        if (saturation == 0) {
            out = new double[] {value, value, value};
        }
        return new Paint(String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(out[0] * 255), Math.round(out[1] * 255), Math.round(out[2] * 255)), color.opacity());
    }

    // ================================================================ XML

    /**
     * Número para o SVG: até 3 casas, sempre com ponto (independe do idioma do
     * computador), nunca em notação científica; não finito vira 0.
     */
    static String num(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        String text = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    /**
     * Texto seguro para o XML (conteúdo ou atributo): escapa {@code & < > " '}
     * e quebras/tabulações, e tira o que o XML 1.0 não aceita (controles,
     * surrogates soltos). Texto do usuário nunca vira marcação.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        int i = 0;
        while (i < value.length()) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (!isXmlChar(cp)) {
                continue;
            }
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                case '\t' -> out.append("&#9;");
                case '\n' -> out.append("&#10;");
                case '\r' -> out.append("&#13;");
                default -> out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    /** Caractere aceito pelo XML 1.0 (surrogate solto não é: {@code codePointAt} o devolve sozinho). */
    static boolean isXmlChar(int cp) {
        return cp == 0x9 || cp == 0xA || cp == 0xD
                || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    /** Linha de texto para um tspan: tabulação vira espaço (o SVG não tem tabulação). */
    private static String cleanText(String line) {
        return line.replace('\t', ' ');
    }

    private static final Pattern PATH_TOKEN = Pattern.compile("[A-Za-z]|-?(?:\\d+\\.?\\d*|\\.\\d+)");

    /**
     * Um path desenhado no quadrado 100×100 (os {@code -fx-shape} do app.css),
     * escalado para a caixa ({@code x}, {@code y}, {@code w}, {@code h}). Aceita
     * os comandos absolutos M, L, H, V, A e Z (os que os formatos usam).
     */
    static String scalePath(String path, double x, double y, double w, double h) {
        double sx = w / 100;
        double sy = h / 100;
        List<String> tokens = new ArrayList<>();
        Matcher m = PATH_TOKEN.matcher(path);
        while (m.find()) {
            tokens.add(m.group());
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        char command = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                i++;
                if (command == 'Z') {
                    out.append(out.isEmpty() ? "" : " ").append('Z');
                    continue;
                }
            }
            out.append(out.isEmpty() ? "" : " ").append(command);
            switch (command) {
                case 'M', 'L' -> {
                    out.append(' ').append(num(x + number(tokens, i) * sx))
                            .append(' ').append(num(y + number(tokens, i + 1) * sy));
                    i += 2;
                }
                case 'H' -> {
                    out.append(' ').append(num(x + number(tokens, i) * sx));
                    i += 1;
                }
                case 'V' -> {
                    out.append(' ').append(num(y + number(tokens, i) * sy));
                    i += 1;
                }
                case 'A' -> {
                    out.append(' ').append(num(number(tokens, i) * sx))
                            .append(' ').append(num(number(tokens, i + 1) * sy))
                            .append(' ').append(tokens.get(i + 2))
                            .append(' ').append(tokens.get(i + 3))
                            .append(' ').append(tokens.get(i + 4))
                            .append(' ').append(num(x + number(tokens, i + 5) * sx))
                            .append(' ').append(num(y + number(tokens, i + 6) * sy));
                    i += 7;
                }
                default -> throw new IllegalArgumentException("Comando de path não suportado: " + command);
            }
        }
        return out.toString();
    }

    private static double number(List<String> tokens, int index) {
        return Double.parseDouble(tokens.get(index));
    }

    /** Escreve o XML com recuo, escapando cada valor de atributo. */
    private static final class Xml {

        private final Writer out;
        private int depth;

        Xml(Writer out) {
            this.out = out;
        }

        void raw(String text) throws IOException {
            out.write(text);
        }

        /** Uma linha pronta (já escapada), no recuo atual. */
        void line(String text) throws IOException {
            indent();
            out.write(text);
            out.write('\n');
        }

        void open(String name, String... attributes) throws IOException {
            indent();
            tag(name, attributes);
            out.write(">\n");
            depth++;
        }

        void close(String name) throws IOException {
            depth--;
            indent();
            out.write("</" + name + ">\n");
        }

        void empty(String name, String... attributes) throws IOException {
            indent();
            tag(name, attributes);
            out.write("/>\n");
        }

        /** Elemento só com texto ({@code <title>}, {@code <desc>}). */
        void element(String name, String text) throws IOException {
            indent();
            out.write("<" + name + ">" + escape(text) + "</" + name + ">\n");
        }

        private void tag(String name, String... attributes) throws IOException {
            out.write('<');
            out.write(name);
            for (int i = 0; i + 1 < attributes.length; i += 2) {
                out.write(' ');
                out.write(attributes[i]);
                out.write("=\"");
                out.write(escape(attributes[i + 1]));
                out.write('"');
            }
        }

        private void indent() throws IOException {
            for (int i = 0; i < depth; i++) {
                out.write("  ");
            }
        }
    }
}
