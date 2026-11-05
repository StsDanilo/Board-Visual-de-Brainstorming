package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Busca na árvore de boards por id: o que o link para outro board usa para navegar. */
class BoardTreeTest {

    /**
     * <pre>
     * Principal
     *   Projeto A
     *     Orçamento
     *   Projeto B
     *     Custos
     *       Detalhe
     * </pre>
     */
    static final class Sample {
        final Board root = new Board("Principal");
        final Card projectA = cardBoard(root, "Projeto A");
        final Card budget = cardBoard(projectA.getChildBoard(), "Orçamento\nsegunda linha");
        final Card projectB = cardBoard(root, "Projeto B");
        final Card costs = cardBoard(projectB.getChildBoard(), "Custos");
        final Card detail = cardBoard(costs.getChildBoard(), "Detalhe");

        static Card cardBoard(Board parent, String text) {
            Card card = new Card(parent.getCards().size() * 300, 0);
            card.setText(text);
            card.setChildBoard(new Board(""));
            parent.addCard(card);
            return card;
        }
    }

    private final Sample s = new Sample();

    private static List<Board> boards(List<BoardTree.Step> path) {
        return path.stream().map(BoardTree.Step::board).toList();
    }

    @Test
    void pathToADeepBoardGoesThroughEveryLevel() {
        List<BoardTree.Step> path = BoardTree.pathTo(s.root, s.detail.getChildBoard().getId());

        assertEquals(List.of(s.root, s.projectB.getChildBoard(), s.costs.getChildBoard(), s.detail.getChildBoard()),
                boards(path));
        assertNull(path.get(0).card(), "o principal não está em card nenhum");
        assertSame(s.costs, path.get(2).card(), "cada nível sabe o card que o contém");
        assertEquals("Principal › Projeto B › Custos › Detalhe", BoardTree.label(path));
    }

    @Test
    void pathFindsBoardsInDifferentBranches() {
        assertEquals(List.of(s.root, s.projectA.getChildBoard(), s.budget.getChildBoard()),
                boards(BoardTree.pathTo(s.root, s.budget.getChildBoard().getId())));
        assertEquals(List.of(s.root, s.projectB.getChildBoard()),
                boards(BoardTree.pathTo(s.root, s.projectB.getChildBoard().getId())));
        assertEquals("Principal › Projeto A › Orçamento",
                BoardTree.label(BoardTree.pathTo(s.root, s.budget.getChildBoard().getId())),
                "o nome é a primeira linha do card");
    }

    @Test
    void pathToTheRootIsJustTheRoot() {
        assertEquals(List.of(s.root), boards(BoardTree.pathTo(s.root, s.root.getId())));
    }

    @Test
    void unknownIdHasNoPath() {
        assertEquals(List.of(), BoardTree.pathTo(s.root, "nao-existe"));
        assertEquals(List.of(), BoardTree.pathTo(s.root, null));
        assertEquals(List.of(), BoardTree.pathTo(null, s.root.getId()));
        assertTrue(BoardTree.find(s.root, "nao-existe").isEmpty());
        assertFalse(BoardTree.contains(s.root, "nao-existe"));
    }

    @Test
    void deletingTheCardRemovesItsWholeSubtreeFromTheSearch() {
        String costsId = s.costs.getChildBoard().getId();
        String detailId = s.detail.getChildBoard().getId();

        s.projectB.getChildBoard().removeCard(s.costs);

        assertFalse(BoardTree.contains(s.root, costsId));
        assertFalse(BoardTree.contains(s.root, detailId), "o que estava dentro também sumiu");
        assertFalse(BoardTree.ids(s.root).contains(detailId));
    }

    @Test
    void renamingTheCardKeepsTheIdAndChangesTheLabel() {
        String id = s.costs.getChildBoard().getId();

        s.costs.setText("Gastos");

        assertEquals("Principal › Projeto B › Gastos", BoardTree.label(BoardTree.pathTo(s.root, id)));
    }

    @Test
    void entriesListTheWholeTreeInPdfOrderWithDepth() {
        List<BoardTree.Entry> entries = BoardTree.entries(s.root);

        assertEquals(List.of("Principal", "Projeto A", "Orçamento", "Projeto B", "Custos", "Detalhe"),
                entries.stream().map(BoardTree.Entry::name).toList());
        assertEquals(List.of(0, 1, 2, 1, 2, 3), entries.stream().map(BoardTree.Entry::depth).toList());
        assertEquals("Principal › Projeto B › Custos", entries.get(4).label());
        assertSame(s.costs.getChildBoard(), entries.get(4).board());
    }

    @Test
    void idsHasEveryBoardOfTheTree() {
        Set<String> ids = BoardTree.ids(s.root);

        assertEquals(6, ids.size());
        assertTrue(ids.contains(s.root.getId()));
        assertTrue(ids.contains(s.detail.getChildBoard().getId()));
    }

    @Test
    void deepCopyGivesNewIdsToEveryBoardInside() {
        Board copy = s.projectB.getChildBoard().deepCopy();
        Card copiedCosts = copy.getCards().get(0);
        Card copiedDetail = copiedCosts.getChildBoard().getCards().get(0);

        assertNotEquals(s.projectB.getChildBoard().getId(), copy.getId());
        assertNotEquals(s.costs.getChildBoard().getId(), copiedCosts.getChildBoard().getId(), "filho: id novo");
        assertNotEquals(s.detail.getChildBoard().getId(), copiedDetail.getChildBoard().getId(), "neto: id novo");
        assertEquals("Detalhe", copiedDetail.getText(), "o conteúdo é o mesmo");
        assertEquals(1, s.costs.getChildBoard().getCards().size(), "o original não perdeu nada");
    }

    @Test
    void duplicatingACardBoardKeepsIdsUniqueAndLinksPointingToTheOriginal() {
        // Um atalho dentro de Custos aponta para Detalhe (o neto, no mesmo ramo).
        Card shortcut = new Card(0, 300);
        shortcut.setBoardLink(s.detail.getChildBoard().getId());
        s.costs.getChildBoard().addCard(shortcut);

        Card copy = s.projectB.copy();
        s.root.addCard(copy);

        assertEquals(9, BoardTree.ids(s.root).size(), "6 originais + 3 copiados, sem repetir");
        Card copiedShortcut = copy.getChildBoard().getCards().get(0).getChildBoard().getCards().get(1);
        assertEquals(s.detail.getChildBoard().getId(), copiedShortcut.getBoardLink(),
                "o link da cópia continua levando ao Detalhe original");
        assertEquals(List.of(s.root, s.projectB.getChildBoard(), s.costs.getChildBoard(), s.detail.getChildBoard()),
                boards(BoardTree.pathTo(s.root, copiedShortcut.getBoardLink())));
    }

    @Test
    void duplicatingACardWithABoardLinkCopiesTheLink() {
        Card card = new Card(0, 0);
        card.setBoardLink(s.costs.getChildBoard().getId());

        assertEquals(s.costs.getChildBoard().getId(), card.copy().getBoardLink());
    }

    @Test
    void blankBoardLinkMeansNoLink() {
        Card card = new Card(0, 0);
        card.setBoardLink("  ");

        assertFalse(card.hasBoardLink());
        assertNull(card.getBoardLink());
    }
}
