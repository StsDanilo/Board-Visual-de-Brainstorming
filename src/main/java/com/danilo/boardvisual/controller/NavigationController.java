package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BoardView.ViewState;
import com.danilo.boardvisual.view.BreadcrumbView;
import com.danilo.boardvisual.view.CardView;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableStringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Navegação entre boards aninhados.
 *
 * Mantém o caminho do board principal até o board na tela (uma pilha de
 * níveis). Entrar num card empilha; voltar ou clicar num nível do caminho
 * desempilha até ele. Cada nível lembra a própria posição e zoom.
 *
 * O board principal é o que é salvo em arquivo: os filhos vão dentro dele.
 */
public class NavigationController {

    private static final String UNTITLED = "Sem título";

    private final BoardView view;
    private final BreadcrumbView breadcrumb;
    private final EditHistory history;

    /** Um nível do caminho. {@code card} é o card que contém o board ({@code null} no principal). */
    private static final class Level {
        final Board board;
        final Card card;
        ViewState viewState = ViewState.DEFAULT;

        Level(Board board, Card card) {
            this.board = board;
            this.card = card;
        }
    }

    private final List<Level> path = new ArrayList<>();

    public NavigationController(BoardView view, BreadcrumbView breadcrumb, EditHistory history) {
        this.view = view;
        this.breadcrumb = breadcrumb;
        this.history = history;
        breadcrumb.setOnNavigate(this::navigateTo);
        breadcrumb.getBackButton().setOnAction(e -> back());
    }

    public void attach(CardView cardView) {
        cardView.getBoardButton().setOnAction(e -> enter(cardView.getCard()));
    }

    /** Começa do zero com um novo board principal (novo arquivo ou arquivo aberto). */
    public void openRoot(Board root) {
        path.clear();
        path.add(new Level(root, null));
        show(path.get(0));
    }

    public Board getRoot() {
        return path.isEmpty() ? null : path.get(0).board;
    }

    public void enter(Card card) {
        if (!card.hasChildBoard() || path.isEmpty()) {
            return;
        }
        currentLevel().viewState = view.getViewState();
        Level level = new Level(card.getChildBoard(), card);
        path.add(level);
        show(level);
    }

    /** Volta um nível. Retorna false se já estiver no board principal. */
    public boolean back() {
        if (path.size() < 2) {
            return false;
        }
        navigateTo(path.size() - 2);
        return true;
    }

    /** Vai direto para um nível do caminho (0 = board principal). */
    public void navigateTo(int index) {
        if (index < 0 || index >= path.size() - 1) {
            return;
        }
        path.subList(index + 1, path.size()).clear();
        show(path.get(index));
    }

    private Level currentLevel() {
        return path.get(path.size() - 1);
    }

    private void show(Level level) {
        view.requestFocus(); // encerra uma edição de texto em andamento no board que está saindo
        view.setBoard(level.board);
        view.setViewState(level.viewState);
        history.boardChanged();
        breadcrumb.setPath(path.stream().map(this::nameOf).toList());
    }

    /** Nome exibido no caminho: o do arquivo no principal; o texto do card nos filhos. */
    private ObservableStringValue nameOf(Level level) {
        if (level.card == null) {
            return Bindings.createStringBinding(() -> orUntitled(level.board.getName()), level.board.nameProperty());
        }
        return Bindings.createStringBinding(() -> orUntitled(firstLine(level.card.getText())), level.card.textProperty());
    }

    private static String firstLine(String text) {
        String stripped = text == null ? "" : text.strip();
        int lineBreak = stripped.indexOf('\n');
        return lineBreak < 0 ? stripped : stripped.substring(0, lineBreak).strip();
    }

    private static String orUntitled(String name) {
        return name == null || name.isBlank() ? UNTITLED : name;
    }
}
