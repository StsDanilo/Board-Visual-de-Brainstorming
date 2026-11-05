package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSearch;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.SearchResults;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.SearchBarView;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Busca de texto (Ctrl+F): abre a {@link SearchBarView}, acha os cards e as setas que
 * casam ({@link BoardSearch}), destaca-os no board da tela e leva a visão até eles.
 *
 * <ul>
 *   <li><b>Digitar</b> refaz a busca e destaca os que casam (o atual, mais forte). Se o atual
 *       está no board da tela e fora da área visível, a visão vai até ele; se já aparece, não mexe.</li>
 *   <li><b>Enter / Shift+Enter</b> (ou os botões) vão ao próximo / anterior, dando a volta, e
 *       <b>centralizam</b> o resultado sem mudar o zoom (abaixo de 50% o zoom sobe a 50%, que é o
 *       mínimo em que o texto se lê). Se o atual ainda não foi visto (está em outro board), o Enter
 *       vai nele primeiro. A busca é refeita a cada passo: o que mudou no board desde então entra.</li>
 *   <li><b>Em todos os boards</b>: acha também nos boards dentro dos cards; a lista mostra o caminho
 *       ({@code Principal › board 2}), e ir a um resultado de outro board usa o {@code jumpTo} do link
 *       para outro board ({@link NavigationController#jumpTo}): o voltar (← e Alt+←) leva ao board de
 *       onde se saiu, como nos links.</li>
 *   <li><b>Esc</b> fecha a busca e tira os destaques. Dentro do campo as teclas são do texto
 *       (o {@code KeyboardController} só age fora de campos de texto).</li>
 * </ul>
 *
 * Tudo isso é só da tela: o modelo não muda e nada entra no desfazer. Os destaques são feitos
 * no momento da busca: editar um texto com a busca aberta não os atualiza até o próximo passo.
 *
 * <b>Ctrl+F durante a edição de texto</b> (de um card, de um item de painel, do texto de uma seta):
 * não abre a busca (as teclas são do texto) e só avisa na barra de status; o item "Buscar..." do menu
 * Editar abre sempre (o clique do mouse encerra a edição ao mover o foco para o campo da busca).
 */
public class SearchController {

    private final BoardView view;
    private final SearchBarView bar;
    private final NavigationController navigation;
    private final SearchResults results = new SearchResults();
    private Consumer<String> notifier = message -> { };

    public SearchController(BoardView view, SearchBarView bar, NavigationController navigation) {
        this.view = view;
        this.bar = bar;
        this.navigation = navigation;

        bar.getField().textProperty().addListener((obs, old, text) -> queryChanged());
        bar.getAllBoardsBox().selectedProperty().addListener((obs, old, all) -> queryChanged());
        bar.getNextButton().setOnAction(e -> next());
        bar.getPreviousButton().setOnAction(e -> previous());
        bar.getCloseButton().setOnAction(e -> close());
        bar.setOnPick(this::pick);
        bar.getField().addEventFilter(KeyEvent.KEY_PRESSED, this::onFieldKey);
        navigation.addOnBoardShown(this::boardShown);
    }

    /** Quem mostra os avisos curtos (a barra de status). */
    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier == null ? message -> { } : notifier;
    }

    /**
     * Instala a guarda do Ctrl+F na janela: com o texto de um card em edição (ou de qualquer
     * campo, menos o da própria busca), o Ctrl+F não abre a busca nem aciona o menu.
     */
    public void installOn(Window window) {
        window.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (isFindShortcut(e) && isEditingOtherText(window)) {
                e.consume();
                notifier.accept("Termine de editar o texto (Esc) para buscar com Ctrl+F.");
            }
        });
    }

    private static boolean isFindShortcut(KeyEvent e) {
        return e.getCode() == KeyCode.F && e.isShortcutDown() && !e.isShiftDown() && !e.isAltDown();
    }

    private boolean isEditingOtherText(Window window) {
        Node owner = window.getScene() == null ? null : window.getScene().getFocusOwner();
        return owner instanceof TextInputControl && owner != bar.getField();
    }

    // ------------------------------------------------------------- abrir / fechar

    public boolean isOpen() {
        return bar.isOpen();
    }

    /** Abre a busca (ou volta o cursor ao campo, com o texto selecionado, se já está aberta). */
    public void open() {
        bar.open();
        recompute();
        bar.getField().requestFocus();
        bar.getField().selectAll();
    }

    /** Fecha a busca e tira os destaques; o foco volta ao canvas. */
    public void close() {
        boolean hadFocus = bar.hasFocusInside();
        bar.close();
        results.replace(List.of());
        view.clearSearchHighlights();
        if (hadFocus) {
            view.requestFocus();
        }
    }

    /** Fecha se estava aberta (o Esc do canvas): retorna se havia o que fechar. */
    public boolean closeIfOpen() {
        if (!bar.isOpen()) {
            return false;
        }
        close();
        return true;
    }

    // --------------------------------------------------------------- resultados

    public SearchResults getResults() {
        return results;
    }

    private BoardSearch.Scope scope() {
        return bar.getAllBoardsBox().isSelected() ? BoardSearch.Scope.ALL_BOARDS : BoardSearch.Scope.CURRENT_BOARD;
    }

    /** Refaz a busca com o que está no campo, no board da tela, e atualiza a caixa e os destaques. */
    private void recompute() {
        Board shown = navigation.getCurrent();
        String query = bar.getField().getText();
        results.replace(BoardSearch.search(navigation.getRoot(), shown, query, scope()), shown);
        bar.showResults(results.hits(), results.index());
        if (query == null || query.isBlank()) {
            bar.setSummary("", false);
        } else if (results.isEmpty()) {
            bar.setSummary("Nenhum resultado", true);
        } else {
            bar.setSummary(results.counterText(), false);
        }
        applyHighlights();
    }

    private void applyHighlights() {
        if (!bar.isOpen()) {
            return;
        }
        Board shown = navigation.getCurrent();
        Set<Card> cards = new HashSet<>();
        Set<Connection> connections = new HashSet<>();
        for (BoardSearch.Hit hit : results.hitsIn(shown)) {
            if (hit.card() != null) {
                cards.add(hit.card());
            } else {
                connections.add(hit.connection());
            }
        }
        BoardSearch.Hit current = results.currentIsIn(shown) ? results.current() : null;
        view.setSearchHighlights(cards, current == null ? null : current.card(),
                connections, current == null ? null : current.connection());
    }

    /** O texto ou o escopo mudou: refaz a busca e, se o atual está na tela, garante que ele apareça. */
    private void queryChanged() {
        if (!bar.isOpen()) {
            return;
        }
        recompute();
        BoardSearch.Hit current = results.current();
        if (current != null && results.currentIsIn(navigation.getCurrent())) {
            reveal(current, true);
        }
    }

    /** O board da tela mudou (navegação, novo ou aberto): refaz os destaques para o novo board. */
    private void boardShown() {
        if (bar.isOpen()) {
            recompute();
        }
    }

    // ---------------------------------------------------------------- navegar

    public void next() {
        step(true);
    }

    public void previous() {
        step(false);
    }

    private void step(boolean forward) {
        if (!bar.isOpen()) {
            return;
        }
        recompute(); // o board pode ter mudado desde a última vez
        if (results.isEmpty()) {
            return; // a caixa já avisa "Nenhum resultado"
        }
        BoardSearch.Hit target;
        if (!results.currentIsIn(navigation.getCurrent())) {
            target = results.current(); // ainda não visto (outro board): vai nele antes de andar
        } else {
            target = forward ? results.next() : results.previous();
        }
        goTo(target);
    }

    /** Clique num resultado da lista. */
    private void pick(int index) {
        recompute();
        if (results.select(index)) {
            goTo(results.current());
        }
    }

    /** Vai ao resultado (que já é o atual): troca de board se preciso, destaca e centraliza. */
    private void goTo(BoardSearch.Hit hit) {
        boolean hadFocus = bar.getField().isFocused();
        if (hit.board() != navigation.getCurrent()) {
            NavigationPath.JumpResult jump = navigation.jumpTo(hit.boardId());
            if (jump == NavigationPath.JumpResult.NOT_FOUND) {
                recompute();
                notifier.accept("Esse resultado não existe mais.");
                return;
            }
            if (hadFocus) {
                bar.getField().requestFocus(); // a troca de board devolve o foco ao canvas
            }
        }
        recompute(); // novos objetos de resultado e o contador (o atual é mantido pela chave)
        BoardSearch.Hit current = results.current();
        if (current != null) {
            reveal(current, false);
        }
    }

    private void reveal(BoardSearch.Hit hit, boolean onlyIfHidden) {
        if (hit.isArrow()) {
            view.revealConnection(hit.connection(), onlyIfHidden);
        } else {
            view.revealCard(hit.card(), onlyIfHidden);
        }
    }

    // ------------------------------------------------------------------ teclas

    /** Teclas do campo: Enter / Shift+Enter navegam, Esc fecha, Ctrl+F seleciona o texto (o resto é do texto). */
    private void onFieldKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            e.consume();
            close();
        } else if (e.getCode() == KeyCode.ENTER && !e.isShortcutDown() && !e.isAltDown()) {
            e.consume();
            step(!e.isShiftDown());
        } else if (isFindShortcut(e)) {
            e.consume();
            bar.getField().selectAll();
        }
    }
}
