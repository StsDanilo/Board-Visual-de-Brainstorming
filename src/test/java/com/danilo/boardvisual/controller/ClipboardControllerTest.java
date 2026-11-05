package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.persistence.BoardImages;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.MainWindow;
import com.danilo.boardvisual.view.SelectionToolbarView;
import com.danilo.boardvisual.view.ToolBarView;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copiar, recortar e colar pelo controller, num {@link BoardView} de verdade (sem
 * janela) e com a área de transferência do sistema substituída por uma falsa:
 * os testes nunca mexem na do usuário. Regras na seção 3.25 do DOCUMENTACAO.md.
 */
class ClipboardControllerTest {

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

    /** A área de transferência do sistema: quem copia texto tira a imagem que estava lá, como a de verdade. */
    private static final class FakeSystem implements ClipboardController.SystemSide {
        boolean image;
        String text;
        int imagePastes;
        Point2D imageCenter;
        boolean refuseText;
        boolean imageFails;

        @Override
        public boolean hasImage() {
            return image;
        }

        @Override
        public boolean putText(String newText) {
            if (refuseText) {
                return false;
            }
            text = newText;
            image = false;
            return true;
        }

        @Override
        public boolean pasteImage(Point2D center) {
            imagePastes++;
            imageCenter = center;
            return !imageFails;
        }
    }

    /** Onde o board atual guarda imagens: a pasta imagens/ do save e a pasta de trabalho. */
    private static final class FakeSpace implements ClipboardController.ImageSpace {
        Path saveImages;
        final Path work;

        FakeSpace(Path saveImages, Path work) {
            this.saveImages = saveImages;
            this.work = work;
        }

        @Override
        public List<Path> searchDirs() {
            return Files.isDirectory(work) ? List.of(work, saveImages) : List.of(saveImages);
        }

        @Override
        public Path newImagesDir() throws IOException {
            return Files.createDirectories(work);
        }
    }

    private Board board;
    private BoardView view;
    private EditHistory history;
    private FakeSystem system;
    private FakeSpace space;
    private ClipboardController controller;
    private final List<String> messages = new ArrayList<>();

    private void setUp(Path root) {
        board = new Board("principal");
        view = new BoardView();
        view.setBoard(board);
        view.resize(800, 600);
        new Scene(view, 800, 600);
        history = new EditHistory(view::getBoard);
        SelectionActionsController actions = new SelectionActionsController(
                view, new ToolBarView(), new SelectionToolbarView(), history);
        actions.setNotifier(messages::add);
        system = new FakeSystem();
        space = new FakeSpace(BoardImages.dirOf(root.resolve("save")), root.resolve("trabalho"));
        controller = new ClipboardController(view, history, actions, space, system, new CardContextMenuController(view));
        controller.setNotifier(messages::add);
    }

    private Card add(Board target, String id, double x, double y, String text) {
        Card card = new Card(id, x, y);
        card.setText(text);
        target.addCard(card);
        return card;
    }

    private static List<String> texts(Board target) {
        return target.getCards().stream().map(Card::getText).toList();
    }

    private static double centerX(Collection<Card> cards) {
        double left = cards.stream().mapToDouble(Card::getX).min().orElse(0);
        double right = cards.stream().mapToDouble(c -> c.getX() + c.getWidth()).max().orElse(0);
        return (left + right) / 2;
    }

    private static double centerY(Collection<Card> cards) {
        double top = cards.stream().mapToDouble(Card::getY).min().orElse(0);
        double bottom = cards.stream().mapToDouble(c -> c.getY() + c.getHeight()).max().orElse(0);
        return (top + bottom) / 2;
    }

    // ------------------------------------------------------------ mesmo board

    @Test
    void copyAndPasteInTheSameBoardAddsTheSelectedCopiesAsOneUndoStep(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            Card b = add(board, "b", 400, 0, "B");
            Connection ab = board.connect(a, b, ConnectionStyle.DASHED).orElseThrow();
            ab.setLabel("leva a");
            view.setSelection(List.of(a, b));

            assertTrue(controller.copy());
            assertTrue(controller.paste());

            assertEquals(List.of("A", "B", "A", "B"), texts(board));
            assertEquals(2, board.getConnections().size());
            Connection copied = board.getConnections().get(1);
            assertEquals("leva a", copied.getLabel());
            assertEquals(ConnectionStyle.DASHED, copied.getStyle());
            List<Card> pasted = board.getCards().subList(2, 4);
            assertEquals(Set.copyOf(pasted), Set.copyOf(view.getSelection()), "os colados ficam selecionados");
            assertEquals(view.visibleCenter().getX(), centerX(pasted), 1e-6, "no centro da área visível");
            assertEquals(view.visibleCenter().getY(), centerY(pasted), 1e-6);
            assertEquals("A\nB", system.text, "o texto dos cards vai para a área de transferência do sistema");

            history.undo();
            assertEquals(List.of("A", "B"), texts(board), "um único passo desfaz a colagem inteira");
            assertEquals(1, board.getConnections().size());
            assertFalse(history.canUndoProperty().get(), "e só havia esse passo (copiar não é passo)");
            history.redo();
            assertEquals(4, board.getCards().size());
            return null;
        });
    }

    @Test
    void pastingAgainDescendsAStepAndCopyingAgainStartsOver(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();

            controller.paste();
            controller.paste();
            controller.paste();

            Card first = board.getCards().get(1);
            Card second = board.getCards().get(2);
            Card third = board.getCards().get(3);
            assertEquals(24, second.getX() - first.getX(), 1e-6);
            assertEquals(24, second.getY() - first.getY(), 1e-6);
            assertEquals(48, third.getX() - first.getX(), 1e-6);

            view.select(a);
            controller.copy(); // copiou de novo: a escada recomeça
            controller.paste();
            assertEquals(first.getX(), board.getCards().get(4).getX(), 1e-6);
            return null;
        });
    }

    @Test
    void pasteWithNothingCopiedDoesNothingAndCreatesNoStep(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            add(board, "a", 0, 0, "A");

            assertFalse(controller.paste());

            assertEquals(1, board.getCards().size());
            assertFalse(history.canUndoProperty().get());
            return null;
        });
    }

    @Test
    void copyWithoutASelectionKeepsWhatWasCopiedBefore(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            assertTrue(controller.copy());

            view.clearSelection();
            assertFalse(controller.copy(), "nada selecionado: não copia");
            assertTrue(controller.paste(), "e o que estava copiado continua valendo");

            assertEquals(List.of("A", "A"), texts(board));
            return null;
        });
    }

    @Test
    void theCopyDoesNotDependOnTheSourceCardsAnymore(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "original");
            view.select(a);
            controller.copy();

            a.setText("mudou");
            history.perform(() -> board.removeCard(a));
            controller.paste();

            assertEquals(List.of("original"), texts(board));
            return null;
        });
    }

    // ------------------------------------------------------------ outro board

    @Test
    void pastesIntoAnotherBoardOfTheTreeAndTheClipboardSurvivesNavigation(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            Card holder = add(board, "h", 400, 0, "Projeto");
            Board child = new Board("filho", "");
            holder.setChildBoard(child);
            view.select(a);
            controller.copy();

            // "Entrar" no board filho: a tela passa a mostrar outro board.
            view.setBoard(child);
            history.boardChanged();
            assertTrue(controller.paste());

            assertEquals(List.of("A"), texts(child));
            assertEquals(List.of("A", "Projeto"), texts(board), "o board de origem não muda");
            assertEquals(Set.copyOf(child.getCards()), Set.copyOf(view.getSelection()));
            assertNotEquals("a", child.getCards().get(0).getId());
            history.undo();
            assertTrue(child.getCards().isEmpty(), "o desfazer age no board da tela");
            assertEquals(2, board.getCards().size());
            return null;
        });
    }

    @Test
    void pastesACardWithANestedBoardWithItsWholeContent(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card holder = add(board, "h", 0, 0, "Projeto");
            Board child = new Board("filho", "");
            add(child, "x", 0, 0, "dentro");
            holder.setChildBoard(child);
            view.select(holder);
            controller.copy();

            Board other = new Board("outro");
            view.setBoard(other);
            controller.paste();

            Card copy = other.getCards().get(0);
            assertTrue(copy.hasChildBoard());
            assertEquals(List.of("dentro"), texts(copy.getChildBoard()));
            assertNotEquals("filho", copy.getChildBoard().getId());
            assertEquals(1, child.getCards().size(), "o original continua igual");
            return null;
        });
    }

    // ------------------------------------------------------------ recortar

    @Test
    void cutCopiesAndRemovesTheSelectionExceptTheLockedOnes(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            Card b = add(board, "b", 400, 0, "B");
            b.setLocked(true);
            view.setSelection(List.of(a, b));

            assertTrue(controller.cut());

            assertEquals(List.of("B"), texts(board), "o travado fica");
            assertTrue(messages.stream().anyMatch(m -> m.contains("travado")), "e avisa: " + messages);
            controller.paste();
            assertEquals(List.of("B", "A", "B"), texts(board), "o que foi recortado vem inteiro, inclusive o travado");
            assertFalse(board.getCards().get(2).isLocked(), "a cópia do travado nasce destravada");
            return null;
        });
    }

    @Test
    void cutIsOneUndoStepAndThePasteIsAnother(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);

            controller.cut();
            controller.paste();
            assertEquals(List.of("A"), texts(board));

            history.undo();
            assertTrue(board.getCards().isEmpty(), "desfaz a colagem");
            history.undo();
            assertEquals(List.of("A"), texts(board), "desfaz o recorte");
            return null;
        });
    }

    @Test
    void cutWithoutASelectionDoesNothing(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            add(board, "a", 0, 0, "A");

            assertFalse(controller.cut());

            assertEquals(1, board.getCards().size());
            assertFalse(history.canUndoProperty().get());
            return null;
        });
    }

    // ------------------------------------------------------------ prioridade com a imagem do sistema

    @Test
    void anImageThatEnteredTheSystemAfterTheCopyWins(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();
            system.image = true; // o usuário copiou uma imagem depois

            assertTrue(controller.paste());

            assertEquals(1, system.imagePastes);
            assertEquals(1, board.getCards().size(), "nenhum card colado");
            return null;
        });
    }

    @Test
    void cardsCopiedAfterAnImageWin(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            system.image = true; // havia uma imagem no sistema
            view.select(a);
            controller.copy(); // copiar cards substitui o que estava no sistema

            assertFalse(system.image);
            assertTrue(controller.paste());

            assertEquals(0, system.imagePastes);
            assertEquals(List.of("A", "A"), texts(board));
            return null;
        });
    }

    @Test
    void onlyAnImageInTheSystemPastesTheImage(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            system.image = true;

            assertTrue(controller.paste());
            assertEquals(1, system.imagePastes);
            assertEquals(null, system.imageCenter, "sem ponto: no centro da área visível");
            return null;
        });
    }

    @Test
    void textInTheSystemDoesNotCancelTheCopiedCards(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();
            system.text = "copiei um texto em outro programa"; // sem imagem: nada para colar no board

            assertTrue(controller.paste());

            assertEquals(List.of("A", "A"), texts(board));
            assertEquals(0, system.imagePastes);
            return null;
        });
    }

    @Test
    void ifTheSystemRefusesTheTextAnImageThereWins(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            system.image = true;
            system.refuseText = true;
            view.select(a);
            controller.copy();

            controller.paste();

            assertEquals(1, system.imagePastes, "não deu para saber a ordem: vale a imagem do sistema");
            return null;
        });
    }

    @Test
    void ifTheImageCannotBePastedTheCopiedCardsAreUsed(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();
            system.image = true;
            system.imageFails = true;

            assertTrue(controller.paste());

            assertEquals(List.of("A", "A"), texts(board));
            return null;
        });
    }

    @Test
    void canPasteReflectsTheCopiedCardsAndTheSystemImage(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            assertFalse(controller.canPaste());
            system.image = true;
            assertTrue(controller.canPaste());
            system.image = false;
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();
            assertTrue(controller.canPaste());
            return null;
        });
    }

    // ------------------------------------------------------------ posição

    @Test
    void pasteAtAPointCentersTheCardsThere(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card a = add(board, "a", 0, 0, "A");
            view.select(a);
            controller.copy();

            controller.paste(new Point2D(1000, 2000));

            Card pasted = board.getCards().get(1);
            assertEquals(1000, pasted.getCenterX(), 1e-6);
            assertEquals(2000, pasted.getCenterY(), 1e-6);
            return null;
        });
    }

    // ------------------------------------------------------------ imagens entre pastas

    private void writeImage(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), new byte[] {1, 2, 3});
    }

    @Test
    void pastingInTheSameSaveReusesTheImageFileWithoutCopying(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            writeImage(space.saveImages, "foto.png");
            Card photo = Card.createImage("foto.png", 100, 100, 160, 90);
            board.addCard(photo);
            view.select(photo);

            controller.copy();
            controller.paste();

            assertEquals("foto.png", board.getCards().get(1).getImageFile());
            assertFalse(Files.exists(space.work), "mesmo save: o arquivo é o mesmo, nada é copiado");
            return null;
        });
    }

    @Test
    void pastingInAnotherSaveCopiesTheImageFileToItsWorkFolder(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            writeImage(space.saveImages, "foto.png");
            Card photo = Card.createImage("foto.png", 100, 100, 160, 90);
            board.addCard(photo);
            view.select(photo);
            controller.copy();

            // Outro save aberto: as imagens agora vêm de outra pasta, e o save de origem pode até sumir.
            Files.delete(space.saveImages.resolve("foto.png"));
            space.saveImages = BoardImages.dirOf(root.resolve("outro-save"));
            Board other = new Board("outro");
            view.setBoard(other);
            history.clear();
            controller.paste();

            assertEquals("foto.png", other.getCards().get(0).getImageFile());
            assertTrue(Files.isRegularFile(space.work.resolve("foto.png")), "o arquivo veio para a pasta de trabalho");
            assertTrue(messages.stream().noneMatch(m -> m.contains("Imagem não encontrada")), messages.toString());
            return null;
        });
    }

    @Test
    void aMissingImageStillPastesTheCardAndWarns(@TempDir Path root) throws Exception {
        onFx(() -> {
            setUp(root);
            Card photo = Card.createImage("sumiu.png", 100, 100, 160, 90); // o arquivo nunca existiu
            board.addCard(photo);
            view.select(photo);
            controller.copy();

            assertTrue(controller.paste());

            assertEquals(2, board.getCards().size());
            assertEquals("sumiu.png", board.getCards().get(1).getImageFile());
            assertTrue(messages.stream().anyMatch(m -> m.contains("Imagem não encontrada")), messages.toString());
            return null;
        });
    }

    // ------------------------------------------------------------ menu

    @Test
    void editMenuHasCutCopyPasteWithTheUsualShortcuts() throws Exception {
        onFx(() -> {
            MainWindow window = new MainWindow();
            assertEquals(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN), window.getCutItem().getAccelerator());
            assertEquals(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN), window.getCopyItem().getAccelerator());
            assertEquals(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN), window.getPasteItem().getAccelerator());
            assertEquals("Recortar", window.getCutItem().getText());
            assertEquals("Copiar", window.getCopyItem().getText());
            assertEquals("Colar", window.getPasteItem().getText());
            return null;
        });
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
