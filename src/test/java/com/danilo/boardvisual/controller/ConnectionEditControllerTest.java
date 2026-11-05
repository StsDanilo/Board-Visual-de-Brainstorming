package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.ConnectionView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Texto e exclusão de setas pelo controller, num BoardView de verdade (sem
 * janela): Enter confirma num passo do desfazer, Esc cancela, Delete exclui a
 * seta selecionada (e o desfazer a traz de volta com texto e estilo).
 */
class ConnectionEditControllerTest {

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

    private final Board board = new Board("setas");
    private final EditHistory history = new EditHistory(() -> board);
    private final Card a = new Card("a", 0, 0);
    private final Card b = new Card("b", 400, 0);
    private Connection connection;
    private BoardView view;
    private ConnectionEditController controller;

    private void setUp() {
        board.addCard(a);
        board.addCard(b);
        connection = board.connect(a, b, ConnectionStyle.DOUBLE).orElseThrow();
        view = new BoardView();
        controller = new ConnectionEditController(view, history);
        view.setConnectionViewInitializer(controller::attach);
        view.setBoard(board);
        new Scene(view, 800, 400);
    }

    private ConnectionView connectionView() {
        return view.getConnectionView(connection).orElseThrow();
    }

    private void type(String text, KeyCode key) {
        ConnectionView cv = connectionView();
        cv.getLabelEditor().setText(text);
        Event.fireEvent(cv.getLabelEditor(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key,
                false, false, false, false));
    }

    @Test
    void enterConfirmsTheLabelAsOneUndoStep() throws Exception {
        onFx(() -> {
            setUp();
            controller.editLabel(connection);
            assertTrue(connectionView().isEditingLabel());
            assertEquals(connection, view.getSelectedConnection(), "editar seleciona a seta");

            type("  leva a  ", KeyCode.ENTER);

            assertFalse(connectionView().isEditingLabel());
            assertEquals("leva a", connection.getLabel(), "sem os espaços das pontas");
            history.undo();
            assertEquals("", connection.getLabel());
            history.redo();
            assertEquals("leva a", connection.getLabel());
            return null;
        });
    }

    @Test
    void escapeCancelsWithoutAnUndoStep() throws Exception {
        onFx(() -> {
            setUp();
            connection.setLabel("original");
            controller.editLabel(connection);
            assertEquals("original", connectionView().getLabelEditor().getText(), "o campo abre com o texto atual");

            type("outro", KeyCode.ESCAPE);

            assertFalse(connectionView().isEditingLabel());
            assertEquals("original", connection.getLabel());
            assertFalse(history.canUndoProperty().get());
            return null;
        });
    }

    @Test
    void emptyTextRemovesTheLabel() throws Exception {
        onFx(() -> {
            setUp();
            connection.setLabel("depende de");
            controller.editLabel(connection);
            type("", KeyCode.ENTER);
            assertEquals("", connection.getLabel());
            assertFalse(connectionView().getLabel().isVisible(), "seta sem texto: nada aparece");
            return null;
        });
    }

    @Test
    void editorRefusesTextOverTheLimit() throws Exception {
        onFx(() -> {
            setUp();
            controller.editLabel(connection);
            var editor = connectionView().getLabelEditor();
            editor.setText("x".repeat(Connection.MAX_LABEL_LENGTH));
            editor.appendText("y");
            assertEquals(Connection.MAX_LABEL_LENGTH, editor.getText().length());
            return null;
        });
    }

    @Test
    void deleteRemovesTheSelectedConnectionAndUndoBringsItBack() throws Exception {
        onFx(() -> {
            setUp();
            connection.setLabel("depende de");
            assertFalse(controller.deleteSelected(), "nada selecionado: Delete fica para os cards");

            view.selectConnection(connection);
            assertTrue(controller.deleteSelected());
            assertTrue(board.getConnections().isEmpty());
            assertNull(view.getSelectedConnection());
            assertEquals(2, board.getCards().size(), "os cards ficam");

            history.undo();
            Connection back = board.findConnection(connection.getId()).orElseThrow();
            assertEquals("depende de", back.getLabel());
            assertEquals(ConnectionStyle.DOUBLE, back.getStyle());
            assertTrue(view.getConnectionView(back).isPresent());
            return null;
        });
    }

    @Test
    void styleButtonChangesTheSelectedConnectionAsOneUndoStep() throws Exception {
        onFx(() -> {
            setUp();
            assertFalse(controller.applyStyleToSelected(ConnectionStyle.LINE), "sem seta selecionada: não age");
            assertEquals(ConnectionStyle.DOUBLE, connection.getStyle());

            view.selectConnection(connection);
            assertTrue(controller.applyStyleToSelected(ConnectionStyle.DOUBLE), "o mesmo estilo: consome, sem passo");
            assertFalse(history.canUndoProperty().get());
            assertTrue(controller.applyStyleToSelected(ConnectionStyle.LINE));
            assertEquals(ConnectionStyle.LINE, connection.getStyle());

            history.undo();
            assertEquals(ConnectionStyle.DOUBLE, connection.getStyle());
            history.redo();
            assertEquals(ConnectionStyle.LINE, connection.getStyle());
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
