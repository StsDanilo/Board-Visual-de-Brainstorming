package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.alignment.ViewReveal;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BreadcrumbView;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * Shift+1 (caber tudo) e Shift+2 (caber a seleção) num {@link BoardView} de verdade (sem
 * janela): o que muda é só a visão; o modelo e o desfazer ficam como estavam.
 */
class ViewFitControllerTest {

    private static final double VIEW_W = 1000;
    private static final double VIEW_H = 600;

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

    private static <T> T onFx(Callable<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void onFx(Runnable work) {
        onFx(() -> {
            work.run();
            return null;
        });
    }

    /** Tela com tamanho de verdade (sem janela): o ajuste só vale com a tela dimensionada. */
    private static BoardView sizedView(Board board) {
        BoardView view = new BoardView();
        view.resize(VIEW_W, VIEW_H);
        view.setBoard(board);
        return view;
    }

    private static Card add(Board board, double x, double y) {
        Card card = new Card(x, y);
        board.addCard(card);
        return card;
    }

    private static boolean fullyVisible(BoardView view, Card card) {
        BoardView.ViewState s = view.getViewState();
        return ViewReveal.isFullyVisible(new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight()),
                s.panX(), s.panY(), s.zoom(), VIEW_W, VIEW_H);
    }

    @Test
    void fitAllShowsEveryCardAndKeepsTheModelAndHistoryUntouched() {
        onFx(() -> {
            Board board = new Board("b");
            List<Card> cards = new ArrayList<>(List.of(
                    add(board, -1500, -600), add(board, 1800, 200), add(board, 100, 1200)));
            cards.add(Card.create(CardShape.FRAME, 400, 400));
            cards.add(Card.createImage("foto.png", 1200, -900, 320, 180));
            cards.forEach(c -> { if (!board.getCards().contains(c)) board.addCard(c); });
            BoardView view = sizedView(board);
            EditHistory history = new EditHistory(() -> board);
            var before = board.snapshot();
            ViewFitController controller = new ViewFitController(view);

            controller.fitAll();

            for (Card card : board.getCards()) {
                assertTrue(fullyVisible(view, card), "card inteiro na tela: " + card);
            }
            double zoom = view.zoomProperty().get();
            assertTrue(zoom >= 0.2 && zoom <= 3.0, "zoom dentro dos limites: " + zoom);
            assertTrue(zoom < 1, "o board é maior que a tela: reduziu");
            assertEquals(before, board.snapshot(), "o modelo não mudou");
            assertFalse(history.canUndoProperty().get(), "não entra no desfazer");
        });
    }

    @Test
    void fitAllOnAnEmptyBoardGoesBackTo100Percent() {
        onFx(() -> {
            BoardView view = sizedView(new Board("vazio"));
            view.setViewState(new BoardView.ViewState(-340, 120, 2.2));

            new ViewFitController(view).fitAll();

            assertEquals(BoardView.ViewState.DEFAULT, view.getViewState(), "como o Ctrl+0");
        });
    }

    @Test
    void fitAllOnASingleCardStopsAtMaxZoom() {
        onFx(() -> {
            Board board = new Board("um");
            Card card = add(board, 50, 50);
            BoardView view = sizedView(board);

            new ViewFitController(view).fitAll();

            assertEquals(3.0, view.zoomProperty().get(), 1e-9, "não amplia além de 300%");
            assertTrue(fullyVisible(view, card));
        });
    }

    @Test
    void fitAllOnAHugeBoardStopsAtMinZoomAndSaysSo() {
        onFx(() -> {
            Board board = new Board("enorme");
            add(board, -20000, -3000);
            add(board, 20000, 3000);
            BoardView view = sizedView(board);
            ViewFitController controller = new ViewFitController(view);
            List<String> messages = new ArrayList<>();
            controller.setNotifier(messages::add);

            controller.fitAll();

            assertEquals(0.2, view.zoomProperty().get(), 1e-9, "não reduz além de 20%");
            assertEquals(1, messages.size(), "avisa que o conteúdo não cabe nem no zoom mínimo");
            assertTrue(messages.get(0).contains("20%"));
            // O centro do conteúdo (x: -20000..20200, y: -3000..3120) cai no centro da área útil
            // (tela menos as margens 96/48 na horizontal e 64/48 na vertical).
            BoardView.ViewState s = view.getViewState();
            assertEquals(96 + (VIEW_W - 96 - 48) / 2, s.panX() + 100 * s.zoom(), 1e-6);
            assertEquals(64 + (VIEW_H - 64 - 48) / 2, s.panY() + 60 * s.zoom(), 1e-6);
        });
    }

    @Test
    void fitSelectionShowsOnlyTheSelectedCards() {
        onFx(() -> {
            Board board = new Board("b");
            Card near1 = add(board, 0, 0);
            Card near2 = add(board, 500, 100);
            Card far = add(board, 9000, 7000);
            BoardView view = sizedView(board);
            ViewFitController controller = new ViewFitController(view);
            controller.fitAll();
            double zoomAll = view.zoomProperty().get();

            view.setSelection(List.of(near1, near2));
            controller.fitSelection();

            assertTrue(view.zoomProperty().get() > zoomAll, "aproximou");
            assertTrue(fullyVisible(view, near1));
            assertTrue(fullyVisible(view, near2));
            assertFalse(fullyVisible(view, far), "o card fora da seleção ficou de fora");
            assertEquals(Set.of(near1, near2), Set.copyOf(view.getSelection()), "a seleção não muda");
        });
    }

    @Test
    void fitSelectionWithNothingSelectedOnlyWarns() {
        onFx(() -> {
            Board board = new Board("b");
            add(board, 0, 0);
            BoardView view = sizedView(board);
            view.setViewState(new BoardView.ViewState(40, 50, 1.5));
            ViewFitController controller = new ViewFitController(view);
            List<String> messages = new ArrayList<>();
            controller.setNotifier(messages::add);

            controller.fitSelection();

            assertEquals(new BoardView.ViewState(40, 50, 1.5), view.getViewState(), "a visão não mudou");
            assertEquals(1, messages.size());
        });
    }

    @Test
    void notSizedViewIsLeftAlone() {
        onFx(() -> {
            Board board = new Board("b");
            add(board, 0, 0);
            BoardView view = new BoardView(); // sem resize: largura e altura 0
            view.setBoard(board);
            view.setViewState(new BoardView.ViewState(7, 8, 1.2));

            new ViewFitController(view).fitAll();

            assertEquals(new BoardView.ViewState(7, 8, 1.2), view.getViewState());
        });
    }

    @Test
    void eachNestedLevelKeepsItsOwnFittedViewWhileGoingBack() {
        onFx(() -> {
            Board root = new Board("principal");
            add(root, -3000, -2000);
            Card door = add(root, 3000, 2000);
            Board child = new Board("filho");
            door.setChildBoard(child);
            add(child, 0, 0);
            Card door2 = add(child, 900, 500);
            Board grand = new Board("neto");
            door2.setChildBoard(grand);
            add(grand, 0, 0);
            BoardView view = new BoardView();
            view.resize(VIEW_W, VIEW_H);
            EditHistory history = new EditHistory(view::getBoard);
            NavigationController navigation = new NavigationController(view, new BreadcrumbView(), history);
            ViewFitController controller = new ViewFitController(view);

            navigation.openRoot(root);
            controller.fitAll();
            BoardView.ViewState rootFit = view.getViewState();
            assertTrue(rootFit.zoom() < 1);

            navigation.enter(door);
            assertEquals(BoardView.ViewState.DEFAULT, view.getViewState(), "o filho começa na visão padrão");
            controller.fitAll();
            BoardView.ViewState childFit = view.getViewState();
            assertNotEquals(rootFit, childFit);

            navigation.enter(door2);
            controller.fitAll();
            BoardView.ViewState grandFit = view.getViewState();
            assertEquals(3.0, grandFit.zoom(), 1e-9, "um card sozinho");

            navigation.back();
            assertEquals(childFit, view.getViewState(), "o filho lembrou o ajuste dele");
            navigation.back();
            assertEquals(rootFit, view.getViewState(), "o principal lembrou o ajuste dele");
            assertFalse(history.canUndoProperty().get(), "navegar e ajustar a visão não entram no desfazer");
        });
    }
}
