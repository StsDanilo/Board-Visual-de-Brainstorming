package com.danilo.boardvisual.export;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.PanelMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExporterTest {

    // ------------------------------------------------------------ seção (frame)

    private static Card frame(double x, double y, String title) {
        Card frame = new Card(x, y);
        frame.setShape(com.danilo.boardvisual.model.CardShape.FRAME);
        frame.resize(600, 400);
        frame.setText(title);
        return frame;
    }

    @Test
    void invisibleTextOfAFrameCoversOnlyTheTitleStrip() {
        Card section = frame(0, 0, "Pesquisa");
        PDRectangle rect = new PDRectangle(10, 50, 600, 400);

        PDRectangle title = PdfExporter.textRect(section, rect, 1);

        assertEquals(450, title.getUpperRightY(), 1e-3, "no topo da seção");
        assertEquals(com.danilo.boardvisual.model.Frames.TITLE_HEIGHT, title.getHeight(), 1e-3);
        assertEquals(600, title.getWidth(), 1e-3);
    }

    @Test
    void frameTitleIsSearchableAndCardsInsideKeepTheirLinks() throws IOException {
        Board root = new Board("Seções");
        Card section = frame(0, 0, "Pesquisa de mercado");
        section.setLink("https://secao.example.com");
        Card inside = new Card(100, 120);
        inside.setText("Concorrentes");
        inside.setLink("https://card.example.com");
        Card insideBoard = new Card(350, 120);
        insideBoard.setText("Detalhes");
        insideBoard.setChildBoard(new Board(""));
        root.addCard(inside);
        root.addCard(insideBoard);
        root.addCard(section); // vai para trás dos cards

        try (PDDocument document = exporter.build(root)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);
            assertTrue(text.contains("Pesquisa de mercado"), text);
            assertTrue(text.contains("Concorrentes"), text);

            PDPage page = document.getPage(0);
            List<PDAnnotationLink> uris = uriLinks(page);
            assertEquals(2, uris.size());
            PDAnnotationLink sectionLink = uris.stream()
                    .filter(l -> uriOf(l).contains("secao")).findFirst().orElseThrow();
            PDAnnotationLink cardLink = uris.stream()
                    .filter(l -> uriOf(l).contains("card")).findFirst().orElseThrow();
            assertEquals(CardMarkers.SIZE, sectionLink.getRectangle().getWidth(), 1e-3,
                    "link da seção só no marcador");
            assertEquals(inside.getWidth(), cardLink.getRectangle().getWidth(), 1e-3, "o card de dentro, inteiro");
            assertTrue(page.getAnnotations().indexOf(sectionLink) < page.getAnnotations().indexOf(cardLink),
                    "a seção vem antes (atrás) dos cards");
            for (PDAnnotationLink link : links(page)) {
                PDRectangle r = link.getRectangle();
                assertTrue(r.getWidth() < section.getWidth() && r.getHeight() < section.getHeight(),
                        "nenhuma área clicável do tamanho da seção");
            }
            // O card-board de dentro continua levando à página dele.
            assertEquals(1, links(page).size() - uris.size(), "um link interno: o do card-board");
        }
    }

    @Test
    void invisibleTextOfAnImageCardGoesBelowTheImage() {
        Card photo = Card.createImage("foto.png", 0, 0, 320, 180);
        photo.setHeight(220); // 40 de legenda
        org.apache.pdfbox.pdmodel.common.PDRectangle card =
                new org.apache.pdfbox.pdmodel.common.PDRectangle(10, 50, 320, 220);

        var caption = PdfExporter.textRect(photo, card, 1);
        var plain = PdfExporter.textRect(new Card(0, 0), card, 1);

        assertEquals(50, caption.getLowerLeftY(), 1e-3);
        assertEquals(40, caption.getHeight(), 1e-3, "só a faixa da legenda (PDF: origem embaixo)");
        assertEquals(320, caption.getWidth(), 1e-3);
        assertEquals(card, plain, "card sem imagem: o card todo");
    }

    /** Imagem em branco no tamanho pedido: o que importa aqui é a estrutura do PDF. */
    private final List<String> rendered = new ArrayList<>();
    private final PdfExporter exporter = new PdfExporter((board, bounds, ppu) -> {
        rendered.add(board.getName());
        return new BufferedImage((int) Math.ceil(bounds.width() * ppu), (int) Math.ceil(bounds.height() * ppu),
                BufferedImage.TYPE_INT_RGB);
    });

    /**
     * Principal: "Projeto" (board aninhado) e "Tarefas" (painel em lista).
     * Projeto: "Etapa" (board aninhado, vazio) e "Notas" (painel de texto).
     */
    private Board sample() {
        Board root = new Board("Meu board");
        Card project = new Card(0, 0);
        project.setText("Projeto\nsegunda linha");
        Board projectBoard = new Board("");
        project.setChildBoard(projectBoard);
        Card tasks = new Card(300, 0);
        tasks.setText("Tarefas");
        tasks.setPanelMode(PanelMode.LIST);
        tasks.setListItemTexts(List.of("Comprar café", "", "Revisar ideias"));
        root.addCard(project);
        root.addCard(tasks);
        root.connect(project, tasks);

        Card stage = new Card(0, 0);
        stage.setText("Etapa");
        stage.setChildBoard(new Board(""));
        Card notes = new Card(0, 200);
        notes.setText("Notas");
        notes.setPanelMode(PanelMode.TEXT);
        notes.setDetailText("Detalhe importante com acentuação.");
        projectBoard.addCard(stage);
        projectBoard.addCard(notes);
        return root;
    }

    private static int pageIndex(PDDocument document, PDAnnotationLink link) throws IOException {
        PDPage target = ((PDPageDestination) link.getDestination()).getPage();
        return document.getPages().indexOf(target);
    }

    private static List<PDAnnotationLink> links(PDPage page) throws IOException {
        List<PDAnnotationLink> links = new ArrayList<>();
        for (PDAnnotation annotation : page.getAnnotations()) {
            if (annotation instanceof PDAnnotationLink link) {
                links.add(link);
            }
        }
        return links;
    }

    @Test
    void pagesFollowTheBoardTreeWithDetailsAfterTheirBoard() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            // 0 principal, 1 detalhes de Tarefas, 2 Projeto, 3 detalhes de Notas, 4 Etapa
            assertEquals(5, document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String tasksDetails = stripper.getText(document);
            assertTrue(tasksDetails.contains("Comprar café"), tasksDetails);
            assertTrue(tasksDetails.contains("Revisar ideias"), tasksDetails);
            stripper.setStartPage(4);
            stripper.setEndPage(4);
            assertTrue(stripper.getText(document).contains("Detalhe importante com acentuação."));
        }
    }

    @Test
    void cardsLinkToChildBoardAndDetailsPages() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            List<Integer> targets = new ArrayList<>();
            for (PDAnnotationLink link : links(document.getPage(0))) {
                targets.add(pageIndex(document, link));
            }
            assertEquals(List.of(2, 1), targets, "card Projeto → board filho; Tarefas → detalhes");

            // Página do board Projeto: caminho (Principal) + Etapa + Notas.
            List<Integer> projectTargets = new ArrayList<>();
            for (PDAnnotationLink link : links(document.getPage(2))) {
                projectTargets.add(pageIndex(document, link));
            }
            assertEquals(List.of(0, 4, 3), projectTargets);
        }
    }

    @Test
    void detailsPageLinksBackToItsBoard() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            List<Integer> targets = new ArrayList<>();
            for (PDAnnotationLink link : links(document.getPage(3))) {
                targets.add(pageIndex(document, link));
            }
            // caminho: Meu board, Projeto; depois "‹ Voltar ao board Projeto"
            assertEquals(List.of(0, 2, 2), targets);
        }
    }

    @Test
    void outlineMirrorsTheTree() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            PDOutlineItem root = document.getDocumentCatalog().getDocumentOutline().getFirstChild();
            assertEquals("Meu board", root.getTitle());
            List<String> children = new ArrayList<>();
            root.children().forEach(item -> children.add(item.getTitle()));
            assertEquals(List.of("Detalhes: Tarefas", "Projeto"), children);
            PDOutlineItem project = root.getLastChild();
            List<String> grandchildren = new ArrayList<>();
            project.children().forEach(item -> grandchildren.add(item.getTitle()));
            assertEquals(List.of("Detalhes: Notas", "Etapa"), grandchildren);
        }
    }

    @Test
    void cardTextIsSearchableAndEmptyBoardsAreNotRendered() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);
            assertTrue(text.contains("Projeto"), text);
            assertTrue(text.contains("segunda linha"), text);
            assertTrue(text.contains("Tarefas"), text);
        }
        // Principal e Projeto têm cards; Etapa está vazio e não precisa de imagem.
        assertEquals(List.of("Meu board", ""), rendered);
    }

    @Test
    void longPanelTextContinuesOnNextPages() throws IOException {
        Board root = new Board("Longo");
        Card panel = new Card(0, 0);
        panel.setPanelMode(PanelMode.TEXT);
        panel.setDetailText(String.join("\n", java.util.Collections.nCopies(120, "Uma linha de detalhe.")));
        root.addCard(panel);

        try (PDDocument document = exporter.build(root)) {
            assertEquals(1 + 3, document.getNumberOfPages(), "120 linhas ocupam 3 páginas A4");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(3);
            stripper.setEndPage(3);
            assertTrue(stripper.getText(document).contains("(continuação)"));
        }
    }

    @Test
    void unsupportedCharactersDoNotBreakTheExport() throws IOException {
        Board root = new Board("Emoji 🚀");
        Card card = new Card(0, 0);
        card.setText("Ideia 💡 com ção");
        root.addCard(card);

        try (PDDocument document = exporter.build(root)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Ideia ? com ção"), text);
        }
    }

    @Test
    void exportWritesTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("board.pdf");
        exporter.export(sample(), file);

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            assertEquals(5, document.getNumberOfPages());
        }
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "sem arquivo temporário sobrando");
        }
    }

    // ------------------------------------------------------------ link externo

    private static List<PDAnnotationLink> uriLinks(PDPage page) throws IOException {
        List<PDAnnotationLink> result = new ArrayList<>();
        for (PDAnnotationLink link : links(page)) {
            if (link.getAction() instanceof PDActionURI) {
                result.add(link);
            }
        }
        return result;
    }

    private static String uriOf(PDAnnotationLink link) {
        return ((PDActionURI) link.getAction()).getURI();
    }

    @Test
    void cardWithLinkOpensTheAddressFromTheWholeCard(@TempDir Path dir) throws IOException {
        Board root = new Board("Links");
        Card card = new Card(0, 0);
        card.setText("Site");
        card.setLink("https://açaí.com.br/página");
        root.addCard(card);
        Path file = dir.resolve("links.pdf");

        exporter.export(root, file);

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            List<PDAnnotationLink> uris = uriLinks(document.getPage(0));
            assertEquals(1, uris.size());
            assertEquals("https://xn--aa-4iaz.com.br/p%C3%A1gina", uriOf(uris.get(0)), "endereço só em ASCII");
            PDRectangle rect = uris.get(0).getRectangle();
            assertEquals(card.getWidth(), rect.getWidth(), 1e-3, "o card todo é clicável");
            assertEquals(card.getHeight(), rect.getHeight(), 1e-3);
        }
    }

    @Test
    void cardThatAlreadyLeadsToAPageOpensTheLinkOnlyFromTheClip() throws IOException {
        Board root = new Board("Misto");
        Card holder = new Card(0, 0);
        holder.setText("Projeto");
        holder.setChildBoard(new Board(""));
        holder.setLink("exemplo.com");
        root.addCard(holder);

        try (PDDocument document = exporter.build(root)) {
            PDPage page = document.getPage(0);
            List<PDAnnotationLink> all = links(page);
            assertEquals(2, all.size());
            PDAnnotationLink toChild = all.get(0);
            PDAnnotationLink toSite = all.get(1); // depois = por cima do link do card
            assertTrue(toSite.getAction() instanceof PDActionURI, "o clipe fica por cima");
            assertEquals("https://exemplo.com", uriOf(toSite));
            assertEquals(1, pageIndex(document, toChild), "o card continua levando ao board filho");

            PDRectangle clip = toSite.getRectangle();
            PDRectangle cardRect = toChild.getRectangle();
            assertEquals(CardMarkers.SIZE, clip.getWidth(), 1e-3, "só a área do clipe");
            assertTrue(clip.getLowerLeftX() >= cardRect.getLowerLeftX());
            assertTrue(clip.getUpperRightX() <= cardRect.getUpperRightX());
            assertTrue(clip.getUpperRightY() <= cardRect.getUpperRightY());
            assertTrue(clip.getUpperRightY() > cardRect.getUpperRightY() - 30, "na borda de cima");
        }
    }

    @Test
    void markerRectFollowsTheScreenLayout() {
        Card card = new Card(0, 0);
        card.setLink("exemplo.com");
        PDRectangle cardRect = new PDRectangle(100, 50, 200, 120);
        CardMarkers.Spot spot = CardMarkers.layout(card).get(0);

        PDRectangle half = PdfExporter.markerRect(card, new PDRectangle(100, 50, 100, 60), 0.5, CardMarkers.Kind.LINK);
        PDRectangle full = PdfExporter.markerRect(card, cardRect, 1, CardMarkers.Kind.LINK);

        assertEquals(100 + spot.x(), full.getLowerLeftX(), 1e-3);
        assertEquals(170 - spot.y(), full.getUpperRightY(), 1e-3, "PDF: origem embaixo");
        assertEquals(spot.size() / 2, half.getWidth(), 1e-3, "acompanha a escala da página");
        assertEquals(cardRect, PdfExporter.markerRect(new Card(0, 0), cardRect, 1, CardMarkers.Kind.LINK),
                "sem o marcador: o card todo");
    }

    @Test
    void cardsWithoutLinkHaveNoUriAnnotation() throws IOException {
        try (PDDocument document = exporter.build(sample())) {
            for (PDPage page : document.getPages()) {
                assertEquals(List.of(), uriLinks(page));
            }
        }
    }

    // ------------------------------------------------------ link para outro board

    /**
     * Na página do "Etapa" (dentro de Projeto), um card com atalho para o board
     * de "Projeto": o card todo leva à página do destino, em outro ramo da árvore.
     */
    @Test
    void cardWithBoardLinkLeadsToTheDestinationPage() throws IOException {
        Board root = sample();
        Card project = root.getCards().get(0);
        Board stageBoard = project.getChildBoard().getCards().get(0).getChildBoard();
        Card shortcut = new Card(0, 0);
        shortcut.setText("Atalho");
        shortcut.setBoardLink(project.getChildBoard().getId());
        stageBoard.addCard(shortcut);

        try (PDDocument document = exporter.build(root)) {
            // 0 principal, 1 detalhes de Tarefas, 2 Projeto, 3 detalhes de Notas, 4 Etapa
            // Antes vêm os links do caminho no topo (Principal, Projeto); o do card é o último.
            List<PDAnnotationLink> all = links(document.getPage(4));
            PDAnnotationLink toProject = all.get(all.size() - 1);
            assertEquals(2, pageIndex(document, toProject), "o atalho leva à página de Projeto");
            assertEquals(shortcut.getWidth(), toProject.getRectangle().getWidth(), 1e-3, "o card todo");
        }
    }

    @Test
    void cardThatAlreadyLeadsToAPageGoesToTheBoardLinkOnlyFromTheMarker() throws IOException {
        Board root = new Board("Misto");
        Card holder = new Card(0, 0);
        holder.setText("Projeto");
        holder.setChildBoard(new Board(""));
        Card other = new Card(400, 0);
        other.setText("Outro");
        other.setChildBoard(new Board(""));
        holder.setBoardLink(other.getChildBoard().getId());
        root.addCard(holder);
        root.addCard(other);

        try (PDDocument document = exporter.build(root)) {
            // 0 principal, 1 Projeto, 2 Outro
            List<PDAnnotationLink> holderLinks = new ArrayList<>();
            for (PDAnnotationLink link : links(document.getPage(0))) {
                if (link.getRectangle().getLowerLeftX() < 300) {
                    holderLinks.add(link);
                }
            }
            assertEquals(2, holderLinks.size());
            PDAnnotationLink toChild = holderLinks.get(0);
            PDAnnotationLink toOther = holderLinks.get(1); // depois = por cima
            assertEquals(1, pageIndex(document, toChild), "o card continua levando ao board filho");
            assertEquals(2, pageIndex(document, toOther), "o marcador leva ao destino do atalho");
            assertEquals(CardMarkers.SIZE, toOther.getRectangle().getWidth(), 1e-3, "só a área do marcador");
        }
    }

    @Test
    void boardLinkAndWebLinkShareACardWithoutOwnPage() throws IOException {
        Board root = new Board("Dois links");
        Card target = new Card(400, 0);
        target.setChildBoard(new Board(""));
        Card both = new Card(0, 0);
        both.setLink("exemplo.com");
        both.setBoardLink(target.getChildBoard().getId());
        root.addCard(both);
        root.addCard(target);

        try (PDDocument document = exporter.build(root)) {
            List<PDAnnotationLink> mine = new ArrayList<>();
            for (PDAnnotationLink link : links(document.getPage(0))) {
                if (link.getRectangle().getLowerLeftX() < 300) {
                    mine.add(link);
                }
            }
            assertEquals(2, mine.size());
            assertEquals(1, pageIndex(document, mine.get(0)), "o card todo vai ao board do atalho");
            assertEquals(both.getWidth(), mine.get(0).getRectangle().getWidth(), 1e-3);
            assertTrue(mine.get(1).getAction() instanceof PDActionURI, "o clipe, por cima, abre o site");
            assertEquals(CardMarkers.SIZE, mine.get(1).getRectangle().getWidth(), 1e-3);
            PDRectangle clip = PdfExporter.markerRect(both, mine.get(0).getRectangle(), 1, CardMarkers.Kind.LINK);
            assertEquals(clip.getLowerLeftX(), mine.get(1).getRectangle().getLowerLeftX(), 1e-3);
        }
    }

    @Test
    void boardLinkToADeletedBoardHasNoClickableArea() throws IOException {
        Board root = new Board("Quebrado");
        Card card = new Card(0, 0);
        card.setBoardLink("board-que-nao-existe");
        root.addCard(card);

        try (PDDocument document = exporter.build(root)) {
            assertEquals(List.of(), links(document.getPage(0)));
        }
    }

    @Test
    void markerRectsOfACardWithBothMarkersAreSideBySide() {
        Card card = new Card(0, 0);
        card.setLink("exemplo.com");
        card.setBoardLink("algum-board");
        PDRectangle cardRect = new PDRectangle(0, 0, 200, 120);

        PDRectangle clip = PdfExporter.markerRect(card, cardRect, 1, CardMarkers.Kind.LINK);
        PDRectangle arrow = PdfExporter.markerRect(card, cardRect, 1, CardMarkers.Kind.BOARD_LINK);

        assertEquals(clip.getUpperRightY(), arrow.getUpperRightY(), 1e-3, "mesma fileira");
        assertEquals(CardMarkers.SIZE + CardMarkers.SPACING, arrow.getLowerLeftX() - clip.getLowerLeftX(), 1e-3,
                "clipe à esquerda, seta à direita");
    }
}
