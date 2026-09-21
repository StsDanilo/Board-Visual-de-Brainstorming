package com.danilo.boardvisual.export;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.PanelMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
}
