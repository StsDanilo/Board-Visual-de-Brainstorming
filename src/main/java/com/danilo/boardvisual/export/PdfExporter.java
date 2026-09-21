package com.danilo.boardvisual.export;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardNames;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.PanelMode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PageMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporta um board e todos os seus boards aninhados para um PDF navegável.
 *
 * <pre>
 * página do board principal
 *   páginas de "Detalhes" dos painéis flutuantes desse board
 *   página do 1º board filho
 *     detalhes dele, filhos dele...
 *   página do 2º board filho ...
 * </pre>
 *
 * Em cada página de board: o caminho no topo (cada nível anterior é um link),
 * o board como imagem, e áreas clicáveis por cima — card de board aninhado
 * leva à página do filho, painel flutuante leva à página de detalhes. Uma
 * camada de texto invisível por cima dos cards permite buscar e copiar.
 * Os marcadores do PDF (barra lateral) espelham a mesma árvore.
 *
 * A imagem de cada board vem de fora ({@link BoardImageRenderer}): quem sabe
 * desenhar cards é a tela (JavaFX). Assim esta classe não depende do JavaFX e
 * é testável com uma imagem qualquer.
 */
public final class PdfExporter {

    /** Desenha a região {@code bounds} de um board (coordenadas do board). */
    @FunctionalInterface
    public interface BoardImageRenderer {
        BufferedImage render(Board board, Box bounds, double pixelsPerUnit);
    }

    /** Espaço em volta dos cards (sombra, pilha do board aninhado). */
    static final double CONTENT_MARGIN = 32;
    /** Altura da faixa do caminho no topo das páginas de board. */
    static final float HEADER_HEIGHT = 40;
    /** O PDF não aceita páginas maiores que 14.400 pt; boards maiores são reduzidos. */
    static final double MAX_PAGE_SIZE = 14_000;
    /** Pixels da imagem por ponto da página (2 = nítido ao dar zoom e ao imprimir). */
    static final double IMAGE_RESOLUTION = 2;
    /** Limites da imagem de um board, para não estourar a memória. */
    static final double MAX_IMAGE_SIDE = 8_000;
    static final double MAX_IMAGE_PIXELS = 40_000_000;
    /** Tamanho da página de um board vazio. */
    static final Box EMPTY_BOARD = new Box(0, 0, 480, 280);

    private static final float PADDING = 24;
    private static final float MIN_PAGE_WIDTH = 320;
    private static final float PATH_FONT_SIZE = 11;
    private static final float[] TEXT_COLOR = {0.13f, 0.14f, 0.16f};
    private static final float[] MUTED_COLOR = {0.45f, 0.47f, 0.5f};
    private static final float[] LINK_COLOR = {0.23f, 0.42f, 0.95f};
    private static final float[] RULE_COLOR = {0.88f, 0.88f, 0.87f};

    // Páginas de detalhes: A4 retrato.
    private static final PDRectangle DETAILS_PAGE = PDRectangle.A4;
    private static final float DETAILS_MARGIN = 56;
    private static final float DETAILS_FONT_SIZE = 12;
    private static final float DETAILS_LEADING = 17;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float BULLET_INDENT = 16;

    private final BoardImageRenderer renderer;

    public PdfExporter(BoardImageRenderer renderer) {
        this.renderer = renderer;
    }

    /** Grava o PDF (num arquivo temporário e depois no lugar, como o salvamento em JSON). */
    public void export(Board root, Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".boardvisual-", ".pdf.tmp");
        try (PDDocument document = build(root)) {
            document.save(temp.toFile());
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Monta o documento em memória (separado de {@link #export} para os testes). */
    PDDocument build(Board root) throws IOException {
        PDDocument document = new PDDocument();
        try {
            BoardNode rootNode = BoardNode.tree(root, BoardNames.ofRoot(root), null);
            List<BoardNode> order = new ArrayList<>();
            rootNode.collect(order);

            // 1ª passada: criar todas as páginas, para os links terem destino.
            for (BoardNode node : order) {
                node.prepare(renderer);
                node.page = new PDPage(new PDRectangle(node.pageWidth, node.pageHeight));
                document.addPage(node.page);
                for (PanelDetails details : node.details) {
                    details.layout();
                    for (int i = 0; i < details.pageCount(); i++) {
                        PDPage page = new PDPage(DETAILS_PAGE);
                        details.pages.add(page);
                        document.addPage(page);
                    }
                }
            }
            // 2ª passada: desenhar.
            for (BoardNode node : order) {
                drawBoardPage(document, node);
                for (PanelDetails details : node.details) {
                    drawDetailsPages(document, details);
                }
            }

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            outline.addLast(rootNode.outlineItem());
            outline.openNode();
            document.getDocumentCatalog().setPageMode(PageMode.USE_OUTLINES);
            document.getDocumentInformation().setTitle(rootNode.name);
            document.getDocumentInformation().setCreator("BoardVisual");
            return document;
        } catch (IOException | RuntimeException e) {
            document.close();
            throw e;
        }
    }

    // ------------------------------------------------------------ página do board

    private void drawBoardPage(PDDocument document, BoardNode node) throws IOException {
        PDPage page = node.page;
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            if (node.image == null) {
                text(cs, PdfText.REGULAR, 12, MUTED_COLOR, "Board vazio",
                        (node.pageWidth - PdfText.width(PdfText.REGULAR, 12, "Board vazio")) / 2,
                        (node.pageHeight - HEADER_HEIGHT) / 2);
            } else {
                PDImageXObject image = LosslessFactory.createFromImage(document, node.image);
                cs.drawImage(image, node.imageX, 0, node.imageWidth, node.imageHeight);
            }
            // Depois da imagem, para a linha do cabeçalho ficar por cima dela.
            drawPath(cs, page, node.ancestry(), null);
            rule(cs, 0, node.pageWidth, node.pageHeight - HEADER_HEIGHT);
            if (node.image == null) {
                return;
            }

            for (Card card : node.board.getCards()) {
                PDRectangle rect = node.rectOf(card);
                writeInvisibleText(cs, card.getText(), rect);
                PDPage target = node.linkTarget(card);
                if (target != null) {
                    link(page, rect, target);
                }
            }
        }
    }

    /**
     * Texto dos cards invisível, na posição do card: não aparece (a imagem já
     * mostra), mas permite buscar e copiar.
     */
    private static void writeInvisibleText(PDPageContentStream cs, String text, PDRectangle rect) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] lines = text.strip().split("\n");
        float size = Math.max(4, Math.min(12, rect.getHeight() / lines.length / 1.2f));
        cs.beginText();
        cs.setRenderingMode(RenderingMode.NEITHER);
        cs.setFont(PdfText.REGULAR, size);
        float y = rect.getUpperRightY() - size;
        for (String line : lines) {
            String clean = PdfText.sanitize(line.strip());
            float width = PdfText.width(PdfText.REGULAR, size, clean);
            cs.setHorizontalScaling(width > rect.getWidth() ? 100 * rect.getWidth() / width : 100);
            cs.setTextMatrix(new Matrix(1, 0, 0, 1, rect.getLowerLeftX(), y));
            cs.showText(clean);
            y -= size * 1.2f;
        }
        cs.endText();
    }

    // --------------------------------------------------------- páginas de detalhes

    private void drawDetailsPages(PDDocument document, PanelDetails details) throws IOException {
        List<List<DetailLine>> pages = details.paginate();
        for (int i = 0; i < details.pages.size(); i++) {
            PDPage page = details.pages.get(i);
            float top = DETAILS_PAGE.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                drawPath(cs, page, details.owner.ancestry(), "Detalhes");
                rule(cs, 0, DETAILS_PAGE.getWidth(), top - HEADER_HEIGHT);

                float y = top - HEADER_HEIGHT - DETAILS_MARGIN / 2;
                if (i == 0) {
                    String title = PdfText.ellipsize(PdfText.BOLD, TITLE_FONT_SIZE,
                            BoardNames.firstLine(details.card.getText()).isEmpty()
                                    ? "Painel sem título" : BoardNames.firstLine(details.card.getText()),
                            PanelDetails.textWidth());
                    y -= TITLE_FONT_SIZE;
                    text(cs, PdfText.BOLD, TITLE_FONT_SIZE, TEXT_COLOR, title, DETAILS_MARGIN, y);
                    y -= 22;
                    String back = "‹ Voltar ao board " + details.owner.name;
                    back = PdfText.ellipsize(PdfText.REGULAR, 11, back, PanelDetails.textWidth());
                    text(cs, PdfText.REGULAR, 11, LINK_COLOR, back, DETAILS_MARGIN, y);
                    link(page, new PDRectangle(DETAILS_MARGIN, y - 3,
                            PdfText.width(PdfText.REGULAR, 11, back), 15), details.owner.page);
                    y -= 30;
                } else {
                    y -= 11;
                    text(cs, PdfText.REGULAR, 11, MUTED_COLOR, "(continuação)", DETAILS_MARGIN, y);
                    y -= 26;
                }
                for (DetailLine line : pages.get(i)) {
                    if (line.bullet()) {
                        text(cs, PdfText.REGULAR, DETAILS_FONT_SIZE, TEXT_COLOR, "•", DETAILS_MARGIN, y);
                    }
                    text(cs, PdfText.REGULAR, DETAILS_FONT_SIZE, TEXT_COLOR, line.text(),
                            DETAILS_MARGIN + line.indent(), y);
                    y -= DETAILS_LEADING;
                }
            }
        }
    }

    // -------------------------------------------------------------- utilitários

    /**
     * Caminho no topo: {@code Principal › board 2 › board 3}. Os níveis
     * anteriores ao atual são links para as páginas deles. {@code suffix}
     * (ex.: "Detalhes") vira o último nível, e aí todos os boards são links.
     */
    private static void drawPath(PDPageContentStream cs, PDPage page, List<BoardNode> path, String suffix)
            throws IOException {
        float x = PADDING;
        float y = page.getMediaBox().getHeight() - HEADER_HEIGHT / 2 - PATH_FONT_SIZE / 2 + 2;
        String separator = "  ›  ";
        for (int i = 0; i < path.size(); i++) {
            BoardNode node = path.get(i);
            boolean current = suffix == null && i == path.size() - 1;
            String name = PdfText.ellipsize(PdfText.REGULAR, PATH_FONT_SIZE, node.name, 180);
            PDFont font = current ? PdfText.BOLD : PdfText.REGULAR;
            float width = PdfText.width(font, PATH_FONT_SIZE, name);
            text(cs, font, PATH_FONT_SIZE, current ? TEXT_COLOR : LINK_COLOR, name, x, y);
            if (!current) {
                link(page, new PDRectangle(x - 2, y - 4, width + 4, PATH_FONT_SIZE + 6), node.page);
            }
            x += width;
            if (i < path.size() - 1 || suffix != null) {
                text(cs, PdfText.REGULAR, PATH_FONT_SIZE, MUTED_COLOR, separator, x, y);
                x += PdfText.width(PdfText.REGULAR, PATH_FONT_SIZE, separator);
            }
        }
        if (suffix != null) {
            text(cs, PdfText.BOLD, PATH_FONT_SIZE, TEXT_COLOR, suffix, x, y);
        }
    }

    static float pathWidth(List<BoardNode> path) {
        float width = 0;
        for (BoardNode node : path) {
            width += PdfText.width(PdfText.BOLD, PATH_FONT_SIZE,
                    PdfText.ellipsize(PdfText.REGULAR, PATH_FONT_SIZE, node.name, 180));
            width += PdfText.width(PdfText.REGULAR, PATH_FONT_SIZE, "  ›  ");
        }
        return width;
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float[] color, String value,
                             float x, float y) throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(PdfText.sanitize(value));
        cs.endText();
    }

    private static void rule(PDPageContentStream cs, float x1, float x2, float y) throws IOException {
        cs.setStrokingColor(RULE_COLOR[0], RULE_COLOR[1], RULE_COLOR[2]);
        cs.setLineWidth(0.8f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
    }

    /** Área clicável sem borda que leva à página {@code target}. */
    private static void link(PDPage page, PDRectangle rect, PDPage target) throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(rect);
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(0);
        link.setBorderStyle(border);
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(target);
        link.setDestination(destination);
        page.getAnnotations().add(link);
    }

    // ------------------------------------------------------------------ árvore

    /** Um board na árvore do documento, com a página e a geometria dela. */
    private static final class BoardNode {

        final Board board;
        final String name;
        final BoardNode parent;
        final List<BoardNode> children = new ArrayList<>();
        final List<PanelDetails> details = new ArrayList<>();
        /** Card do board que leva a cada filho (mesma ordem de {@link #children}). */
        final List<Card> childCards = new ArrayList<>();

        PDPage page;
        Box bounds;
        BufferedImage image;
        /** Pontos da página por unidade do board. */
        double scale;
        float pageWidth;
        float pageHeight;
        float imageX;
        float imageWidth;
        float imageHeight;

        private BoardNode(Board board, String name, BoardNode parent) {
            this.board = board;
            this.name = name;
            this.parent = parent;
        }

        static BoardNode tree(Board board, String name, BoardNode parent) {
            BoardNode node = new BoardNode(board, name, parent);
            for (Card card : board.getCards()) {
                if (card.hasChildBoard()) {
                    node.children.add(tree(card.getChildBoard(), BoardNames.ofChild(card), node));
                    node.childCards.add(card);
                } else if (card.isPanel() && card.hasPanelContent()) {
                    node.details.add(new PanelDetails(card, node));
                }
            }
            return node;
        }

        /** Ordem das páginas: este board, depois os filhos (cada um com os seus). */
        void collect(List<BoardNode> order) {
            order.add(this);
            children.forEach(child -> child.collect(order));
        }

        List<BoardNode> ancestry() {
            List<BoardNode> path = new ArrayList<>();
            for (BoardNode n = this; n != null; n = n.parent) {
                path.add(0, n);
            }
            return path;
        }

        /** Calcula a geometria da página e pede a imagem do board. */
        void prepare(BoardImageRenderer renderer) {
            bounds = contentBounds(board);
            scale = Math.min(1, MAX_PAGE_SIZE / Math.max(bounds.width(), bounds.height()));
            imageWidth = (float) (bounds.width() * scale);
            imageHeight = (float) (bounds.height() * scale);
            pageWidth = Math.max(Math.max(imageWidth, MIN_PAGE_WIDTH), pathWidth(ancestry()) + 2 * PADDING);
            pageHeight = imageHeight + HEADER_HEIGHT;
            imageX = (pageWidth - imageWidth) / 2;
            if (!board.getCards().isEmpty()) {
                double pixelsPerUnit = Math.min(scale * IMAGE_RESOLUTION, Math.min(
                        MAX_IMAGE_SIDE / Math.max(bounds.width(), bounds.height()),
                        Math.sqrt(MAX_IMAGE_PIXELS / (bounds.width() * bounds.height()))));
                image = renderer.render(board, bounds, pixelsPerUnit);
            }
        }

        /** Retângulo do card na página (PDF: origem embaixo à esquerda). */
        PDRectangle rectOf(Card card) {
            float x = imageX + (float) ((card.getX() - bounds.x()) * scale);
            float top = (float) ((card.getY() - bounds.y()) * scale);
            float width = (float) (card.getWidth() * scale);
            float height = (float) (card.getHeight() * scale);
            return new PDRectangle(x, imageHeight - top - height, width, height);
        }

        /** Página aberta ao clicar no card, ou {@code null}. */
        PDPage linkTarget(Card card) {
            int child = childCards.indexOf(card);
            if (child >= 0) {
                return children.get(child).page;
            }
            for (PanelDetails d : details) {
                if (d.card == card) {
                    return d.pages.get(0);
                }
            }
            return null;
        }

        PDOutlineItem outlineItem() {
            PDOutlineItem item = new PDOutlineItem();
            item.setTitle(name);
            item.setDestination(page);
            for (PanelDetails d : details) {
                PDOutlineItem detailsItem = new PDOutlineItem();
                detailsItem.setTitle("Detalhes: " + BoardNames.firstLine(d.card.getText()));
                detailsItem.setDestination(d.pages.get(0));
                item.addLast(detailsItem);
            }
            children.forEach(child -> item.addLast(child.outlineItem()));
            item.openNode();
            return item;
        }
    }

    /** Área ocupada pelos cards, com margem; tamanho fixo se o board está vazio. */
    static Box contentBounds(Board board) {
        Box union = null;
        for (Card card : board.getCards()) {
            Box box = new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight());
            union = union == null ? box : union.union(box);
        }
        if (union == null) {
            return EMPTY_BOARD;
        }
        return new Box(union.x() - CONTENT_MARGIN, union.y() - CONTENT_MARGIN,
                union.width() + 2 * CONTENT_MARGIN, union.height() + 2 * CONTENT_MARGIN);
    }

    // ---------------------------------------------------------------- detalhes

    private record DetailLine(String text, float indent, boolean bullet) {
    }

    /** Conteúdo de um painel flutuante, quebrado em linhas e páginas. */
    private static final class PanelDetails {

        final Card card;
        final BoardNode owner;
        final List<PDPage> pages = new ArrayList<>();
        private List<DetailLine> lines;

        PanelDetails(Card card, BoardNode owner) {
            this.card = card;
            this.owner = owner;
        }

        static float textWidth() {
            return DETAILS_PAGE.getWidth() - 2 * DETAILS_MARGIN;
        }

        void layout() {
            lines = new ArrayList<>();
            if (card.getPanelMode() == PanelMode.LIST) {
                for (String item : card.getListItemTexts()) {
                    if (item.isBlank()) {
                        continue;
                    }
                    boolean first = true;
                    for (String line : PdfText.wrap(PdfText.REGULAR, DETAILS_FONT_SIZE, item.strip(),
                            textWidth() - BULLET_INDENT)) {
                        lines.add(new DetailLine(line, BULLET_INDENT, first));
                        first = false;
                    }
                }
            } else {
                for (String paragraph : card.getDetailText().strip().split("\n", -1)) {
                    for (String line : PdfText.wrap(PdfText.REGULAR, DETAILS_FONT_SIZE, paragraph, textWidth())) {
                        lines.add(new DetailLine(line, 0, false));
                    }
                }
            }
        }

        /** Linhas por página: a primeira tem título e link de volta; as outras, só "(continuação)". */
        private static int capacity(boolean first) {
            float top = DETAILS_PAGE.getHeight() - HEADER_HEIGHT - DETAILS_MARGIN / 2
                    - (first ? TITLE_FONT_SIZE + 22 + 30 : 11 + 26);
            return (int) ((top - DETAILS_MARGIN) / DETAILS_LEADING) + 1;
        }

        int pageCount() {
            return paginate().size();
        }

        List<List<DetailLine>> paginate() {
            List<List<DetailLine>> result = new ArrayList<>();
            int index = 0;
            do {
                int capacity = capacity(result.isEmpty());
                result.add(lines.subList(index, Math.min(lines.size(), index + capacity)));
                index += capacity;
            } while (index < lines.size());
            return result;
        }
    }
}
