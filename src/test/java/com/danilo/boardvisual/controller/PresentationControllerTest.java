package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSnapshot;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.PanelMode;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BoardView.ViewState;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.FloatingPanelView;
import com.danilo.boardvisual.view.MainWindow;
import com.danilo.boardvisual.view.Tool;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O modo apresentação (F5) com a janela do app montada de verdade (MainWindow + MainController + Stage, sem
 * exibir): os eventos são os do próprio JavaFX, disparados nos nós, e passam pelos filtros da janela como os de
 * um clique ou tecla reais. Sem tela (JavaFX indisponível), é pulado em vez de falhar.
 *
 * O alvo dos eventos de mouse é escolhido aqui, nó a nó (o picking de verdade da cena é conferido no roteiro de
 * interface, fora do Maven); o que se prova é a regra: o que o filtro deixa passar e o que consome, e que nenhum
 * evento de edição mexe no modelo.
 */
class PresentationControllerTest {

    private static final String CSS = PresentationControllerTest.class
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
            return result.get(30, TimeUnit.SECONDS);
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

    // ------------------------------------------------------------------ o cenário

    /**
     * <pre>
     * Principal: Alfa --liga--> Beta, Painel (lista), Notas (texto), Projeto (board), Outro (board), Link, Atalho
     *   Projeto: Dentro 1, Dentro 2, Fundo (board)
     *     Fundo: Neto
     *   Outro: Solto
     * </pre>
     * Ordem dos slides: Principal, Projeto, Fundo, Outro. O card "Atalho" tem link para o board do "Fundo".
     */
    private static final class Rig {
        final Stage stage = new Stage();
        final MainWindow window = new MainWindow();
        final List<String> opened = new ArrayList<>();
        final MainController main;
        final Scene scene;
        final BoardView view;
        final Board root;
        final Card alfa;
        final Card beta;
        final Card panel;
        final Card notes;
        final Card project;
        final Card other;
        final Card link;
        final Card shortcut;
        final Connection connection;
        final Card deep;

        Rig() {
            main = new MainController(stage, window, opened::add);
            scene = new Scene(window, 1200, 800);
            scene.getStylesheets().add(CSS);
            stage.setScene(scene);
            view = window.getBoardView();
            root = main.getNavigation().getRoot();

            alfa = card(root, 100, 100, "Alfa");
            beta = card(root, 450, 100, "Beta");
            connection = root.connect(alfa, beta).orElseThrow();
            connection.setLabel("liga");
            panel = card(root, 100, 300, "Painel");
            panel.setPanelMode(PanelMode.LIST);
            panel.addListItem("item um");
            notes = card(root, 450, 300, "Notas");
            notes.setPanelMode(PanelMode.TEXT);
            notes.setDetailText("detalhes das notas");

            project = card(root, 100, 500, "Projeto");
            project.setChildBoard(new Board(""));
            card(project.getChildBoard(), 0, 0, "Dentro 1");
            card(project.getChildBoard(), 300, 0, "Dentro 2");
            deep = card(project.getChildBoard(), 0, 300, "Fundo");
            deep.setChildBoard(new Board(""));
            card(deep.getChildBoard(), 0, 0, "Neto");

            other = card(root, 450, 500, "Outro");
            other.setChildBoard(new Board(""));
            card(other.getChildBoard(), 0, 0, "Solto");

            link = card(root, 800, 100, "Link");
            link.setLink("https://exemplo.com/x");
            shortcut = card(root, 800, 300, "Atalho");
            shortcut.setBoardLink(deep.getChildBoard().getId());

            layout();
        }

        void layout() {
            window.resize(1200, 800);
            window.applyCss();
            window.layout();
        }

        static Card card(Board board, double x, double y, String text) {
            Card card = new Card(x, y);
            card.setText(text);
            board.addCard(card);
            return card;
        }

        CardView cardView(Card card) {
            return view.getCardView(card).orElseThrow();
        }

        /** O que o usuário veria como "o modelo": uma foto de cada board da árvore inteira. */
        List<BoardSnapshot> snapshots() {
            return BoardTree.entries(main.getNavigation().getRoot()).stream().map(e -> e.board().snapshot()).toList();
        }

        FloatingPanelView openPanel() {
            return view.getChildren().stream().filter(FloatingPanelView.class::isInstance)
                    .map(FloatingPanelView.class::cast).findFirst().orElse(null);
        }

        Board current() {
            return main.getNavigation().getCurrent();
        }

        PresentationController presentation() {
            return main.getPresentation();
        }
    }

    // ------------------------------------------------------------------ eventos

    private static Point2D center(Node node) {
        Bounds b = node.localToScene(node.getBoundsInLocal());
        return new Point2D(b.getCenterX(), b.getCenterY());
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, Node target, Point2D scene, MouseButton button,
                                    int clicks) {
        boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
        return new MouseEvent(null, target, type, scene.getX(), scene.getY(), scene.getX(), scene.getY(), button,
                clicks, false, false, false, false, down && button == MouseButton.PRIMARY,
                down && button == MouseButton.MIDDLE, down && button == MouseButton.SECONDARY, false, false,
                type != MouseEvent.MOUSE_DRAGGED, new PickResult(target, scene.getX(), scene.getY()));
    }

    /**
     * Dispara o evento no nó e devolve se ele foi consumido ANTES de chegar ao nó (pelos filtros da janela, da cena
     * ou dos pais). O JavaFX consome uma cópia do evento no caminho: o objeto original nunca fica "consumido", por
     * isso a conta é feita com um filtro-sonda no próprio alvo, que só roda se ninguém consumiu antes.
     */
    @SuppressWarnings("unchecked")
    private static boolean fire(Node target, Event event) {
        boolean[] reached = {false};
        EventHandler<Event> probe = e -> reached[0] = true;
        EventType<Event> type = (EventType<Event>) event.getEventType();
        target.addEventFilter(type, probe);
        try {
            Event.fireEvent(target, event);
        } finally {
            target.removeEventFilter(type, probe);
        }
        return !reached[0];
    }

    /**
     * Aperta, solta e clica (como um clique de verdade) e devolve, para cada um dos três eventos, se ele foi
     * consumido antes de chegar ao alvo ({@link #fire}).
     */
    private static List<Boolean> click(Node target, Point2D scene, MouseButton button, int clicks) {
        List<Boolean> consumed = new ArrayList<>();
        for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED)) {
            consumed.add(fire(target, mouse(type, target, scene, button, clicks)));
        }
        return consumed;
    }

    private static void click(Node target) {
        click(target, center(target), MouseButton.PRIMARY, 1);
    }

    private static void drag(Node target, Point2D from, Point2D to, MouseButton button) {
        Event.fireEvent(target, mouse(MouseEvent.MOUSE_PRESSED, target, from, button, 1));
        Event.fireEvent(target, mouse(MouseEvent.MOUSE_DRAGGED, target, new Point2D((from.getX() + to.getX()) / 2,
                (from.getY() + to.getY()) / 2), button, 1));
        Event.fireEvent(target, mouse(MouseEvent.MOUSE_DRAGGED, target, to, button, 1));
        Event.fireEvent(target, mouse(MouseEvent.MOUSE_RELEASED, target, to, button, 1));
    }

    private static KeyEvent key(EventType<KeyEvent> type, KeyCode code, boolean ctrl, boolean shift, boolean alt) {
        boolean typed = type == KeyEvent.KEY_TYPED;
        String character = typed ? "x" : KeyEvent.CHAR_UNDEFINED;
        return new KeyEvent(type, character, typed ? character : "", typed ? KeyCode.UNDEFINED : code,
                shift, ctrl, alt, false);
    }

    /** Aperta e solta a tecla no nó; devolve se o "apertar" foi consumido. */
    private static boolean press(Node target, KeyCode code, boolean ctrl, boolean shift, boolean alt) {
        boolean consumed = fire(target, key(KeyEvent.KEY_PRESSED, code, ctrl, shift, alt));
        Event.fireEvent(target, key(KeyEvent.KEY_RELEASED, code, ctrl, shift, alt));
        return consumed;
    }

    private static boolean press(Node target, KeyCode code) {
        return press(target, code, false, false, false);
    }

    private static ScrollEvent wheel(Node target, Point2D scene, double deltaY) {
        return new ScrollEvent(ScrollEvent.SCROLL, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                false, false, false, false, false, false, 0, deltaY, 0, deltaY, ScrollEvent.HorizontalTextScrollUnits.NONE,
                0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, new PickResult(target, scene.getX(), scene.getY()));
    }

    private static TextArea textAreaOf(CardView cardView) {
        return (TextArea) cardView.lookup(".card-text");
    }

    // ------------------------------------------------------------------ entrar e sair

    @Test
    void startShowsTheBoardFullScreenWithoutBarsAndStopBringsEverythingBack() {
        onFx(() -> {
            Rig r = new Rig();
            ViewState before = new ViewState(37, -12, 0.8);
            javafx.scene.input.KeyCombination exitKeyBefore = r.stage.getFullScreenExitKeyCombination();
            String exitHintBefore = r.stage.getFullScreenExitHint();
            r.view.setViewState(before);
            r.window.getToolBar().setActiveTool(Tool.RECTANGLE);
            r.view.select(r.alfa);
            assertFalse(r.presentation().isPresenting());

            r.presentation().start();

            assertTrue(r.presentation().isPresenting());
            assertTrue(r.window.presentingProperty().get());
            assertTrue(r.stage.isFullScreen(), "tela cheia");
            assertEquals("", r.stage.getFullScreenExitHint(), "a dica padrão do JavaFX some");
            assertSame(javafx.scene.input.KeyCombination.NO_MATCH, r.stage.getFullScreenExitKeyCombination(),
                    "o Esc é nosso, não o do JavaFX");
            for (Node bar : List.of(r.window.getMenuBar(), r.window.getToolBar(), r.window.getStatusBar())) {
                assertFalse(bar.isVisible(), bar + " escondida");
                assertFalse(bar.isManaged(), bar + " não ocupa lugar");
            }
            assertFalse(r.window.getToolBar().getConnectionStyleBar().isVisible(), "mini barra de estilos de seta");
            assertTrue(r.window.getBreadcrumb().isVisible(), "o caminho continua (discreto)");
            assertTrue(r.window.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("presenting")));
            assertTrue(r.view.isPresenting());
            assertTrue(r.view.getSelection().isEmpty(), "a seleção some ao entrar");
            assertFalse(r.window.getSelectionToolbar().isVisible(), "barra flutuante da seleção");
            assertEquals(Tool.SELECT, r.window.getToolBar().getActiveTool(), "ferramenta de edição desligada");
            assertEquals(PresentationController.START_NOTICE, r.window.getPresentationNotice());
            assertNotEquals(before, r.view.getViewState(), "o board foi enquadrado");

            r.presentation().stop();

            assertFalse(r.presentation().isPresenting());
            assertFalse(r.stage.isFullScreen(), "janela fora da tela cheia");
            assertEquals(exitKeyBefore, r.stage.getFullScreenExitKeyCombination(), "a combinação padrão do JavaFX volta");
            assertEquals(exitHintBefore, r.stage.getFullScreenExitHint(), "e a dica também");
            for (Node bar : List.of(r.window.getMenuBar(), r.window.getToolBar(), r.window.getStatusBar())) {
                assertTrue(bar.isVisible());
                assertTrue(bar.isManaged());
            }
            assertFalse(r.view.isPresenting());
            assertEquals(Tool.RECTANGLE, r.window.getToolBar().getActiveTool(), "a ferramenta de antes volta");
            assertEquals(before, r.view.getViewState(), "a visão de antes volta exatamente");
            assertEquals("", r.window.getPresentationNotice());
        });
    }

    @Test
    void startAndStopAreIdempotent() {
        onFx(() -> {
            Rig r = new Rig();
            ViewState before = new ViewState(10, 20, 0.5);
            r.view.setViewState(before);
            r.presentation().stop(); // fora da apresentação: nada
            assertEquals(before, r.view.getViewState());

            r.presentation().start();
            ViewState fitted = r.view.getViewState();
            r.presentation().start(); // de novo: não guarda o estado "de antes" por cima
            assertEquals(fitted, r.view.getViewState());
            r.presentation().stop();
            assertEquals(before, r.view.getViewState(), "um segundo start não perdeu a visão original");
            r.presentation().stop();
            assertEquals(before, r.view.getViewState());
        });
    }

    @Test
    void presentMenuItemStartsAndTheViewMenuHasIt() {
        onFx(() -> {
            Rig r = new Rig();
            MenuItem item = r.window.getPresentItem();
            assertEquals("F5", item.getAccelerator().getName());
            assertTrue(r.window.getMenuBar().getMenus().stream().filter(m -> m.getText().equals("Exibir"))
                    .anyMatch(m -> m.getItems().contains(item)), "Exibir > Apresentar");
            item.fire();
            assertTrue(r.presentation().isPresenting());
        });
    }

    @Test
    void theBoardAppearsFramedWithEveryCardInsideTheScreen() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            double w = r.view.getWidth();
            double h = r.view.getHeight();
            ViewState state = r.view.getViewState();
            for (Card card : r.root.getCards()) {
                Box box = new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight());
                double left = box.x() * state.zoom() + state.panX();
                double top = box.y() * state.zoom() + state.panY();
                double right = (box.x() + box.width()) * state.zoom() + state.panX();
                double bottom = (box.y() + box.height()) * state.zoom() + state.panY();
                assertTrue(left >= 0 && top >= 0 && right <= w && bottom <= h,
                        card.getText() + " inteiro na tela: " + left + "," + top + " " + right + "," + bottom
                                + " em " + w + "x" + h);
            }
        });
    }

    @Test
    void theFitIsRedoneWhenTheFullScreenSizeArrivesAfterwardsButNotAfterTheUserMovedTheView() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            ViewState small = r.view.getViewState();

            r.window.resize(1800, 1000); // a tela cheia "chega" depois
            r.window.applyCss();
            r.window.layout();
            ViewState big = r.view.getViewState();
            assertNotEquals(small, big, "enquadrou de novo para o tamanho novo");
            assertTrue(big.zoom() > small.zoom());

            r.view.zoomAt(1.2, 100, 100); // o usuário mexeu na visão
            ViewState moved = r.view.getViewState();
            r.window.resize(1000, 700);
            r.window.applyCss();
            r.window.layout();
            assertEquals(moved, r.view.getViewState(), "depois de mexer, o tamanho novo não desfaz a visão do usuário");
        });
    }

    @Test
    void leavingFullScreenFromOutsideEndsThePresentation() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            r.stage.setFullScreen(false); // o sistema tirou a janela da tela cheia
            assertFalse(r.presentation().isPresenting());
            assertTrue(r.window.getMenuBar().isVisible());
            assertFalse(r.view.isPresenting());
        });
    }

    @Test
    void newBoardWhilePresentingEndsThePresentationFirst() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            r.main.newBoard();
            assertFalse(r.presentation().isPresenting());
            assertFalse(r.stage.isFullScreen());
            assertTrue(r.view.getBoard().getCards().isEmpty());
        });
    }

    // ------------------------------------------------------------------ Esc, F5 e teclas

    @Test
    void escapeExitsAndIsConsumedSoTheKeyboardControllerDoesNotAlsoClearTheSelection() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            assertTrue(press(r.view, KeyCode.ESCAPE), "o Esc é consumido");
            assertFalse(r.presentation().isPresenting());
            assertFalse(r.stage.isFullScreen());
        });
    }

    @Test
    void escapeExitsDirectlyEvenWithAPanelOpenAndClosesIt() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            click(r.cardView(r.notes).getPanelButton());
            r.layout();
            assertNotNull(r.openPanel(), "o painel abriu");

            press(r.view, KeyCode.ESCAPE);

            assertFalse(r.presentation().isPresenting(), "um Esc só sai, mesmo com o painel aberto");
            assertNull(r.openPanel(), "o painel fecha junto");
            assertFalse(r.stage.isFullScreen());
        });
    }

    @Test
    void f5InsideThePresentationDoesNothing() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            ViewState fitted = r.view.getViewState();
            assertTrue(press(r.view, KeyCode.F5), "o F5 é consumido: não reinicia nem sai");
            assertTrue(r.presentation().isPresenting());
            assertEquals(fitted, r.view.getViewState());
        });
    }

    @Test
    void shiftOneFitsAgainAndCtrlZeroGoesBackToOneHundredPercent() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            ViewState fitted = r.view.getViewState();
            r.view.zoomAt(1.4, 200, 200);
            r.view.panBy(33, 21);
            assertNotEquals(fitted, r.view.getViewState());

            assertTrue(press(r.view, KeyCode.DIGIT1, false, true, false), "Shift+1 (DIGIT1: igual no ABNT2)");
            assertEquals(fitted, r.view.getViewState(), "enquadrou de novo");

            assertTrue(press(r.view, KeyCode.DIGIT0, true, false, false));
            assertEquals(1.0, r.view.getViewState().zoom(), 1e-9);
            assertEquals(0, r.view.getViewState().panX(), 1e-9);
        });
    }

    // ------------------------------------------------------------------ slides

    @Test
    void arrowsAndPageKeysWalkTheBoardsInPdfOrderFittingEachOne() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            Board main = r.root;
            Board project = r.project.getChildBoard();
            Board deep = r.deep.getChildBoard();
            Board other = r.other.getChildBoard();
            assertSame(main, r.current());

            assertTrue(press(r.view, KeyCode.RIGHT));
            assertSame(project, r.current(), "→: o primeiro filho");
            ViewState projectView = r.view.getViewState();
            assertNotEquals(ViewState.DEFAULT, projectView, "o board de destino aparece enquadrado");
            assertEquals(2, r.main.getNavigation().pathNames().size(), "o caminho do topo é o do board novo");

            press(r.view, KeyCode.PAGE_DOWN);
            assertSame(deep, r.current(), "PageDown: o board dentro do filho");
            press(r.view, KeyCode.RIGHT);
            assertSame(other, r.current(), "o último filho de Projeto leva ao próximo da ordem do PDF");

            press(r.view, KeyCode.RIGHT);
            assertSame(other, r.current(), "no último, → não faz nada");
            assertEquals(PresentationController.LAST_NOTICE, r.window.getPresentationNotice());

            press(r.view, KeyCode.LEFT);
            assertSame(deep, r.current(), "←: o anterior");
            press(r.view, KeyCode.PAGE_UP);
            assertSame(project, r.current(), "PageUp: o anterior");
            press(r.view, KeyCode.LEFT);
            assertSame(main, r.current());

            r.window.hidePresentationNotice();
            press(r.view, KeyCode.LEFT);
            assertSame(main, r.current(), "no primeiro, ← não faz nada");
            assertEquals(PresentationController.FIRST_NOTICE, r.window.getPresentationNotice());
        });
    }

    @Test
    void slideKeysWithModifiersDoNotNavigateAndAltLeftGoesBack() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            assertTrue(press(r.view, KeyCode.RIGHT, true, false, false), "Ctrl+→ é consumido");
            assertTrue(press(r.view, KeyCode.RIGHT, false, true, false), "Shift+→ também");
            assertSame(r.root, r.current(), "com modificador, as setas não passam de slide");

            press(r.view, KeyCode.RIGHT);
            assertSame(r.project.getChildBoard(), r.current());
            assertTrue(press(r.view, KeyCode.LEFT, false, false, true), "Alt+←: voltar, não 'slide anterior'");
            assertSame(r.root, r.current(), "voltou ao board de onde veio");
            assertFalse(r.main.getNavigation().back(), "e estamos de volta no começo da pilha");
        });
    }

    @Test
    void enteringANestedBoardWithTheCardButtonFitsItAndBackReturnsFitted() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();

            click(r.cardView(r.project).getBoardButton());
            assertSame(r.project.getChildBoard(), r.current(), "o botão do card entra no board aninhado");
            ViewState inside = r.view.getViewState();
            assertNotEquals(ViewState.DEFAULT, inside, "enquadrado");
            assertTrue(r.view.getBoard().getCards().stream().allMatch(c -> r.view.getCardView(c).isPresent()));
            assertTrue(r.view.getCardView(r.view.getBoard().getCards().get(0)).orElseThrow().isReadOnly(),
                    "os cards do board novo já nascem em leitura");

            r.layout(); // o botão voltar acabou de aparecer: precisa de layout para ter tamanho e lugar
            click(r.window.getBreadcrumb().getBackButton());
            assertSame(r.root, r.current(), "o botão voltar do caminho");
            assertTrue(r.presentation().isPresenting(), "continua apresentando");
        });
    }

    // ------------------------------------------------------------------ links e painéis

    @Test
    void linkMarkersWorkExternalOpensTheBrowserAndBoardLinkJumpsThenBackReturns() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();

            click(r.cardView(r.link).getMarker(CardMarkers.Kind.LINK));
            assertEquals(List.of("https://exemplo.com/x"), r.opened, "o link externo abre no navegador");
            assertSame(r.root, r.current());

            click(r.cardView(r.shortcut).getMarker(CardMarkers.Kind.BOARD_LINK));
            assertSame(r.deep.getChildBoard(), r.current(), "o link para outro board leva ao destino (jumpTo)");
            assertNotEquals(ViewState.DEFAULT, r.view.getViewState(), "o destino aparece enquadrado");

            r.layout(); // o botão voltar acabou de aparecer: precisa de layout para ter tamanho e lugar
            click(r.window.getBreadcrumb().getBackButton());
            assertSame(r.root, r.current(), "o botão voltar leva à origem do pulo");
            assertTrue(r.presentation().isPresenting());
        });
    }

    @Test
    void aBrokenBoardLinkWarnsWithTheNoticeNotWithADialog() {
        onFx(() -> {
            Rig r = new Rig();
            r.shortcut.setBoardLink("id-que-nao-existe");
            r.presentation().start();
            click(r.cardView(r.shortcut).getMarker(CardMarkers.Kind.BOARD_LINK));
            assertEquals("Board de destino não existe mais.", r.window.getPresentationNotice(),
                    "sem Alert por cima da tela cheia");
            assertSame(r.root, r.current());
        });
    }

    @Test
    void panelsOpenReadOnlyWithTheirContentAndCloseByTheXTheCardButtonOrAClickOutside() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();

            click(r.cardView(r.notes).getPanelButton());
            r.layout();
            FloatingPanelView text = r.openPanel();
            assertNotNull(text);
            assertTrue(text.isReadOnly());
            TextArea detail = (TextArea) text.lookup(".card-panel-detail");
            assertEquals("detalhes das notas", detail.getText(), "o conteúdo aparece");
            assertFalse(detail.isEditable());
            assertTrue(r.view.getSelection().isEmpty(), "abrir o painel não seleciona o card");
            assertTrue(r.view.getChildren().contains(text));

            click(text.lookup(".card-panel-close"));
            assertNull(r.openPanel(), "o ✕ fecha");

            click(r.cardView(r.panel).getPanelButton());
            r.layout();
            FloatingPanelView list = r.openPanel();
            assertNotNull(list);
            assertNull(list.lookup(".card-panel-new-item"), "sem o campo de adicionar item");
            assertNull(list.lookup(".card-panel-remove"), "sem o ✕ de remover item");
            TextField item = (TextField) list.lookup(".card-panel-item");
            assertEquals("item um", item.getText());
            assertFalse(item.isEditable());
            click(r.cardView(r.panel).getPanelButton());
            assertNull(r.openPanel(), "o botão do card alterna");

            click(r.cardView(r.notes).getPanelButton());
            assertNotNull(r.openPanel());
            click(r.view, center(r.view), MouseButton.PRIMARY, 1); // o fundo
            assertNull(r.openPanel(), "um clique fora do painel fecha");
            assertTrue(r.presentation().isPresenting());
        });
    }

    @Test
    void panelTextCannotBeTypedIntoNorItemsAddedOrRemoved() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            click(r.cardView(r.notes).getPanelButton());
            r.layout();
            TextArea detail = (TextArea) r.openPanel().lookup(".card-panel-detail");
            List<BoardSnapshot> before = r.snapshots();

            assertTrue(fire(detail, key(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, false, false, false)));
            assertTrue(press(detail, KeyCode.A));
            assertTrue(press(detail, KeyCode.BACK_SPACE));
            assertTrue(press(detail, KeyCode.V, true, false, false), "Ctrl+V no painel");
            assertTrue(click(detail, center(detail), MouseButton.PRIMARY, 2).get(0), "clicar no texto não edita");

            assertEquals("detalhes das notas", r.notes.getDetailText());
            assertEquals(before, r.snapshots());
        });
    }

    // ------------------------------------------------------------------ pan e zoom

    @Test
    void wheelZoomsAndMouseDragMovesTheViewWithAnyButton() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            ViewState fitted = r.view.getViewState();
            Point2D mid = center(r.view);

            Event.fireEvent(r.view, wheel(r.view, mid, 120));
            assertTrue(r.view.getViewState().zoom() > fitted.zoom(), "a roda dá zoom em torno do cursor");

            ViewState zoomed = r.view.getViewState();
            drag(r.view, mid, mid.add(40, 25), MouseButton.SECONDARY);
            assertEquals(zoomed.panX() + 40, r.view.getViewState().panX(), 1e-6, "botão direito arrastando move a visão");
            assertEquals(zoomed.panY() + 25, r.view.getViewState().panY(), 1e-6);

            ViewState afterRight = r.view.getViewState();
            drag(r.view, mid, mid.add(-30, 10), MouseButton.MIDDLE);
            assertEquals(afterRight.panX() - 30, r.view.getViewState().panX(), 1e-6, "botão do meio");

            ViewState afterMiddle = r.view.getViewState();
            drag(r.view, mid, mid.add(15, -5), MouseButton.PRIMARY);
            assertEquals(afterMiddle.panX() + 15, r.view.getViewState().panX(), 1e-6,
                    "o botão primário arrastando o fundo também move a visão (não há seleção a desenhar)");
            assertEquals(afterMiddle.zoom(), r.view.getViewState().zoom(), 1e-9);
            assertFalse(r.view.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("panning")),
                    "soltou: sem o cursor de mão fechada");
        });
    }

    @Test
    void draggingACardPansTheViewInsteadOfMovingTheCard() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            double x = r.alfa.getX();
            double y = r.alfa.getY();
            ViewState before = r.view.getViewState();
            Node body = r.cardView(r.alfa).getBody();
            Point2D p = center(body);

            drag(body, p, p.add(60, 40), MouseButton.PRIMARY);

            assertEquals(x, r.alfa.getX());
            assertEquals(y, r.alfa.getY());
            assertEquals(before.panX() + 60, r.view.getViewState().panX(), 1e-6);
            assertTrue(r.view.getSelection().isEmpty(), "e não selecionou o card");
        });
    }

    // ------------------------------------------------------------------ nada altera o modelo

    @Test
    void controlTheSameEventsDoEditTheBoardWhenNotPresenting() {
        // Sem este controle, os testes de "nada muda" poderiam passar só porque os eventos não chegam a lugar nenhum.
        onFx(() -> {
            Rig r = new Rig();
            r.view.select(r.beta);
            List<BoardSnapshot> before = r.snapshots();
            press(r.view, KeyCode.DELETE);
            assertNotEquals(before, r.snapshots(), "Delete exclui o card selecionado fora da apresentação");
            assertFalse(r.root.getCards().contains(r.beta));

            r.view.select(r.alfa);
            Point2D p = center(r.cardView(r.alfa).getBody());
            double x = r.alfa.getX();
            drag(r.cardView(r.alfa).getBody(), p, p.add(80, 0), MouseButton.PRIMARY);
            assertNotEquals(x, r.alfa.getX(), "arrastar move o card fora da apresentação");

            r.view.select(r.panel);
            press(r.view, KeyCode.L); // tecla de ferramenta
            assertEquals(Tool.DIAMOND, r.window.getToolBar().getActiveTool(), "letra de ferramenta troca a ferramenta");
        });
    }

    @Test
    void noKeyboardEventChangesTheBoard() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            List<BoardSnapshot> before = r.snapshots();
            boolean canUndoBefore = r.window.getUndoItem().isDisable();

            List<KeyCode> plain = new ArrayList<>(List.of(KeyCode.DELETE, KeyCode.BACK_SPACE, KeyCode.ENTER, KeyCode.TAB,
                    KeyCode.SPACE, KeyCode.F2, KeyCode.F3, KeyCode.HOME, KeyCode.END, KeyCode.UP, KeyCode.DOWN,
                    KeyCode.A, KeyCode.X, KeyCode.Z, KeyCode.DIGIT2));
            for (Tool tool : Tool.values()) {
                plain.add(tool.getShortcut()); // as letras de ferramenta
            }
            for (KeyCode code : plain) {
                assertTrue(press(r.view, code), code + " é consumido");
            }
            assertEquals(Tool.SELECT, r.window.getToolBar().getActiveTool(), "nenhuma letra trocou a ferramenta");

            for (KeyCode code : List.of(KeyCode.D, KeyCode.X, KeyCode.C, KeyCode.V, KeyCode.Z, KeyCode.Y, KeyCode.A,
                    KeyCode.L, KeyCode.F, KeyCode.N, KeyCode.O, KeyCode.S, KeyCode.E, KeyCode.OPEN_BRACKET,
                    KeyCode.CLOSE_BRACKET, KeyCode.PAGE_UP, KeyCode.PAGE_DOWN, KeyCode.DELETE, KeyCode.DIGIT1)) {
                assertTrue(press(r.view, code, true, false, false), "Ctrl+" + code + " é consumido");
                assertTrue(press(r.view, code, true, true, false), "Ctrl+Shift+" + code + " é consumido");
            }
            assertTrue(press(r.view, KeyCode.DIGIT2, false, true, false), "Shift+2 (ajustar à seleção)");
            assertTrue(fire(r.view, key(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, false, false, false)));

            assertEquals(before, r.snapshots(), "nada mudou em nenhum board da árvore");
            assertTrue(r.view.getSelection().isEmpty());
            assertFalse(r.main.getSearch().isOpen(), "Ctrl+F não abre a busca");
            assertEquals(canUndoBefore, r.window.getUndoItem().isDisable());
            assertTrue(r.presentation().isPresenting(), "e nenhuma dessas teclas saiu da apresentação");
        });
    }

    @Test
    void noKeyboardEventReachesATextFieldEither() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            TextArea text = textAreaOf(r.cardView(r.alfa));
            assertFalse(text.isEditable(), "o texto do card é só de leitura");
            List<BoardSnapshot> before = r.snapshots();

            for (KeyCode code : List.of(KeyCode.A, KeyCode.BACK_SPACE, KeyCode.DELETE, KeyCode.ENTER, KeyCode.SPACE)) {
                assertTrue(press(text, code), code + " no campo do card");
            }
            assertTrue(fire(text, key(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, false, false, false)));
            assertTrue(press(text, KeyCode.V, true, false, false), "Ctrl+V");
            assertTrue(press(text, KeyCode.A, true, false, false), "Ctrl+A");

            assertEquals("Alfa", r.alfa.getText());
            assertEquals(before, r.snapshots());
        });
    }

    @Test
    void noMouseEventChangesTheBoardNorSelectsNorEditsText() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            List<BoardSnapshot> before = r.snapshots();
            CardView alfa = r.cardView(r.alfa);
            Node body = alfa.getBody();
            TextArea text = textAreaOf(alfa);

            // clique simples e duplo no corpo e no texto, com Shift (somar à seleção), com Ctrl
            for (Node target : List.of(body, text)) {
                for (int clicks : List.of(1, 2, 3)) {
                    for (boolean consumed : click(target, center(target), MouseButton.PRIMARY, clicks)) {
                        assertTrue(consumed, "clique de " + clicks + " cliques em " + target.getClass().getSimpleName()
                                + " é consumido");
                    }
                }
            }
            assertFalse(text.isFocused(), "o texto não ganhou o foco");
            assertTrue(r.view.getSelection().isEmpty(), "nada se seleciona");

            // arrastar a alça de conexão de Alfa até Beta: não cria seta
            int arrows = r.root.getConnections().size();
            drag(alfa.getConnectorHandle(), center(alfa.getConnectorHandle()), center(r.cardView(r.beta).getBody()),
                    MouseButton.PRIMARY);
            assertEquals(arrows, r.root.getConnections().size());

            // ferramenta de criação ligada à força: clicar no fundo não cria card
            r.window.getToolBar().setActiveTool(Tool.RECTANGLE);
            int cards = r.root.getCards().size();
            click(r.view, new Point2D(5, 5), MouseButton.PRIMARY, 1);
            assertEquals(cards, r.root.getCards().size());
            r.window.getToolBar().setActiveTool(Tool.SELECT);

            // duplo clique no texto e na linha da seta: não abre a edição
            click(r.view.getConnectionView(r.connection).orElseThrow().getLabel(),
                    center(r.view.getConnectionView(r.connection).orElseThrow().getLabel()), MouseButton.PRIMARY, 2);
            assertFalse(r.view.getConnectionView(r.connection).orElseThrow().getLabelEditor().isVisible(),
                    "o texto da seta é só de leitura");
            assertNull(r.view.getSelectedConnection(), "a seta não fica selecionada");

            // caixa de seleção no fundo
            drag(r.view, new Point2D(2, 2), new Point2D(900, 600), MouseButton.PRIMARY);
            assertTrue(r.view.getSelection().isEmpty(), "nem a caixa de seleção seleciona");

            assertEquals(before, r.snapshots());
        });
    }

    @Test
    void rightClickDoesNotOpenAnyContextMenuAndContextMenuEventsAreConsumed() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            Node body = r.cardView(r.alfa).getBody();
            Point2D p = center(body);

            click(body, p, MouseButton.SECONDARY, 1);
            click(r.view, new Point2D(5, 5), MouseButton.SECONDARY, 1);
            assertFalse(r.main.getContextMenu().getMenu().isShowing(), "sem menu de clique direito");
            assertTrue(r.view.getSelection().isEmpty(), "o clique direito não seleciona o card");

            ContextMenuEvent menu = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, p.getX(), p.getY(),
                    p.getX(), p.getY(), false, new PickResult(body, p.getX(), p.getY()));
            assertTrue(fire(body, menu), "o pedido de menu de contexto é consumido");
        });
    }

    @Test
    void everyMenuItemIsDisabledExceptTheOnesThatDoNotEditAndFiringThemChangesNothing() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            List<BoardSnapshot> before = r.snapshots();

            for (var menu : r.window.getMenuBar().getMenus()) {
                for (MenuItem item : menu.getItems()) {
                    if (item != r.window.getPresentItem()) {
                        assertTrue(item.isDisable(), "'" + item.getText() + "' desabilitado na apresentação");
                    }
                }
            }
            // O pior caso: algo dispara os itens à força (o menu está escondido, mas o código pode chamá-los).
            for (MenuItem item : List.of(r.window.getUndoItem(), r.window.getRedoItem(), r.window.getCutItem(),
                    r.window.getCopyItem(), r.window.getPasteItem(), r.window.getSelectAllItem(),
                    r.window.getSearchItem(), r.window.getNewItem(), r.window.getOpenItem(),
                    r.window.getSaveItem(), r.window.getSaveAsItem(), r.window.getExportPdfItem(),
                    r.window.getExportPngItem(), r.window.getExportTransparentPngItem())) {
                item.fire(); // as ações do menu não fazem nada na apresentação (nem abrem diálogos)
            }
            assertEquals(before, r.snapshots());
            assertTrue(r.presentation().isPresenting(), "Novo e Abrir não derrubaram a apresentação nem trocaram o board");
            assertSame(r.root, r.main.getNavigation().getRoot());
            assertTrue(r.view.getSelection().isEmpty(), "Selecionar tudo não seleciona na apresentação");
            assertFalse(r.main.getSearch().isOpen(), "Buscar não abre a caixa na apresentação");
            for (MenuItem item : List.of(r.window.getResetViewItem(), r.window.getFitAllItem(),
                    r.window.getFitSelectionItem())) {
                item.fire(); // os de visão podem (só mexem na visão)
            }
            assertEquals(before, r.snapshots());

            r.presentation().stop();
            assertFalse(r.window.getCutItem().isDisable(), "fora da apresentação o menu volta");
            assertFalse(r.window.getSelectAllItem().isDisable());
            assertTrue(r.window.getUndoItem().isDisable(), "desfazer continua desabilitado: não há o que desfazer");
        });
    }

    @Test
    void theBoardViewRefusesSelectionAndReadOnlyCoversTextOfCardsCreatedLater() {
        onFx(() -> {
            Rig r = new Rig();
            r.presentation().start();
            r.view.select(r.alfa);
            r.view.setSelection(List.of(r.alfa, r.beta));
            r.view.selectAll();
            r.view.selectConnection(r.connection);
            assertTrue(r.view.getSelection().isEmpty(), "nem pela API");
            assertNull(r.view.getSelectedConnection());

            assertTrue(textAreaOf(r.cardView(r.alfa)).getStyleClass().contains("card-text"));
            r.cardView(r.alfa).focusText();
            assertFalse(textAreaOf(r.cardView(r.alfa)).isFocused(), "focusText não age em leitura");

            Card late = Rig.card(r.root, 900, 700, "Tardio");
            assertTrue(r.cardView(late).isReadOnly(), "card criado durante a apresentação já nasce em leitura");
            assertFalse(textAreaOf(r.cardView(late)).isEditable());

            r.presentation().stop();
            assertFalse(r.cardView(late).isReadOnly(), "ao sair, volta a editar");
            assertTrue(textAreaOf(r.cardView(r.alfa)).isEditable());
            r.view.select(r.alfa);
            assertTrue(r.view.getSelection().contains(r.alfa), "e a seleção volta a funcionar");
        });
    }

    @Test
    void nothingDoneDuringThePresentationEntersTheUndoHistory() {
        onFx(() -> {
            Rig r = new Rig();
            double x0 = r.alfa.getX();
            // Um passo de desfazer de verdade, feito antes de apresentar: arrastar o Alfa.
            r.view.select(r.alfa);
            Node body = r.cardView(r.alfa).getBody();
            drag(body, center(body), center(body).add(80, 0), MouseButton.PRIMARY);
            assertNotEquals(x0, r.alfa.getX());
            assertFalse(r.window.getUndoItem().isDisable(), "há um passo de desfazer antes de apresentar");

            r.presentation().start();
            press(r.view, KeyCode.RIGHT);               // slides
            press(r.view, KeyCode.RIGHT);
            press(r.view, KeyCode.LEFT, false, false, true); // voltar
            r.view.zoomAt(1.3, 50, 50);
            press(r.view, KeyCode.DIGIT1, false, true, false);
            r.presentation().stop();

            r.main.getNavigation().navigateTo(0);
            assertSame(r.root, r.current());
            assertFalse(r.window.getUndoItem().isDisable(), "o passo de antes continua lá");
            r.window.getUndoItem().fire();
            assertEquals(x0, r.alfa.getX(), "e é o único: desfaz exatamente o arrasto");
            assertTrue(r.window.getUndoItem().isDisable(),
                    "navegar, enquadrar e dar zoom na apresentação não criaram nenhum passo");
        });
    }

    @Test
    void presentationDoesNotTouchTheViewStateOfTheLevelsItLeavesBehind() {
        onFx(() -> {
            Rig r = new Rig();
            ViewState rootView = new ViewState(15, 25, 0.7);
            r.view.setViewState(rootView);

            r.presentation().start();
            press(r.view, KeyCode.RIGHT); // vai ao board do Projeto
            r.presentation().stop();

            assertSame(r.project.getChildBoard(), r.current(), "sair fica no board em que a apresentação terminou");
            r.layout(); // a janela volta ao tamanho de antes (menu e barra de status ocupam lugar de novo)
            ViewState afterExit = r.view.getViewState();
            assertNotEquals(rootView, afterExit);
            r.view.fitAll();
            assertEquals(afterExit, r.view.getViewState(),
                    "o board visitado só na apresentação continua enquadrado, agora com as folgas normais");

            r.main.getNavigation().back(); // volta à origem do pulo
            assertSame(r.root, r.current());
            assertEquals(rootView, r.view.getViewState(), "o board de onde se entrou volta exatamente como estava");
        });
    }

    @Test
    void exitingOnTheBoardWhereItStartedRestoresTheExactViewEvenAfterNavigating() {
        onFx(() -> {
            Rig r = new Rig();
            ViewState rootView = new ViewState(-20, 40, 1.3);
            r.view.setViewState(rootView);

            r.presentation().start();
            press(r.view, KeyCode.RIGHT);
            press(r.view, KeyCode.LEFT);
            r.view.zoomAt(1.5, 100, 100);
            r.view.panBy(77, 12);
            assertSame(r.root, r.current());
            r.presentation().stop();

            assertSame(r.root, r.current());
            assertEquals(rootView, r.view.getViewState());
        });
    }

    // ------------------------------------------------------------------ o que é "navegação"

    @Test
    void onlyNavigationNodesTakeThePrimaryClickInThePresentation() {
        onFx(() -> {
            Rig r = new Rig();
            CardView project = r.cardView(r.project);
            CardView linked = r.cardView(r.link);
            CardView notes = r.cardView(r.notes);
            // navegação: botão de entrar no board, botão do painel, marcadores de link, caminho do topo, ✕ do painel
            assertTrue(PresentationController.isNavigationTarget(project.getBoardButton()));
            assertTrue(PresentationController.isNavigationTarget(notes.getPanelButton()));
            assertTrue(PresentationController.isNavigationTarget(linked.getMarker(CardMarkers.Kind.LINK)));
            assertTrue(PresentationController.isNavigationTarget(r.cardView(r.shortcut).getMarker(CardMarkers.Kind.BOARD_LINK)));
            assertTrue(PresentationController.isNavigationTarget(r.window.getBreadcrumb().getBackButton()));
            assertTrue(PresentationController.isNavigationTarget(r.window.getBreadcrumb()));
            Node icon = project.getBoardButton().getGraphic();
            assertTrue(PresentationController.isNavigationTarget(icon), "o ícone dentro do botão também");

            FloatingPanelView editable = new FloatingPanelView(r.panel); // painel de edição, só para classificar
            r.view.getChildren().add(editable);
            r.layout();
            assertTrue(PresentationController.isNavigationTarget(editable.lookup(".card-panel-close")), "o ✕ do painel");
            assertFalse(PresentationController.isNavigationTarget(editable.lookup(".card-panel-add")), "adicionar item");
            assertFalse(PresentationController.isNavigationTarget(editable.lookup(".card-panel-new-item")), "campo novo item");
            assertFalse(PresentationController.isNavigationTarget(editable.lookup(".card-panel-item")), "item");
            r.view.getChildren().remove(editable);

            // o resto: não é navegação (o clique só move a visão)
            assertFalse(PresentationController.isNavigationTarget(project.getBody()));
            assertFalse(PresentationController.isNavigationTarget(textAreaOf(project)));
            assertFalse(PresentationController.isNavigationTarget(project.getConnectorHandle()));
            assertFalse(PresentationController.isNavigationTarget(r.view));
            assertFalse(PresentationController.isNavigationTarget(r.window.getToolBar()));
            assertFalse(PresentationController.isNavigationTarget(null));
            assertFalse(PresentationController.isNavigationTarget(r.scene));
        });
    }

    @Test
    void startClosesTheSearchTheOpenPanelAndKeepsNoLeftoverUi() {
        onFx(() -> {
            Rig r = new Rig();
            r.main.getSearch().open();
            assertTrue(r.main.getSearch().isOpen());
            click(r.cardView(r.notes).getPanelButton()); // fora da apresentação: abre o painel de edição
            r.layout();
            assertNotNull(r.openPanel());
            assertFalse(r.openPanel().isReadOnly());

            r.presentation().start();

            assertFalse(r.main.getSearch().isOpen(), "a caixa de busca fecha");
            assertNull(r.openPanel(), "o painel de edição fecha (reabre em leitura se clicarem de novo)");
            click(r.cardView(r.notes).getPanelButton());
            r.layout();
            assertTrue(r.openPanel().isReadOnly());
            r.presentation().stop();
            assertNull(r.openPanel());
        });
    }

    @Test
    void thePresentationFitUsesSmallerMarginsThanTheNormalFit() {
        onFx(() -> {
            Rig r = new Rig();
            r.view.fitAll();
            ViewState normal = r.view.getViewState();
            r.view.fitAllForPresentation();
            ViewState presenting = r.view.getViewState();
            assertTrue(presenting.zoom() >= normal.zoom(),
                    "sem a barra de ferramentas à esquerda sobra mais lugar: o zoom não é menor");
            assertNotEquals(normal, presenting);
        });
    }

    @Test
    void anEmptyBoardIsShownAtOneHundredPercentWithoutBreaking() {
        onFx(() -> {
            Rig r = new Rig();
            r.main.newBoard();
            r.presentation().start();
            assertEquals(ViewState.DEFAULT, r.view.getViewState(), "board vazio: sem o que enquadrar");
            press(r.view, KeyCode.RIGHT);
            assertFalse(r.presentation().nextSlide(), "um board só: sem próximo");
            assertEquals(PresentationController.LAST_NOTICE, r.window.getPresentationNotice());
            r.presentation().stop();
            assertFalse(r.presentation().isPresenting());
        });
    }

    @Test
    void startingInsideANestedBoardPresentsThatBoardAndTheSlidesContinueFromIt() {
        onFx(() -> {
            Rig r = new Rig();
            r.main.getNavigation().enter(r.project); // o board da tela é o filho do "Projeto"
            r.layout();
            ViewState nestedView = new ViewState(11, 22, 0.75);
            r.view.setViewState(nestedView);
            assertSame(r.project.getChildBoard(), r.current());

            r.presentation().start();
            assertSame(r.project.getChildBoard(), r.current(), "entra no board que está na tela, não no principal");
            assertSame(r.project.getChildBoard(), r.view.getBoard());
            assertNotEquals(nestedView, r.view.getViewState(), "e o enquadra");

            press(r.view, KeyCode.RIGHT);
            assertSame(r.deep.getChildBoard(), r.current(), "o próximo é o do card 'Fundo' (ordem do PDF a partir deste board)");
            press(r.view, KeyCode.LEFT);
            assertSame(r.project.getChildBoard(), r.current());
            press(r.view, KeyCode.LEFT);
            assertSame(r.root, r.current(), "o anterior do primeiro filho é o principal");
            press(r.view, KeyCode.RIGHT);
            assertSame(r.project.getChildBoard(), r.current());

            press(r.view, KeyCode.ESCAPE);
            assertFalse(r.presentation().isPresenting());
            assertSame(r.project.getChildBoard(), r.current(), "sai no board em que terminou");
            assertEquals(nestedView, r.view.getViewState(), "e com a visão que ele tinha antes de apresentar");
            assertEquals(List.of("Sem título", "Projeto"), r.main.getNavigation().pathNames(),
                    "o caminho do topo continua o do board aninhado");
        });
    }
}
