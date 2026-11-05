package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardNames;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BoardView.ViewState;
import com.danilo.boardvisual.view.BreadcrumbView;
import com.danilo.boardvisual.view.CardView;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableStringValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Navegação entre boards aninhados.
 *
 * Mantém o caminho do board principal até o board na tela (uma pilha de
 * níveis, {@link NavigationPath}). Entrar num card empilha; voltar ou clicar
 * num nível do caminho desempilha até ele. Cada nível lembra a própria
 * posição e zoom.
 *
 * O "link para outro board" pula para qualquer board da árvore
 * ({@link #jumpTo}): o caminho no topo passa a ser o caminho real do destino,
 * e o voltar (botão ← e Alt+←) leva de volta ao board de onde se saiu (regras
 * em {@link NavigationPath}).
 *
 * O board principal é o que é salvo em arquivo: os filhos vão dentro dele.
 */
public class NavigationController {

    private final BoardView view;
    private final BreadcrumbView breadcrumb;
    private final EditHistory history;
    private final NavigationPath<ViewState> path = new NavigationPath<>(ViewState.DEFAULT);

    /** Chamado depois de cada troca do board da tela. */
    private Runnable onBoardShown = () -> { };
    /** Outros interessados na troca do board da tela (ex.: a busca), além do {@link #setOnBoardShown}. */
    private final List<Runnable> boardShownListeners = new ArrayList<>();

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

    /** Quem precisa saber que o board da tela mudou (ex.: conferir os links para outros boards). */
    public void setOnBoardShown(Runnable listener) {
        onBoardShown = listener == null ? () -> { } : listener;
    }

    /** Mais um interessado em saber que o board da tela mudou (não substitui os que já existem). */
    public void addOnBoardShown(Runnable listener) {
        boardShownListeners.add(listener);
    }

    /** Começa do zero com um novo board principal (novo arquivo ou arquivo aberto). */
    public void openRoot(Board root) {
        path.openRoot(root);
        show();
    }

    public Board getRoot() {
        return path.root();
    }

    /** O board que está na tela (o principal ou um aninhado). */
    public Board getCurrent() {
        return path.isEmpty() ? null : path.current().board();
    }

    /** Nomes do caminho, do board principal até o da tela (os mesmos do caminho no topo). */
    public List<String> pathNames() {
        return path.levels().stream().map(level -> nameOf(level).get()).toList();
    }

    public void enter(Card card) {
        change(() -> path.enter(card));
    }

    /**
     * Volta: ao board de onde se saiu, se o board da tela foi aberto por um
     * link para outro board; senão, ao pai. Retorna false se não havia para onde.
     */
    public boolean back() {
        return change(path::back);
    }

    /** Vai direto para um nível do caminho (0 = board principal). */
    public void navigateTo(int index) {
        change(() -> path.navigateTo(index));
    }

    /** Vai para o board com esse id, em qualquer lugar da árvore (link para outro board). */
    public NavigationPath.JumpResult jumpTo(String boardId) {
        NavigationPath.JumpResult[] result = new NavigationPath.JumpResult[1];
        change(() -> {
            result[0] = path.jumpTo(boardId);
            return result[0] == NavigationPath.JumpResult.DONE;
        });
        return result[0];
    }

    /** Guarda a visão do nível atual, aplica a mudança e, se algo mudou, mostra o novo nível. */
    private boolean change(BooleanSupplier action) {
        if (path.isEmpty()) {
            return false;
        }
        path.current().setViewState(view.getViewState());
        boolean changed = action.getAsBoolean();
        if (changed) {
            show();
        }
        return changed;
    }

    private void show() {
        NavigationPath.Level<ViewState> level = path.current();
        view.requestFocus(); // encerra uma edição de texto em andamento no board que está saindo
        view.setBoard(level.board());
        view.setViewState(level.viewState());
        history.boardChanged();
        breadcrumb.setPath(path.levels().stream().map(this::nameOf).toList());
        breadcrumb.setBack(path.canGoBack(), backTooltip());
        onBoardShown.run();
        boardShownListeners.forEach(Runnable::run);
    }

    private String backTooltip() {
        if (path.backReturnsToJumpOrigin()) {
            List<NavigationPath.Level<ViewState>> origin = path.current().returnPath();
            return "Voltar para \"" + nameOf(origin.get(origin.size() - 1)).get() + "\", de onde você veio  (Alt+←)";
        }
        return "Voltar ao board anterior  (Alt+←)";
    }

    /** Nome exibido no caminho (regra em {@link BoardNames}). */
    private ObservableStringValue nameOf(NavigationPath.Level<ViewState> level) {
        Board board = level.board();
        Card card = level.card();
        if (card == null) {
            return Bindings.createStringBinding(() -> BoardNames.ofRoot(board), board.nameProperty());
        }
        return Bindings.createStringBinding(() -> BoardNames.ofChild(card), card.textProperty());
    }
}
