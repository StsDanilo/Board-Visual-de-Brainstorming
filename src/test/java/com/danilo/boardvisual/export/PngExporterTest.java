package com.danilo.boardvisual.export;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parte do PNG que não precisa de tela: tamanho, escala, limites, nome e gravação. */
class PngExporterTest {

    // ------------------------------------------------------------ tamanho e escala

    @Test
    void smallBoardUsesTwoPixelsPerUnitAroundTheCardsWithMargin() {
        Board board = new Board("b");
        Card card = new Card(100, 50);
        card.setWidth(200);
        card.setHeight(120);
        board.addCard(card);

        PngExporter.Plan plan = PngExporter.plan(board);

        assertEquals(new Box(68, 18, 264, 184), plan.bounds(), "cards + 32 de margem, como no PDF");
        assertEquals(2, plan.pixelsPerUnit(), 1e-9);
        assertEquals(528, plan.width());
        assertEquals(368, plan.height());
    }

    @Test
    void wideBoardIsReducedToTheMaximumSide() {
        Board board = new Board("b");
        board.addCard(new Card(0, 0));
        Card far = new Card(19_736, 0); // conteúdo: 20.000 de largura com a margem
        board.addCard(far);

        PngExporter.Plan plan = PngExporter.plan(board);

        assertEquals(20_000, plan.bounds().width(), 1e-9);
        assertEquals(PngExporter.MAX_SIDE, plan.width(), "o lado maior para no limite, sem passar 1 px");
        assertTrue(plan.pixelsPerUnit() < PngExporter.RESOLUTION);
    }

    @Test
    void largeSquareBoardIsReducedToTheMaximumPixelCount() {
        Board board = new Board("b");
        board.addCard(new Card(0, 0));
        Card far = new Card(5_736, 5_816); // conteúdo: 6.000 x 6.000
        board.addCard(far);

        PngExporter.Plan plan = PngExporter.plan(board);

        assertTrue(plan.width() <= PngExporter.MAX_SIDE && plan.height() <= PngExporter.MAX_SIDE);
        long pixels = (long) plan.width() * plan.height();
        assertTrue(pixels <= PngExporter.MAX_PIXELS + plan.width() + plan.height(),
                "no máximo o limite (mais o arredondamento de uma linha/coluna): " + pixels);
        assertTrue(pixels > PngExporter.MAX_PIXELS * 0.99, "reduz só o necessário");
    }

    @Test
    void pixelsRoundUpButIgnoreFloatingPointNoise() {
        assertEquals(8_000, PngExporter.pixels(20_000, 8_000 / 20_000.0));
        assertEquals(8_000, PngExporter.pixels(1, 8_000.0000000001));
        assertEquals(3, PngExporter.pixels(1.2, 2), "2,4 px vira 3: não corta a borda");
        assertEquals(1, PngExporter.pixels(0, 2), "nunca uma imagem de 0 px");
    }

    // ------------------------------------------------------------ nome sugerido

    @Test
    void suggestedNameIsTheBoardNameOrThePathToTheNestedBoard() {
        assertEquals("Meu board.png", PngExporter.suggestedFileName(List.of("Meu board")));
        assertEquals("Projeto - Etapa 1 - Detalhe.png",
                PngExporter.suggestedFileName(List.of("Projeto", "Etapa 1", "Detalhe")));
    }

    @Test
    void suggestedNameReplacesCharactersWindowsDoesNotAccept() {
        assertEquals("Prazo_ 10_11 _ok_.png", PngExporter.suggestedFileName(List.of("Prazo: 10/11 \"ok\"")));
        assertEquals("a_b_c_d.png", PngExporter.suggestedFileName(List.of("a?b*c|d")));
        assertEquals("Ideias.png", PngExporter.suggestedFileName(List.of("Ideias...  ")),
                "Windows não aceita ponto ou espaço no fim");
        assertEquals("con_.png", PngExporter.suggestedFileName(List.of("con")), "nome reservado do Windows");
    }

    @Test
    void suggestedNameNeverEmptyAndNeverHuge() {
        assertEquals("Sem título.png", PngExporter.suggestedFileName(List.of("   ")));
        assertEquals("Raiz - Sem título.png", PngExporter.suggestedFileName(List.of("Raiz", "")));
        String longName = PngExporter.suggestedFileName(List.of("x".repeat(500)));
        assertEquals(PngExporter.MAX_NAME_LENGTH + ".png".length(), longName.length());
    }

    @Test
    void pngExtensionIsAddedOnlyWhenMissing() {
        Path folder = Path.of("pasta");
        assertEquals(folder.resolve("a.png"), PngExporter.withPngExtension(folder.resolve("a")));
        assertEquals(folder.resolve("a.PNG"), PngExporter.withPngExtension(folder.resolve("a.PNG")));
        assertEquals(folder.resolve("a.b.png"), PngExporter.withPngExtension(folder.resolve("a.b")));
    }

    // ------------------------------------------------------------ gravação

    private final List<Object[]> calls = new ArrayList<>();
    private final PngExporter exporter = new PngExporter((board, bounds, ppu, background) -> {
        calls.add(new Object[]{board, bounds, ppu, background});
        int width = PngExporter.pixels(bounds.width(), ppu);
        int height = PngExporter.pixels(bounds.height(), ppu);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF3366CC);
        return image;
    });

    @Test
    void exportWritesAPngOfThePlannedSizeAndLeavesNoTemporaryFile(@TempDir Path folder) throws IOException {
        Board board = new Board("b");
        board.addCard(new Card(0, 0));
        Path file = folder.resolve("board.png");

        exporter.export(board, file, PngExporter.Background.TRANSPARENT);

        BufferedImage written = ImageIO.read(file.toFile());
        PngExporter.Plan plan = PngExporter.plan(board);
        assertEquals(plan.width(), written.getWidth());
        assertEquals(plan.height(), written.getHeight());
        assertEquals(0xFF3366CC, written.getRGB(0, 0));
        assertSame(board, calls.get(0)[0]);
        assertEquals(PngExporter.Background.TRANSPARENT, calls.get(0)[3]);
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of(file), files.toList(), "só o PNG: nada de .tmp sobrando");
        }
    }

    @Test
    void exportReplacesAnExistingFile(@TempDir Path folder) throws IOException {
        Board board = new Board("b");
        board.addCard(new Card(0, 0));
        Path file = folder.resolve("board.png");
        Files.writeString(file, "antigo");

        exporter.export(board, file, PngExporter.Background.WHITE);

        assertTrue(ImageIO.read(file.toFile()) != null, "virou um PNG válido");
    }

    @Test
    void emptyBoardIsRefusedWithoutTouchingTheFile(@TempDir Path folder) {
        Path file = folder.resolve("vazio.png");

        assertTrue(PngExporter.isEmpty(new Board("vazio")));
        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(new Board("vazio"), file, PngExporter.Background.WHITE));
        assertFalse(Files.exists(file));
        assertTrue(calls.isEmpty(), "nem chega a desenhar");
    }
}
