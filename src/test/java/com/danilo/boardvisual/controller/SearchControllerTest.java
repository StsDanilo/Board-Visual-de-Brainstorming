package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSearch;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BreadcrumbView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.ConnectionView;
import com.danilo.boardvisual.view.SearchBarView;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A busca de texto (Ctrl+F) pelo controller, num {@link BoardView} e numa {@link SearchBarView} de
 * verdade, sem janela: digitar, destaque, Enter / Shift+Enter, centralizar, Esc, escopo "todos os
 * boards" e o voltar. Regras na seção 3.26 do DOCUMENTACAO.md.
 */
class SearchControllerTest {

    private static final double VIEW_W = 1000;
    private static final double VIEW_H = 600;
    private static final PseudoClass MATCH = PseudoClass.getPseudoClass("search-match");
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("search-current");

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
            throw new AssertionError(e.getCause() instanceof AssertionError ? e.getCause() : e);
        }
    }

    private static void onFx(Runnable work) {
        onFx(() -> {
            work.run();
            return null;
        });
    }

    /** Tudo montado como na aplicação, menos a janela. */
    private static final class Rig {
        final Board root = new Board("Principal");
        final BoardView view = new BoardView();
        final SearchBarView bar = new SearchBarView();
        final EditHistory history = new EditHistory(view::getBoard);
        final NavigationController navigation = new NavigationController(view, new BreadcrumbView(), history);
        final SearchController search = new SearchController(view, bar, navigation);
        final List<String> messages = new ArrayList<>();

        Rig() {
            view.resize(VIEW_W, VIEW_H);
            search.setNotifier(messages::add);
        }

        Card add(Board board, String text, double x, double y) {
            Card card = new Card(x, y);
            card.setText(text);
            board.addCard(card);
            return card;
        }

        void show() {
            navigation.openRoot(root);
        }

        void type(String text) {
            bar.getField().setText(text);
        }

        /** Dispara a tecla no campo, como o JavaFX faria (o filtro do campo reage a ela). */
        void press(KeyCode code, boolean shift) {
            Event.fireEvent(bar.getField(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
        }

        boolean match(Card card) {
            return states(card).contains(MATCH);
        }

        boolean current(Card card) {
            return states(card).contains(CURRENT);
        }

        java.util.Set<PseudoClass> states(Card card) {
            CardView cardView = view.getCardView(card).orElseThrow();
            return cardView.getPseudoClassStates();
        }

        /** Onde o centro do card cai na tela. */
        double[] screenCenter(Card card) {
            BoardView.ViewState s = view.getViewState();
            return new double[]{s.panX() + card.getCenterX() * s.zoom(), s.panY() + card.getCenterY() * s.zoom()};
        }
    }

    // ------------------------------------------------------- digitar e destacar

    @Test
    void typingHighlightsTheMatchesAndMarksTheCurrentOne() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "Ideia de produto", 0, 0);
            Card b = r.add(r.root, "Outra coisa", 300, 0);
            Card c = r.add(r.root, "Mais uma IDEIA", 600, 0);
            r.show();
            r.search.open();

            r.type("ideia");

            assertTrue(r.match(a) && r.match(c));
            assertFalse(r.match(b), "quem não casa não é destacado");
            assertTrue(r.current(a), "o primeiro é o atual");
            assertFalse(r.current(c), "só o atual é mais forte");
            assertEquals("1 de 2", r.bar.getSummary());
            assertFalse(r.bar.isNoMatch());
        });
    }

    @Test
    void refiningTheTextKeepsTheCurrentResultWhileItStillMatches() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "tema alfa", 0, 0);
            Card b = r.add(r.root, "tema beta", 300, 0);
            r.show();
            r.search.open();
            r.type("tema");
            r.press(KeyCode.ENTER, false); // atual: b

            r.type("tema b");

            assertTrue(r.current(b));
            assertFalse(r.match(a));
            assertEquals("1 de 1", r.bar.getSummary());
        });
    }

    @Test
    void noResultTellsItDiscreetlyAndHighlightsNothing() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "banana", 0, 0);
            r.show();
            r.search.open();

            r.type("maçã");

            assertEquals("Nenhum resultado", r.bar.getSummary());
            assertTrue(r.bar.isNoMatch());
            assertFalse(r.match(a));
            assertFalse(r.view.hasSearchHighlights());

            r.type("");
            assertEquals("", r.bar.getSummary(), "campo vazio: sem aviso");
            assertFalse(r.bar.isNoMatch());
        });
    }

    @Test
    void findsPanelContentArrowsSectionsAndImageCaptions() {
        onFx(() -> {
            Rig r = new Rig();
            Card panel = r.add(r.root, "Resumo", 0, 0);
            panel.setPanelMode(PanelMode.LIST);
            panel.setListItemTexts(List.of("verificar o ORÇAMENTO"));
            Card frame = Card.create(com.danilo.boardvisual.model.CardShape.FRAME, 700, 500);
            frame.setText("Etapa do orçamento");
            r.root.addCard(frame);
            Card image = Card.createImage("f.png", 400, 400, 160, 90);
            image.setText("legenda: orçamento aprovado");
            r.root.addCard(image);
            Card x = r.add(r.root, "x", 0, 300);
            Connection arrow = new Connection(panel, x);
            arrow.setLabel("depende do orçamento");
            r.root.addConnection(arrow);
            r.show();
            r.search.open();

            r.type("orcamento");

            assertEquals(4, r.search.getResults().size());
            assertTrue(r.match(panel) && r.match(frame) && r.match(image));
            ConnectionView arrowView = r.view.getConnectionView(arrow).orElseThrow();
            assertTrue(arrowView.getPseudoClassStates().contains(MATCH), "o texto da seta casa");
            assertTrue(arrowView.getLabelLayer().getPseudoClassStates().contains(MATCH),
                    "o contorno vai no texto, que está em outra camada");
        });
    }

    // ---------------------------------------------- Enter, Shift+Enter, centralizar

    @Test
    void enterGoesToTheNextShiftEnterToThePreviousAndBothWrapAround() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "item 1", 0, 0);
            Card b = r.add(r.root, "item 2", 3000, 100);
            Card c = r.add(r.root, "item 3", -2000, 900);
            r.show();
            r.search.open();
            r.type("item");
            assertTrue(r.current(a));

            r.press(KeyCode.ENTER, false);
            assertTrue(r.current(b) && !r.current(a));
            assertEquals("2 de 3", r.bar.getSummary());
            r.press(KeyCode.ENTER, false);
            assertTrue(r.current(c));
            r.press(KeyCode.ENTER, false);
            assertTrue(r.current(a), "depois do último, o primeiro");
            r.press(KeyCode.ENTER, true);
            assertTrue(r.current(c), "Shift+Enter antes do primeiro: o último");
            assertEquals("3 de 3", r.bar.getSummary());
            assertTrue(r.match(a) && r.match(b) && r.match(c), "todos continuam destacados");
        });
    }

    @Test
    void goingToAResultCentersItAndKeepsTheZoom() {
        onFx(() -> {
            Rig r = new Rig();
            r.add(r.root, "perto", 0, 0);
            Card far = r.add(r.root, "perto do fim", 4000, 2500);
            r.show();
            r.view.setViewState(new BoardView.ViewState(10, 20, 1.7));
            r.search.open();
            r.type("fim");
            // Digitar: o resultado está fora da tela, então a visão já vai até ele.
            double[] c = r.screenCenter(far);
            assertEquals(VIEW_W / 2, c[0], 1e-6);
            assertEquals(VIEW_H / 2, c[1], 1e-6);

            r.view.setViewState(new BoardView.ViewState(0, 0, 1.7));
            r.press(KeyCode.ENTER, false); // um só resultado: Enter volta a centralizá-lo
            c = r.screenCenter(far);
            assertEquals(VIEW_W / 2, c[0], 1e-6, "centralizado");
            assertEquals(VIEW_H / 2, c[1], 1e-6);
            assertEquals(1.7, r.view.zoomProperty().get(), 1e-9, "o zoom da pessoa não muda");
        });
    }

    @Test
    void anIllegiblyLowZoomRisesToTheReadableMinimumWhenGoingToAResult() {
        onFx(() -> {
            Rig r = new Rig();
            r.add(r.root, "a", 0, 0);
            Card target = r.add(r.root, "alvo", 4000, 0);
            r.show();
            r.view.setViewState(new BoardView.ViewState(0, 0, 0.2));
            r.search.open();

            r.type("alvo");

            assertEquals(0.5, r.view.zoomProperty().get(), 1e-9, "20% não se lê: sobe a 50%");
            assertEquals(VIEW_W / 2, r.screenCenter(target)[0], 1e-6);
        });
    }

    @Test
    void typingDoesNotMoveTheViewWhileTheResultIsAlreadyVisible() {
        onFx(() -> {
            Rig r = new Rig();
            r.add(r.root, "visível", 300, 200);
            r.show();
            BoardView.ViewState before = r.view.getViewState();
            r.search.open();

            r.type("vis");

            assertEquals(before, r.view.getViewState(), "já aparece inteiro: a tela não anda enquanto digita");
        });
    }

    @Test
    void theButtonsDoTheSameAsTheKeys() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "item 1", 0, 0);
            Card b = r.add(r.root, "item 2", 300, 0);
            r.show();
            r.search.open();
            r.type("item");

            r.bar.getNextButton().fire();
            assertTrue(r.current(b));
            r.bar.getPreviousButton().fire();
            assertTrue(r.current(a));
            r.bar.getCloseButton().fire();
            assertFalse(r.bar.isOpen());
        });
    }

    @Test
    void theSearchIsRedoneOnEveryStepSoWhatChangedIsSeen() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "item 1", 0, 0);
            Card b = r.add(r.root, "item 2", 300, 0);
            r.show();
            r.search.open();
            r.type("item");
            assertEquals("1 de 2", r.bar.getSummary());

            r.root.removeCard(b);
            Card late = r.add(r.root, "item novo", 600, 0);
            r.press(KeyCode.ENTER, false);

            assertEquals("2 de 2", r.bar.getSummary());
            assertTrue(r.current(late), "o card novo entra e o excluído saiu");
        });
    }

    // ------------------------------------------------------------------- Esc

    @Test
    void escapeClosesTheSearchAndRemovesTheHighlights() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "ideia", 0, 0);
            r.show();
            r.search.open();
            r.type("ideia");
            assertTrue(r.match(a));

            r.press(KeyCode.ESCAPE, false);

            assertFalse(r.search.isOpen());
            assertFalse(r.bar.isVisible());
            assertFalse(r.match(a) || r.current(a));
            assertFalse(r.view.hasSearchHighlights());
        });
    }

    @Test
    void closeIfOpenTellsWhetherThereWasSomethingToClose() {
        onFx(() -> {
            Rig r = new Rig();
            r.add(r.root, "x", 0, 0);
            r.show();
            assertFalse(r.search.closeIfOpen(), "fechada: o Esc do canvas segue para as outras regras");
            r.search.open();
            assertTrue(r.search.closeIfOpen());
            assertFalse(r.search.closeIfOpen());
        });
    }

    @Test
    void reopeningKeepsTheLastTextAndHighlightsAgain() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "ideia", 0, 0);
            r.show();
            r.search.open();
            r.type("ideia");
            r.search.close();
            assertFalse(r.match(a));

            r.search.open();

            assertEquals("ideia", r.bar.getField().getText());
            assertTrue(r.match(a) && r.current(a));
        });
    }

    @Test
    void searchingNeverChangesTheModelOrTheHistory() {
        onFx(() -> {
            Rig r = new Rig();
            Card a = r.add(r.root, "ideia", 0, 0);
            Card door = r.add(r.root, "porta", 300, 0);
            door.setChildBoard(new Board("filho"));
            r.show();
            var before = r.root.snapshot();

            r.search.open();
            r.bar.getAllBoardsBox().setSelected(true);
            r.type("ideia");
            r.press(KeyCode.ENTER, false);
            r.press(KeyCode.ENTER, true);
            r.search.close();

            assertEquals(before, r.root.snapshot());
            assertFalse(r.history.canUndoProperty().get(), "a busca não entra no desfazer");
        });
    }

    // -------------------------------------------------- em todos os boards

    /** principal: "alfa projeto", porta Filho; Filho: "beta projeto", porta Neto; Neto: "gama projeto". */
    private static final class Tree {
        final Rig r = new Rig();
        final Card a = r.add(r.root, "alfa projeto", 0, 0);
        final Card door1 = r.add(r.root, "Filho", 400, 0);
        final Board child = new Board("filho");
        final Card b = r.add(child, "beta projeto", 0, 0);
        final Card door2 = r.add(child, "Neto", 400, 0);
        final Board grand = new Board("neto");
        final Card c = r.add(grand, "gama projeto", 100, 100);

        Tree() {
            door1.setChildBoard(child);
            door2.setChildBoard(grand);
            r.show();
        }
    }

    @Test
    void currentBoardScopeOnlySeesTheBoardOnScreen() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();

            t.r.type("projeto");

            assertEquals(1, t.r.search.getResults().size());
            assertTrue(t.r.match(t.a));
            assertFalse(t.r.bar.getResultList().isVisible(), "a lista de caminhos é só da busca em todos os boards");
        });
    }

    @Test
    void allBoardsScopeListsTheResultsWithTheirPaths() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.bar.getAllBoardsBox().setSelected(true);

            t.r.type("projeto");

            assertEquals(3, t.r.search.getResults().size());
            assertEquals("1 de 3", t.r.bar.getSummary());
            assertTrue(t.r.bar.getResultList().isVisible());
            List<String> paths = t.r.bar.getResultList().getItems().stream().map(BoardSearch.Hit::pathLabel).toList();
            assertEquals(List.of("Principal", "Principal › Filho", "Principal › Filho › Neto"), paths);
            assertTrue(t.r.current(t.a), "digitando, o foco fica no board que se vê (sem pular de board)");
            assertEquals(t.r.root, t.r.navigation.getCurrent(), "digitar não troca de board");
        });
    }

    @Test
    void enterWalksAcrossBoardsAndTheBackButtonStaysCoherent() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.bar.getAllBoardsBox().setSelected(true);
            t.r.type("projeto");

            t.r.press(KeyCode.ENTER, false); // 2º resultado: no board Filho
            assertEquals(t.child, t.r.navigation.getCurrent(), "o Enter troca de board");
            assertEquals(List.of("Principal", "Filho"), t.r.navigation.pathNames());
            assertTrue(t.r.current(t.b), "e destaca o card lá");
            assertTrue(t.r.match(t.b));
            assertFalse(t.r.view.getCardView(t.door2).orElseThrow().getPseudoClassStates().contains(MATCH));
            assertEquals("2 de 3", t.r.bar.getSummary());
            double[] center = t.r.screenCenter(t.b);
            assertEquals(VIEW_W / 2, center[0], 1e-6, "centralizado no board de destino");

            t.r.press(KeyCode.ENTER, false); // 3º: no neto
            assertEquals(t.grand, t.r.navigation.getCurrent());
            assertEquals(List.of("Principal", "Filho", "Neto"), t.r.navigation.pathNames());
            assertTrue(t.r.current(t.c));

            t.r.press(KeyCode.ENTER, false); // dá a volta: 1º, no principal
            assertEquals(t.r.root, t.r.navigation.getCurrent());
            assertTrue(t.r.current(t.a));

            // O voltar leva ao board de onde se saiu, como nos links entre boards.
            assertTrue(t.r.navigation.back());
        });
    }

    @Test
    void clickingAResultInTheListJumpsThereAndBackReturnsToTheOrigin() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.bar.getAllBoardsBox().setSelected(true);
            t.r.type("gama");
            assertEquals(1, t.r.search.getResults().size());
            assertEquals(t.r.root, t.r.navigation.getCurrent());

            // O clique numa linha da lista chama isto (o roteiro da janela real clica de verdade na linha).
            t.r.bar.pickResult(0);

            assertEquals(t.grand, t.r.navigation.getCurrent());
            assertEquals(List.of("Principal", "Filho", "Neto"), t.r.navigation.pathNames(), "o caminho real até o destino");
            assertTrue(t.r.current(t.c));
            assertEquals(VIEW_W / 2, t.r.screenCenter(t.c)[0], 1e-6);

            assertTrue(t.r.navigation.back(), "voltar");
            assertEquals(t.r.root, t.r.navigation.getCurrent(), "volta ao board de onde se saiu, não ao pai do destino");
        });
    }

    @Test
    void enterOnAResultInAnotherBoardGoesThereBeforeWalking() {
        onFx(() -> {
            Tree t = new Tree();
            // Nada no principal casa: o primeiro resultado está no Filho.
            t.a.setText("alfa");
            t.r.search.open();
            t.r.bar.getAllBoardsBox().setSelected(true);

            t.r.type("projeto");
            assertEquals(t.r.root, t.r.navigation.getCurrent(), "digitando não troca de board");
            assertEquals("1 de 2", t.r.bar.getSummary());

            t.r.press(KeyCode.ENTER, false);

            assertEquals(t.child, t.r.navigation.getCurrent(), "o primeiro Enter vai ao resultado atual, que ainda não foi visto");
            assertEquals("1 de 2", t.r.bar.getSummary(), "sem pular o primeiro");
            assertTrue(t.r.current(t.b));
        });
    }

    @Test
    void navigatingAwayWhileSearchingRedoesTheHighlightsForTheNewBoard() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.type("projeto");
            assertTrue(t.r.match(t.a));

            t.r.navigation.enter(t.door1); // o usuário entra no Filho com a busca aberta (escopo: board atual)

            assertTrue(t.r.search.isOpen());
            assertTrue(t.r.match(t.b), "a busca do board atual vale para o board novo");
            assertEquals("1 de 1", t.r.bar.getSummary());
        });
    }

    @Test
    void aResultThatDisappearedIsReportedInsteadOfBreaking() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.bar.getAllBoardsBox().setSelected(true);
            t.r.type("gama");
            t.r.root.removeCard(t.door1); // o card que continha o board sumiu

            t.r.press(KeyCode.ENTER, false);

            assertEquals(t.r.root, t.r.navigation.getCurrent(), "não foi a lugar nenhum");
            assertEquals("Nenhum resultado", t.r.bar.getSummary());
        });
    }

    @Test
    void theCurrentBoardChangesTheSearchHighlightsButNotTheResultOfAnotherScope() {
        onFx(() -> {
            Tree t = new Tree();
            t.r.search.open();
            t.r.type("projeto");
            t.r.bar.getAllBoardsBox().setSelected(true);
            assertEquals(3, t.r.search.getResults().size());
            t.r.bar.getAllBoardsBox().setSelected(false);
            assertEquals(1, t.r.search.getResults().size());
            assertNotEquals("", t.r.bar.getSummary());
        });
    }
}
