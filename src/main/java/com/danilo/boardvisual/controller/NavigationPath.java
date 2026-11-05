package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardTree;
import com.danilo.boardvisual.model.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pilha de níveis da navegação entre boards, sem tela (testável): do board
 * principal até o board que está na tela. O {@link NavigationController} usa
 * esta classe e cuida só de mostrar o resultado.
 *
 * <ul>
 *   <li><b>Entrar</b> num card-board empilha um nível; <b>ir a um nível</b> do
 *       caminho desempilha até ele.</li>
 *   <li><b>Pular</b> para um board qualquer da árvore ("link para outro board",
 *       {@link #jumpTo}) troca a pilha inteira pelo caminho real do destino
 *       ({@link BoardTree#pathTo}) e guarda <b>de onde se saiu</b> no nível de
 *       chegada ({@link Level#returnPath()}).</li>
 *   <li><b>Voltar</b>: no nível de chegada de um pulo, volta para o ponto de
 *       partida (a pilha inteira de antes do pulo); em qualquer outro nível,
 *       volta ao pai, como sempre. Entrar num filho a partir do destino e voltar
 *       traz de novo ao destino, e voltar mais uma vez leva à origem. Clicar num
 *       nível acima do destino, no caminho do topo, descarta a volta à origem
 *       (o nível de chegada saiu da pilha).</li>
 * </ul>
 *
 * Cada nível lembra a própria visão ({@code S}: na aplicação, pan e zoom);
 * quem chama guarda a visão do nível atual antes de cada mudança.
 *
 * @param <S> o tipo da visão guardada por nível
 */
public final class NavigationPath<S> {

    /** Um nível do caminho. {@code card} é o card que contém o board ({@code null} no principal). */
    public static final class Level<S> {
        private final Board board;
        private final Card card;
        private S viewState;
        /** Pilha de antes do pulo que chegou a este nível ({@code null} = não é chegada de pulo). */
        private List<Level<S>> returnPath;

        Level(Board board, Card card, S viewState) {
            this.board = board;
            this.card = card;
            this.viewState = viewState;
        }

        public Board board() {
            return board;
        }

        public Card card() {
            return card;
        }

        public S viewState() {
            return viewState;
        }

        public void setViewState(S viewState) {
            this.viewState = viewState;
        }

        /** Pilha de antes do pulo que chegou aqui, ou {@code null}. */
        public List<Level<S>> returnPath() {
            return returnPath;
        }
    }

    /** Resultado de {@link #jumpTo}. */
    public enum JumpResult {
        /** Foi para o destino. */
        DONE,
        /** O destino é o próprio board da tela: nada muda. */
        SAME_BOARD,
        /** Não há board com esse id na árvore (o card que o continha foi excluído). */
        NOT_FOUND
    }

    private final S defaultViewState;
    private final List<Level<S>> levels = new ArrayList<>();

    public NavigationPath(S defaultViewState) {
        this.defaultViewState = defaultViewState;
    }

    /** Começa do zero no board principal (novo arquivo ou arquivo aberto). */
    public void openRoot(Board root) {
        levels.clear();
        levels.add(new Level<>(Objects.requireNonNull(root, "root"), null, defaultViewState));
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    /** Os níveis, do principal até o da tela (somente leitura). */
    public List<Level<S>> levels() {
        return Collections.unmodifiableList(levels);
    }

    public Level<S> current() {
        return levels.get(levels.size() - 1);
    }

    public Board root() {
        return levels.isEmpty() ? null : levels.get(0).board;
    }

    /** Entra no board filho do card. Retorna false se não houve mudança. */
    public boolean enter(Card card) {
        if (levels.isEmpty() || card == null || !card.hasChildBoard()) {
            return false;
        }
        levels.add(new Level<>(card.getChildBoard(), card, defaultViewState));
        return true;
    }

    /** Vai direto para um nível do caminho (0 = principal). Retorna false se não houve mudança. */
    public boolean navigateTo(int index) {
        if (index < 0 || index >= levels.size() - 1) {
            return false;
        }
        levels.subList(index + 1, levels.size()).clear();
        return true;
    }

    /** Se há para onde voltar (o pai, ou a origem de um pulo). */
    public boolean canGoBack() {
        return !levels.isEmpty() && (levels.size() > 1 || current().returnPath != null);
    }

    /** Se voltar agora leva ao ponto de partida de um pulo (e não ao pai). */
    public boolean backReturnsToJumpOrigin() {
        return !levels.isEmpty() && current().returnPath != null;
    }

    /**
     * Volta: à origem do pulo, se o nível atual é a chegada de um; senão, ao pai.
     * Se a origem não existe mais (o card que a continha foi excluído), volta ao pai.
     *
     * @return false se não houve mudança (já no principal, sem pulo)
     */
    public boolean back() {
        if (levels.isEmpty()) {
            return false;
        }
        List<Level<S>> origin = current().returnPath;
        if (origin != null) {
            List<Level<S>> restored = revalidate(origin);
            if (restored != null) {
                levels.clear();
                levels.addAll(restored);
                return true;
            }
            current().returnPath = null;
        }
        return navigateTo(levels.size() - 2);
    }

    /**
     * Vai para o board com esse id, em qualquer lugar da árvore, montando a
     * pilha com o caminho real até ele (do principal ao destino). Os níveis
     * que já estavam na pilha (ex.: o principal) mantêm a visão guardada; o
     * nível de chegada guarda a pilha de antes, para o "voltar".
     */
    public JumpResult jumpTo(String boardId) {
        List<BoardTree.Step> steps = BoardTree.pathTo(root(), boardId);
        if (steps.isEmpty()) {
            return JumpResult.NOT_FOUND;
        }
        if (steps.get(steps.size() - 1).board() == current().board) {
            return JumpResult.SAME_BOARD;
        }
        List<Level<S>> before = List.copyOf(levels);
        Map<Board, S> views = new HashMap<>();
        before.forEach(level -> views.put(level.board, level.viewState));

        levels.clear();
        for (BoardTree.Step step : steps) {
            levels.add(new Level<>(step.board(), step.card(), views.getOrDefault(step.board(), defaultViewState)));
        }
        current().returnPath = before;
        return JumpResult.DONE;
    }

    /**
     * A pilha guardada, se ainda corresponde à árvore (cada board ainda está no
     * card que o continha); senão, o caminho atual até o mesmo board (pelo id:
     * desfazer uma exclusão recria cards e boards como objetos novos, com os
     * mesmos ids), mantendo a visão e a volta de cada nível. {@code null} se o
     * board de origem não existe mais.
     */
    private List<Level<S>> revalidate(List<Level<S>> saved) {
        if (isIntact(saved)) {
            return saved;
        }
        Board target = saved.get(saved.size() - 1).board;
        List<BoardTree.Step> steps = BoardTree.pathTo(root(), target.getId());
        if (steps.isEmpty()) {
            return null;
        }
        Map<String, Level<S>> byId = new HashMap<>();
        saved.forEach(level -> byId.put(level.board.getId(), level));
        List<Level<S>> rebuilt = new ArrayList<>();
        for (BoardTree.Step step : steps) {
            Level<S> old = byId.get(step.board().getId());
            Level<S> level = new Level<>(step.board(), step.card(), old == null ? defaultViewState : old.viewState);
            level.returnPath = old == null ? null : old.returnPath;
            rebuilt.add(level);
        }
        return rebuilt;
    }

    private boolean isIntact(List<Level<S>> saved) {
        if (saved.isEmpty() || saved.get(0).board != root()) {
            return false;
        }
        for (int i = 1; i < saved.size(); i++) {
            Level<S> level = saved.get(i);
            if (!saved.get(i - 1).board.getCards().contains(level.card) || level.card.getChildBoard() != level.board) {
                return false;
            }
        }
        return true;
    }
}
