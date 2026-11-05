package com.danilo.boardvisual.view;

import com.danilo.boardvisual.export.PngExporter;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Frames;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seção (frame) desenhada de verdade (JavaFX, CSS do app): onde ela pega o
 * clique (só a borda e o título; o meio vazio não) e como sai no PNG (atrás
 * dos cards, translúcida). Sem tela, é pulado em vez de falhar.
 */
class FrameViewTest {

    private static final String CSS = FrameViewTest.class
            .getResource("/com/danilo/boardvisual/styles/app.css").toExternalForm();

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

    private static Card frame(String title) {
        Card frame = new Card(0, 0);
        frame.setShape(CardShape.FRAME);
        frame.resize(400, 300);
        frame.setText(title);
        return frame;
    }

    /** Monta o CardView numa cena (fora da tela) com o CSS do app. */
    private static CardView laidOut(Card card) {
        CardView view = new CardView(card);
        Group root = new Group(view);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(CSS);
        root.applyCss();
        root.layout();
        return view;
    }

    @Test
    void onlyTheBorderAndTheTitleOfAFrameTakeTheClick() throws Exception {
        onFx(() -> {
            CardView view = laidOut(frame("Ideias"));

            assertTrue(view.getBody().getBackground() == null || view.getBody().getBackground().getFills().isEmpty(),
                    "o corpo da seção não tem fundo (senão o meio pegaria o clique)");
            assertFalse(view.hitsScenePoint(200, 150), "meio vazio: não é da seção");
            assertFalse(view.hitsScenePoint(20, 150), "perto da borda, por dentro, longe do anel");
            assertFalse(view.hitsScenePoint(200, Frames.TITLE_HEIGHT + 10), "logo abaixo do título");
            assertTrue(view.hitsScenePoint(1, 150), "borda esquerda");
            assertTrue(view.hitsScenePoint(398, 150), "borda direita");
            assertTrue(view.hitsScenePoint(200, 299), "borda de baixo");
            assertTrue(view.hitsScenePoint(-3, 150), "um pouco para fora da borda (anel de 10 px)");
            assertTrue(view.hitsScenePoint(200, 15), "faixa do título");
            assertTrue(view.getFrameGrip().contains(view.getFrameGrip().sceneToLocal(2, 150)));
            assertTrue(view.lookup(".card-text").isMouseTransparent(), "fora da edição o campo não pega clique");
            assertEquals("Ideias", view.getFrameTitle().getText());
            return null;
        });
    }

    @Test
    void commonCardsStillTakeTheClickInTheMiddle() throws Exception {
        onFx(() -> {
            Card card = new Card(0, 0);
            CardView view = laidOut(card);

            assertTrue(view.hitsScenePoint(100, 60));
            assertFalse(view.getFrameTitle().isVisible());
            assertFalse(view.lookup(".card-text").isMouseTransparent());
            return null;
        });
    }

    @Test
    void untitledFrameShowsAPlaceholderAndLongTitlesStayOnOneLine() throws Exception {
        onFx(() -> {
            Card card = frame("");
            CardView view = laidOut(card);
            assertEquals(CardView.FRAME_PLACEHOLDER, view.getFrameTitle().getText());

            card.setText("Primeira linha\nsegunda " + "muito comprida ".repeat(30));
            view.getParent().applyCss();
            view.getParent().layout();
            assertFalse(view.getFrameTitle().getText().contains("\n"), "o título é de uma linha");
            assertTrue(view.getFrameTitle().getHeight() <= Frames.TITLE_HEIGHT, "não cresce");
            assertEquals(400, card.getWidth());
            assertEquals(300, card.getHeight());
            return null;
        });
    }

    @Test
    void frameIsExportedBehindTheCardsAndTranslucent(@TempDir Path folder) throws Exception {
        Board board = new Board("Seção");
        Card section = frame("Tema");
        section.setColor("#FF0000");
        Card card = new Card(100, 100);
        card.setColor("#0000FF");
        board.addCard(card);
        board.addCard(section); // entra atrás do card
        Path file = folder.resolve("secao.png");

        onFx(() -> {
            BoardRenderer renderer = new BoardRenderer(List.of(CSS));
            new PngExporter((b, bounds, scale, bg) -> renderer.render(b, bounds, scale, false))
                    .export(board, file, PngExporter.Background.WHITE);
            return null;
        });

        PngExporter.Plan plan = PngExporter.plan(board);
        BufferedImage image = ImageIO.read(file.toFile());
        int cardColor = rgb(image, plan, 200, 160);
        int empty = rgb(image, plan, 350, 260);
        assertEquals(0x0000FF, cardColor, "o card fica por cima da seção");
        int r = (empty >> 16) & 0xFF;
        int g = (empty >> 8) & 0xFF;
        assertTrue(r > 230 && g > 90 && g < 200, String.format("fundo da seção translúcido (rosado): %06X", empty));
    }

    @Test
    void arrowsAreDrawnOverTheFrame(@TempDir Path folder) throws Exception {
        Board board = new Board("Seta");
        Card section = frame("Tema");
        section.setColor("#FF0000");
        Card top = new Card(100, 40);    // 100..300 × 40..160
        Card bottom = new Card(100, 200); // 100..300 × 200..320: a seta desce de 160 a 200, dentro da seção
        board.addCard(section);
        board.addCard(top);
        board.addCard(bottom);
        board.connect(top, bottom);
        Path file = folder.resolve("seta.png");

        onFx(() -> {
            BoardRenderer renderer = new BoardRenderer(List.of(CSS));
            new PngExporter((b, bounds, scale, bg) -> renderer.render(b, bounds, scale, false))
                    .export(board, file, PngExporter.Background.WHITE);
            return null;
        });

        PngExporter.Plan plan = PngExporter.plan(board);
        int arrow = rgb(ImageIO.read(file.toFile()), plan, 200, 166);
        int r = (arrow >> 16) & 0xFF;
        int g = (arrow >> 8) & 0xFF;
        assertTrue(r < 190 && Math.abs(r - g) < 25,
                String.format("a seta é cinza, sem o tom da seção por cima: %06X", arrow));
    }

    private static int rgb(BufferedImage image, PngExporter.Plan plan, double x, double y) {
        int px = (int) ((x - plan.bounds().x()) * plan.pixelsPerUnit());
        int py = (int) ((y - plan.bounds().y()) * plan.pixelsPerUnit());
        return image.getRGB(px, py) & 0xFFFFFF;
    }

    private static <T> T onFx(Callable<T> task) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(task.call());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done.get(30, TimeUnit.SECONDS);
    }
}
