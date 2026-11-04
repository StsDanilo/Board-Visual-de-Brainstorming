package com.danilo.boardvisual.view;

import com.danilo.boardvisual.export.PngExporter;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De ponta a ponta: o BoardRenderer de verdade (JavaFX, mesmo CSS do app)
 * gerando um PNG em disco. Precisa de tela (no Windows sempre há); sem ela,
 * o teste é pulado em vez de falhar.
 */
class BoardRendererPngTest {

    private static final int GREEN = 0x00C000;

    @BeforeAll
    static void startJavaFx() {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            // outro teste já iniciou o JavaFX nesta JVM
        } catch (Exception | Error e) {
            Assumptions.abort("JavaFX indisponível neste ambiente: " + e);
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void exportsTheBoardWithShapesArrowAndImage(@TempDir Path folder) throws Exception {
        // Uma imagem verde de verdade, carregada como o ImageController carrega.
        BufferedImage green = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                green.setRGB(x, y, GREEN);
            }
        }
        Path imageFile = folder.resolve("verde.png");
        ImageIO.write(green, "png", imageFile.toFile());

        Board board = new Board("Teste");
        Card left = new Card(0, 0);
        left.setText("Ideia");
        left.setColor("#FFD8A8");
        Card right = new Card(400, 0);
        right.setShape(CardShape.ELLIPSE);
        right.setText("Outra");
        Card diamond = new Card(0, 250);
        diamond.setShape(CardShape.DIAMOND);
        Card photo = Card.createImage("verde.png", 520, 330, 160, 120);
        photo.setText("Legenda");
        photo.setHeight(160);
        board.addCard(left);
        board.addCard(right);
        board.addCard(diamond);
        board.addCard(photo);
        board.connect(left, right);

        Path white = folder.resolve("branco.png");
        Path transparent = folder.resolve("transparente.png");
        onFxThread(() -> {
            CardImageLoader loader = name -> new Image(folder.resolve(name).toUri().toString(), false);
            String css = BoardRendererPngTest.class
                    .getResource("/com/danilo/boardvisual/styles/app.css").toExternalForm();
            BoardRenderer renderer = new BoardRenderer(List.of(css), loader);
            PngExporter exporter = new PngExporter((b, bounds, scale, bg) ->
                    renderer.render(b, bounds, scale, bg == PngExporter.Background.TRANSPARENT));
            exporter.export(board, white, PngExporter.Background.WHITE);
            exporter.export(board, transparent, PngExporter.Background.TRANSPARENT);
            return null;
        });

        PngExporter.Plan plan = PngExporter.plan(board);
        BufferedImage image = ImageIO.read(white.toFile());
        assertEquals(plan.width(), image.getWidth());
        assertEquals(plan.height(), image.getHeight());
        assertEquals(0xFFFFFFFF, image.getRGB(1, 1), "canto: fundo branco");

        // Centro do card laranja: a cor do card, não o fundo.
        int[] leftCenter = pixel(plan, left.getX() + 100, left.getY() + 20);
        assertTrue(rgb(image, leftCenter) != 0xFFFFFF, "o card aparece");

        // Entre os dois cards de cima passa a seta.
        assertTrue(hasNonWhiteNear(image, pixel(plan, 300, 60)), "a seta aparece entre os cards");

        // A imagem do card de imagem (verde) aparece no topo dele.
        int[] photoTop = pixel(plan, photo.getX() + 80, photo.getY() + 40);
        int color = rgb(image, photoTop);
        assertTrue(isGreen(color), String.format("imagem do card no PNG: %06X", color));

        BufferedImage alpha = ImageIO.read(transparent.toFile());
        assertEquals(plan.width(), alpha.getWidth());
        assertEquals(0, alpha.getRGB(1, 1) >>> 24, "canto: transparente");
        assertEquals(0xFF, alpha.getRGB(leftCenter[0], leftCenter[1]) >>> 24, "card: opaco");
        assertTrue(isGreen(alpha.getRGB(photoTop[0], photoTop[1]) & 0xFFFFFF));
    }

    /** O snapshot do JavaFX aguenta o limite de 8.000 px (maior que a textura de muitas placas de vídeo). */
    @Test
    void hugeBoardIsReducedToTheLimitAndStillRendersBothEnds(@TempDir Path folder) throws Exception {
        Board board = new Board("Enorme");
        Card first = new Card(0, 0);
        first.setColor("#A5D8FF");
        Card last = new Card(29_736, 0); // conteúdo: 30.000 de largura
        last.setColor("#A5D8FF");
        board.addCard(first);
        board.addCard(last);
        Path file = folder.resolve("enorme.png");

        onFxThread(() -> {
            String css = BoardRendererPngTest.class
                    .getResource("/com/danilo/boardvisual/styles/app.css").toExternalForm();
            BoardRenderer renderer = new BoardRenderer(List.of(css));
            new PngExporter((b, bounds, scale, bg) -> renderer.render(b, bounds, scale, false))
                    .export(board, file, PngExporter.Background.WHITE);
            return null;
        });

        PngExporter.Plan plan = PngExporter.plan(board);
        BufferedImage image = ImageIO.read(file.toFile());
        assertEquals(8_000, image.getWidth());
        assertEquals(plan.height(), image.getHeight());
        assertTrue(rgb(image, pixel(plan, first.getX() + 100, first.getY() + 100)) != 0xFFFFFF, "card do início");
        assertTrue(rgb(image, pixel(plan, last.getX() + 100, last.getY() + 100)) != 0xFFFFFF, "card do fim");
    }

    /** Pixel da imagem que corresponde ao ponto (x, y) do board. */
    private static int[] pixel(PngExporter.Plan plan, double x, double y) {
        return new int[]{(int) ((x - plan.bounds().x()) * plan.pixelsPerUnit()),
                (int) ((y - plan.bounds().y()) * plan.pixelsPerUnit())};
    }

    private static int rgb(BufferedImage image, int[] p) {
        return image.getRGB(p[0], p[1]) & 0xFFFFFF;
    }

    private static boolean hasNonWhiteNear(BufferedImage image, int[] p) {
        for (int dy = -6; dy <= 6; dy++) {
            if ((image.getRGB(p[0], p[1] + dy) & 0xFFFFFF) != 0xFFFFFF) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGreen(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return g > 150 && r < 60 && b < 60;
    }

    private interface FxTask {
        Object run() throws Exception;
    }

    private static void onFxThread(FxTask task) throws Exception {
        CompletableFuture<Object> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(task.run());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(30, TimeUnit.SECONDS);
    }
}
