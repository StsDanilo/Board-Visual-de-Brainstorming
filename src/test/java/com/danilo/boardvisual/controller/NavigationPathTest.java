package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pilha de navegação sem tela: entrar, voltar, ir a um nível e pular para
 * um board qualquer (link para outro board), com a volta ao ponto de partida.
 * A "visão" de cada nível aqui é só um texto.
 */
class NavigationPathTest {

    /**
     * <pre>
     * Principal
     *   Projeto A
     *     Orçamento
     *   Projeto B
     *     Custos
     * </pre>
     */
    private final Board root = new Board("Principal");
    private final Card projectA = cardBoard(root, "Projeto A");
    private final Card budget = cardBoard(projectA.getChildBoard(), "Orçamento");
    private final Card projectB = cardBoard(root, "Projeto B");
    private final Card costs = cardBoard(projectB.getChildBoard(), "Custos");

    private final NavigationPath<String> path = new NavigationPath<>("padrão");

    NavigationPathTest() {
        path.openRoot(root);
    }

    private static Card cardBoard(Board parent, String text) {
        Card card = new Card(parent.getCards().size() * 300, 0);
        card.setText(text);
        card.setChildBoard(new Board(""));
        parent.addCard(card);
        return card;
    }

    private List<Board> boards() {
        return path.levels().stream().map(NavigationPath.Level::board).toList();
    }

    private Board current() {
        return path.current().board();
    }

    @Test
    void enterAndBackWorkAsBefore() {
        path.enter(projectA);
        path.enter(budget);
        assertEquals(List.of(root, projectA.getChildBoard(), budget.getChildBoard()), boards());

        assertTrue(path.back());
        assertEquals(projectA.getChildBoard(), current());
        assertTrue(path.back());
        assertFalse(path.back(), "no principal, sem pulo, não há para onde voltar");
        assertFalse(path.canGoBack());
    }

    @Test
    void jumpRebuildsTheRealPathOfTheDestination() {
        path.enter(projectA);
        path.enter(budget);

        assertEquals(NavigationPath.JumpResult.DONE, path.jumpTo(costs.getChildBoard().getId()));

        assertEquals(List.of(root, projectB.getChildBoard(), costs.getChildBoard()), boards(),
                "o caminho no topo é o do destino, não o de onde se veio");
        assertEquals(costs, path.current().card());
    }

    @Test
    void backFromTheDestinationReturnsToWhereTheUserLeft() {
        path.enter(projectA);
        path.enter(budget);
        path.current().setViewState("zoom no orçamento");

        path.jumpTo(costs.getChildBoard().getId());
        assertTrue(path.backReturnsToJumpOrigin());

        assertTrue(path.back());
        assertEquals(List.of(root, projectA.getChildBoard(), budget.getChildBoard()), boards());
        assertEquals("zoom no orçamento", path.current().viewState(), "a origem lembra a própria visão");
        assertFalse(path.backReturnsToJumpOrigin());
        assertTrue(path.back());
        assertEquals(projectA.getChildBoard(), current(), "depois, o voltar normal (ao pai)");
    }

    @Test
    void jumpToTheRootCanStillGoBack() {
        path.enter(projectB);
        path.enter(costs);

        path.jumpTo(root.getId());

        assertEquals(List.of(root), boards());
        assertTrue(path.canGoBack(), "no principal, mas veio de um pulo");
        path.back();
        assertEquals(costs.getChildBoard(), current());
    }

    @Test
    void enteringAChildFromTheDestinationThenBackGoesToTheDestinationThenToTheOrigin() {
        path.enter(projectA);
        path.jumpTo(projectB.getChildBoard().getId());

        path.enter(costs);
        assertFalse(path.backReturnsToJumpOrigin(), "dentro do filho, o voltar é ao pai");
        path.back();
        assertEquals(projectB.getChildBoard(), current());
        path.back();
        assertEquals(List.of(root, projectA.getChildBoard()), boards(), "e daí, à origem do pulo");
    }

    @Test
    void clickingALevelAboveTheDestinationForgetsTheOrigin() {
        path.enter(projectA);
        path.enter(budget);
        path.jumpTo(costs.getChildBoard().getId());

        path.navigateTo(1); // Projeto B, no caminho do topo

        assertEquals(List.of(root, projectB.getChildBoard()), boards());
        path.back();
        assertEquals(List.of(root), boards(), "voltar sobe ao pai: o pulo ficou para trás");
        assertFalse(path.canGoBack());
    }

    @Test
    void chainedJumpsGoBackOneByOne() {
        path.enter(projectA);
        path.jumpTo(costs.getChildBoard().getId());
        path.jumpTo(budget.getChildBoard().getId());

        path.back();
        assertEquals(costs.getChildBoard(), current());
        path.back();
        assertEquals(projectA.getChildBoard(), current());
    }

    @Test
    void levelsSharedWithTheCurrentPathKeepTheirView() {
        path.current().setViewState("principal com zoom");
        path.enter(projectA);

        path.jumpTo(costs.getChildBoard().getId());

        assertEquals("principal com zoom", path.levels().get(0).viewState());
        assertEquals("padrão", path.current().viewState(), "o destino começa na visão padrão");
    }

    @Test
    void jumpToTheCurrentBoardChangesNothing() {
        path.enter(projectA);

        assertEquals(NavigationPath.JumpResult.SAME_BOARD, path.jumpTo(projectA.getChildBoard().getId()));
        assertFalse(path.backReturnsToJumpOrigin());
    }

    @Test
    void jumpToADeletedBoardIsNotFoundAndChangesNothing() {
        path.enter(projectA);
        String costsId = costs.getChildBoard().getId();
        projectB.getChildBoard().removeCard(costs);

        assertEquals(NavigationPath.JumpResult.NOT_FOUND, path.jumpTo(costsId));
        assertEquals(List.of(root, projectA.getChildBoard()), boards());
    }

    @Test
    void originDeletedMeanwhileBackGoesToTheParent() {
        path.enter(projectA);
        path.enter(budget);
        path.jumpTo(root.getId());

        root.removeCard(projectA); // apagou o ramo de onde se veio

        path.back();
        assertEquals(List.of(root), boards());
        assertFalse(path.canGoBack());
    }

    @Test
    void originDeletedAndRestoredByUndoIsFoundAgainById() {
        EditHistory history = new EditHistory(() -> root);
        path.enter(projectA);
        path.current().setViewState("visão de A");
        path.enter(budget);
        path.jumpTo(root.getId());

        history.perform(() -> root.removeCard(projectA));
        history.undo(); // recria o card e os boards: objetos novos, mesmos ids

        Card restoredA = root.findCard(projectA.getId()).orElseThrow();
        assertNotSame(projectA, restoredA);
        path.back();
        assertEquals(3, boards().size());
        assertEquals(restoredA.getChildBoard(), boards().get(1), "o caminho é refeito com os boards atuais");
        assertEquals(budget.getChildBoard().getId(), current().getId());
        assertEquals("visão de A", path.levels().get(1).viewState(), "cada nível mantém a visão");
    }

    @Test
    void openingAnotherRootForgetsEverything() {
        path.enter(projectA);
        path.jumpTo(costs.getChildBoard().getId());

        Board other = new Board("Outro");
        path.openRoot(other);

        assertEquals(List.of(other), boards());
        assertFalse(path.canGoBack());
    }
}
