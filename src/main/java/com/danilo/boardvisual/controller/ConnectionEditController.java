package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.ConnectionView;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Selecionar, excluir e escrever o texto das setas.
 *
 * - Clique na seta (na linha, que tem uma área de clique larga e invisível, ou
 *   no texto dela): seleciona a seta (destaque azul) e tira a seleção dos cards.
 *   A área larga só existe com a ferramenta Selecionar (CSS); o texto, sempre.
 * - Delete / Backspace (pelo {@link KeyboardController}): exclui a seta selecionada.
 * - Esc: limpa a seleção (a de cards e a de seta), como antes.
 * - Duplo clique na seta ou no texto: abre um campo no meio dela com o texto
 *   atual. Enter confirma, Esc cancela; clicar fora (perder o foco) também
 *   confirma, como no texto dos cards.
 *
 * - Com a seta selecionada, os botões de estilo da barra de ferramentas
 *   (ao lado da ferramenta Conexão) mudam o estilo dela ({@link #applyStyleToSelected}).
 *
 * Cada confirmação que muda o texto é um passo do desfazer; trocar o estilo também
 * ({@link EditHistory#perform}); a exclusão também. A seleção é estado só da
 * tela ({@link BoardView#selectConnection}), fora do desfazer.
 */
public class ConnectionEditController {

    private final BoardView view;
    private final EditHistory history;

    /** Seta cujo texto está sendo editado, ou {@code null}. */
    private ConnectionView editing;

    public ConnectionEditController(BoardView view, EditHistory history) {
        this.view = view;
        this.history = history;
    }

    public void attach(ConnectionView connectionView) {
        for (Node clickable : new Node[]{connectionView.getHitLine(), connectionView.getLabel()}) {
            clickable.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                view.requestFocus(); // conclui uma edição de texto em andamento (card ou outra seta)
                view.selectConnection(connectionView.getConnection());
                e.consume();
            });
            clickable.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    editLabel(connectionView);
                    e.consume();
                }
            });
        }

        TextField editor = connectionView.getLabelEditor();
        // Filtro: Enter e Esc são do campo; não chegam aos atalhos da janela.
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                finish(connectionView, true);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                finish(connectionView, false);
                e.consume();
            }
        });
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                finish(connectionView, true); // clicou fora: confirma
            }
        });
    }

    /** Abre o campo de texto da seta (duplo clique). */
    public void editLabel(ConnectionView connectionView) {
        if (view.isPresenting()) {
            return; // na apresentação o texto da seta é só de leitura
        }
        if (editing != null && editing != connectionView) {
            finish(editing, true);
        }
        view.selectConnection(connectionView.getConnection());
        editing = connectionView;
        connectionView.showLabelEditor();
        TextField editor = connectionView.getLabelEditor();
        editor.requestFocus();
        editor.selectAll();
    }

    /** Abre o campo de texto da seta informada, se ela está na tela (usado pelos roteiros de teste). */
    public void editLabel(Connection connection) {
        view.getConnectionView(connection).ifPresent(this::editLabel);
    }

    /**
     * Fecha o campo. Com {@code commit}, o texto digitado (sem espaços nas
     * pontas) vira o texto da seta num passo do desfazer; sem, nada muda.
     */
    private void finish(ConnectionView connectionView, boolean commit) {
        if (editing != connectionView) {
            return;
        }
        editing = null; // antes de mexer no foco: o aviso de "perdeu o foco" não fecha de novo
        TextField editor = connectionView.getLabelEditor();
        String text = editor.getText() == null ? "" : editor.getText().strip();
        if (editor.isFocused()) {
            view.requestFocus(); // Enter/Esc: o foco volta ao canvas (se clicou fora, fica onde foi)
        }
        connectionView.hideLabelEditor();
        Connection connection = connectionView.getConnection();
        if (commit && !text.equals(connection.getLabel())) {
            history.perform(() -> connection.setLabel(text));
        }
    }

    /** Se o campo de texto de alguma seta está aberto. */
    public boolean isEditing() {
        return editing != null;
    }

    /**
     * Aplica o estilo na seta selecionada, num passo do desfazer (nenhum passo
     * se ela já tem esse estilo). Chamado pela barra de ferramentas.
     *
     * @return se havia uma seta selecionada (aí o botão de estilo não ativa a ferramenta Conexão)
     */
    public boolean applyStyleToSelected(ConnectionStyle style) {
        Connection connection = view.getSelectedConnection();
        if (connection == null || style == null) {
            return false;
        }
        if (connection.getStyle() != style) {
            history.perform(() -> connection.setStyle(style));
        }
        return true;
    }

    /**
     * Exclui a seta selecionada num passo do desfazer.
     *
     * @return se havia uma seta selecionada (aí a tecla Delete não age nos cards)
     */
    public boolean deleteSelected() {
        Connection connection = view.getSelectedConnection();
        Board board = view.getBoard();
        if (connection == null || board == null) {
            return false;
        }
        history.perform(() -> board.removeConnection(connection));
        return true;
    }
}
