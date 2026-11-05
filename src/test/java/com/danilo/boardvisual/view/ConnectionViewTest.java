package com.danilo.boardvisual.view;

import com.danilo.boardvisual.export.PngExporter;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionGeometry;
import com.danilo.boardvisual.model.ConnectionStyle;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seta desenhada de verdade (JavaFX, CSS do app): pontas e tracejado de cada
 * estilo, o texto no meio acompanhando os cards, a seleção de seta no
 * BoardView, e o texto/tracejado no PNG exportado. Sem tela, é pulado.
 */
class ConnectionViewTest {

    private static final String CSS = ConnectionViewTest.class
            .getResource("/com/danilo/boardvisual/styles/app.css").toExternalForm();
    private static final double EPS = 1e-6;

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

    /** Dois cards 200x100 lado a lado: a seta vai de x=200 a x=400, na altura y=50. */
    private static Connection horizontal() {
        Card a = new Card(0, 0);
        a.setWidth(200);
        a.setHeight(100);
        Card b = new Card(400, 0);
        b.setWidth(200);
        b.setHeight(100);
        return new Connection(a, b);
    }

    private static ConnectionView laidOut(Connection connection) {
        ConnectionView view = new ConnectionView(connection);
        Group root = new Group(view, view.getLabelLayer());
        Scene scene = new Scene(root);
        scene.getStylesheets().add(CSS);
        layout(view);
        return view;
    }

    private static void layout(Node node) {
        Group root = (Group) node.getScene().getRoot();
        root.applyCss();
        root.layout();
    }

    private static Line visibleLine(ConnectionView view) {
        return (Line) view.lookup(".connection-line");
    }

    /** As duas pontas: a do destino (em x=400) e a da origem (em x=200). */
    private static Polygon arrowAt(ConnectionView view, double x) {
        return view.lookupAll(".connection-arrow").stream()
                .map(Polygon.class::cast)
                .filter(p -> Math.abs(p.getTranslateX() - x) < EPS)
                .findFirst().orElseThrow();
    }

    @Test
    void eachStyleDrawsItsTipsAndTheLineStopsAtTheirBase() throws Exception {
        onFx(() -> {
            Connection connection = horizontal();
            ConnectionView view = laidOut(connection);
            PseudoClass dashed = PseudoClass.getPseudoClass("dashed");

            for (ConnectionStyle style : ConnectionStyle.values()) {
                connection.setStyle(style);
                layout(view);
                Line line = visibleLine(view);
                assertEquals(style.hasEndArrow(), arrowAt(view, 400).isVisible(), style + ": ponta no destino");
                assertEquals(style.hasStartArrow(), arrowAt(view, 200).isVisible(), style + ": ponta na origem");
                assertEquals(style.hasStartArrow() ? 212 : 200, line.getStartX(), EPS, style + ": começo da linha");
                assertEquals(style.hasEndArrow() ? 388 : 400, line.getEndX(), EPS, style + ": fim da linha");
                assertEquals(style.isDashed(), view.getPseudoClassStates().contains(dashed), style + ": tracejado");
                assertEquals(style.isDashed(), !line.getStrokeDashArray().isEmpty(), style + ": tracejado no CSS");
            }
            // A ponta da origem é a mesma ponta girada 180°: aponta para a origem.
            connection.setStyle(ConnectionStyle.DOUBLE);
            layout(view);
            Polygon start = arrowAt(view, 200);
            assertTrue(start.localToParent(-12, 0).getX() > 200, "a base da ponta da origem fica para dentro da seta");
            return null;
        });
    }

    @Test
    void labelSitsInTheMiddleAndFollowsTheCards() throws Exception {
        onFx(() -> {
            Connection connection = horizontal();
            ConnectionView view = laidOut(connection);
            assertFalse(view.getLabel().isVisible(), "sem texto, nada aparece");

            connection.setLabel("leva a");
            layout(view);
            assertTrue(view.getLabel().isVisible());
            assertEquals(300, centerX(view), 0.5);
            assertEquals(50, centerY(view), 0.5);

            // Mover e redimensionar o card de destino: o texto acompanha por binding.
            connection.getTarget().setX(600);
            connection.getTarget().setY(200);
            layout(view);
            connection.getTarget().setWidth(300);
            layout(view);
            var a = ConnectionGeometry.start(connection);
            var b = ConnectionGeometry.tip(connection);
            assertEquals((a.x() + b.x()) / 2, centerX(view), 0.5);
            assertEquals((a.y() + b.y()) / 2, centerY(view), 0.5);
            return null;
        });
    }

    @Test
    void longLabelEndsWithEllipsisAndNeverResizesTheCards() throws Exception {
        onFx(() -> {
            Connection connection = horizontal();
            ConnectionView view = laidOut(connection);
            connection.setLabel("um texto bem comprido para a seta, que não cabe na largura máxima dela");
            layout(view);

            assertTrue(view.getLabel().getWidth() <= ConnectionView.LABEL_MAX_WIDTH + EPS,
                    "largura: " + view.getLabel().getWidth());
            assertEquals(200, connection.getSource().getWidth());
            assertEquals(100, connection.getSource().getHeight());
            assertEquals(200, connection.getTarget().getWidth());
            return null;
        });
    }

    @Test
    void oppositeArrowsKeepTheirLabelsApart() throws Exception {
        onFx(() -> {
            Board board = new Board("ida e volta");
            Connection ab = horizontal();
            board.addCard(ab.getSource());
            board.addCard(ab.getTarget());
            board.addConnection(ab);
            Connection ba = board.connect(ab.getTarget(), ab.getSource()).orElseThrow();
            ab.setLabel("pergunta");
            ba.setLabel("resposta");

            BoardView boardView = new BoardView();
            boardView.setBoard(board);
            Scene scene = new Scene(boardView, 800, 400);
            scene.getStylesheets().add(CSS);
            boardView.applyCss();
            boardView.layout();

            ConnectionView abView = boardView.getConnectionView(ab).orElseThrow();
            ConnectionView baView = boardView.getConnectionView(ba).orElseThrow();
            assertTrue(abView.isReverse() && baView.isReverse());
            var p = abView.getLabel().getBoundsInParent();
            var q = baView.getLabel().getBoundsInParent();
            assertTrue(q.getMaxX() <= p.getMinX() + EPS, "lado a lado: " + q + " / " + p);
            // As áreas de clique vão cada uma para um lado da linha (senão só a de cima seria clicável).
            assertEquals(-ConnectionView.HIT_SIDE_OFFSET, abView.getHitLine().getTranslateY(), EPS, "A→B: acima");
            assertEquals(ConnectionView.HIT_SIDE_OFFSET, baView.getHitLine().getTranslateY(), EPS, "B→A: abaixo");

            board.removeConnection(ba);
            assertFalse(abView.isReverse(), "sem a contrária, o texto volta ao meio");
            assertEquals(0, abView.getHitLine().getTranslateY(), EPS, "e a área de clique volta para a linha");
            return null;
        });
    }

    @Test
    void connectionSelectionIsExclusiveWithCardSelection() throws Exception {
        onFx(() -> {
            Board board = new Board("seleção");
            Card a = new Card(0, 0);
            Card b = new Card(400, 0);
            board.addCard(a);
            board.addCard(b);
            Connection connection = board.connect(a, b).orElseThrow();
            BoardView view = new BoardView();
            view.setBoard(board);
            ConnectionView connectionView = view.getConnectionView(connection).orElseThrow();
            PseudoClass selected = PseudoClass.getPseudoClass("selected");

            view.select(a);
            view.selectConnection(connection);
            assertEquals(connection, view.getSelectedConnection());
            assertTrue(view.getSelection().isEmpty(), "selecionar a seta tira a seleção dos cards");
            assertTrue(connectionView.getPseudoClassStates().contains(selected));

            view.select(b);
            assertNull(view.getSelectedConnection(), "selecionar um card tira a seleção da seta");
            assertFalse(connectionView.getPseudoClassStates().contains(selected));

            view.selectConnection(connection);
            view.clearSelection();
            assertNull(view.getSelectedConnection(), "Esc / clicar no fundo limpa a seta também");

            view.selectConnection(connection);
            board.removeConnection(connection);
            assertNull(view.getSelectedConnection(), "a seta excluída (ou desfeita) sai da seleção");

            view.selectConnection(new Connection(a, b));
            assertNull(view.getSelectedConnection(), "seta fora do board na tela é ignorada");
            return null;
        });
    }

    /** O texto da seta e o tracejado aparecem no PNG (o PDF usa a mesma imagem). */
    @Test
    void labelAndDashesAppearInTheExport(@TempDir Path folder) throws Exception {
        Board plain = exportBoard(ConnectionStyle.ARROW, "");
        Board labelled = exportBoard(ConnectionStyle.ARROW, "WWWWWW");
        Board dashed = exportBoard(ConnectionStyle.DASHED, "");
        Path plainFile = folder.resolve("simples.png");
        Path labelledFile = folder.resolve("texto.png");
        Path dashedFile = folder.resolve("tracejada.png");

        onFx(() -> {
            BoardRenderer renderer = new BoardRenderer(List.of(CSS));
            PngExporter exporter = new PngExporter((b, bounds, scale, bg) -> renderer.render(b, bounds, scale, false));
            exporter.export(plain, plainFile, PngExporter.Background.WHITE);
            exporter.export(labelled, labelledFile, PngExporter.Background.WHITE);
            exporter.export(dashed, dashedFile, PngExporter.Background.WHITE);
            return null;
        });

        PngExporter.Plan plan = PngExporter.plan(plain);
        BufferedImage plainImage = ImageIO.read(plainFile.toFile());
        BufferedImage labelledImage = ImageIO.read(labelledFile.toFile());
        BufferedImage dashedImage = ImageIO.read(dashedFile.toFile());

        // Logo acima da linha (y=50), no meio da seta: só o texto pinta ali.
        assertEquals(0, darkPixels(plainImage, plan, 280, 41, 320, 46), "sem texto: fundo branco acima da linha");
        assertTrue(darkPixels(labelledImage, plan, 280, 41, 320, 46) > 5, "com texto: letras acima da linha");

        // Ao longo da linha (y=50): a cheia não tem buracos, a tracejada tem.
        assertEquals(0, whitePixels(plainImage, plan, 220, 50, 370), "linha cheia");
        assertTrue(whitePixels(dashedImage, plan, 220, 50, 370) > 10, "linha tracejada");
    }

    private static Board exportBoard(ConnectionStyle style, String label) {
        Board board = new Board("export");
        Connection connection = horizontal();
        connection.setStyle(style);
        connection.setLabel(label);
        board.addCard(connection.getSource());
        board.addCard(connection.getTarget());
        board.addConnection(connection);
        return board;
    }

    private static int darkPixels(BufferedImage image, PngExporter.Plan plan, double x1, double y1, double x2, double y2) {
        int count = 0;
        for (double y = y1; y <= y2; y += 1 / plan.pixelsPerUnit()) {
            for (double x = x1; x <= x2; x += 1 / plan.pixelsPerUnit()) {
                int c = rgb(image, plan, x, y);
                if (((c >> 16) & 0xFF) < 128 && ((c >> 8) & 0xFF) < 128 && (c & 0xFF) < 128) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int whitePixels(BufferedImage image, PngExporter.Plan plan, double x1, double y, double x2) {
        int count = 0;
        for (double x = x1; x <= x2; x += 1 / plan.pixelsPerUnit()) {
            if (rgb(image, plan, x, y) == 0xFFFFFF) {
                count++;
            }
        }
        return count;
    }

    private static int rgb(BufferedImage image, PngExporter.Plan plan, double x, double y) {
        int px = (int) ((x - plan.bounds().x()) * plan.pixelsPerUnit());
        int py = (int) ((y - plan.bounds().y()) * plan.pixelsPerUnit());
        return image.getRGB(px, py) & 0xFFFFFF;
    }

    private static double centerX(ConnectionView view) {
        return view.getLabel().getBoundsInParent().getCenterX();
    }

    private static double centerY(ConnectionView view) {
        return view.getLabel().getBoundsInParent().getCenterY();
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
