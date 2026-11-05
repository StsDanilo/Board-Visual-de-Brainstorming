package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A busca de texto, sem tela: o que varre, como casa e em que ordem devolve. */
class BoardSearchTest {

    private static Card add(Board board, String text) {
        Card card = new Card(0, 0);
        card.setText(text);
        board.addCard(card);
        return card;
    }

    private static List<String> texts(List<BoardSearch.Hit> hits) {
        return hits.stream().map(h -> h.card() != null ? h.card().getText() : h.connection().getLabel()).toList();
    }

    // ------------------------------------------------------- como casa

    @Test
    void ignoresCaseAndAccents() {
        Board board = new Board("b");
        Card card = add(board, "Ação de Marketing — Café com Leite");

        assertEquals(List.of(card), BoardSearch.searchBoard(board, "acao").stream().map(BoardSearch.Hit::card).toList());
        assertEquals(1, BoardSearch.searchBoard(board, "AÇÃO").size());
        assertEquals(1, BoardSearch.searchBoard(board, "cafe").size());
        assertEquals(1, BoardSearch.searchBoard(board, "CAFÉ COM leite").size());
        assertEquals(1, BoardSearch.searchBoard(board, "marketing").size());
        assertEquals(0, BoardSearch.searchBoard(board, "cafés").size());
    }

    @Test
    void decomposedAccentsAlsoMatch() {
        Board board = new Board("b");
        // "café com açúcar" com os acentos separados das letras (forma decomposta, como vêm de alguns editores).
        add(board, java.text.Normalizer.normalize("café com açúcar", java.text.Normalizer.Form.NFD));
        assertEquals(1, BoardSearch.searchBoard(board, "café").size());
        assertEquals(1, BoardSearch.searchBoard(board, "cafe").size());
        assertEquals(1, BoardSearch.searchBoard(board, "açúcar").size());
        assertEquals(1, BoardSearch.searchBoard(board, "acucar").size());
    }

    @Test
    void matchesAPartOfAWordAndWhitespaceCountsAsOne() {
        Board board = new Board("b");
        add(board, "Primeira linha\nsegunda   linha");

        assertEquals(1, BoardSearch.searchBoard(board, "imeira").size(), "um pedaço de palavra casa");
        assertEquals(0, BoardSearch.searchBoard(board, "segunda primeira").size(), "a ordem das palavras importa");
        assertEquals(1, BoardSearch.searchBoard(board, "linha segunda linha").size(), "quebra de linha e espaços repetidos valem um espaço");
        assertEquals(1, BoardSearch.searchBoard(board, "  linha   segunda  ").size(), "espaços nas pontas e repetidos");
        assertTrue(BoardSearch.matches("Linha\nsegunda", "linha segunda"));
    }

    @Test
    void blankQueryFindsNothing() {
        Board board = new Board("b");
        add(board, "algo");
        assertTrue(BoardSearch.searchBoard(board, "").isEmpty());
        assertTrue(BoardSearch.searchBoard(board, "   ").isEmpty());
        assertTrue(BoardSearch.searchBoard(board, null).isEmpty());
        assertTrue(BoardSearch.search(board, board, "  ", BoardSearch.Scope.ALL_BOARDS).isEmpty());
        assertFalse(BoardSearch.matches("algo", ""));
    }

    @Test
    void noMatchGivesAnEmptyList() {
        Board board = new Board("b");
        add(board, "banana");
        add(board, "");
        assertTrue(BoardSearch.searchBoard(board, "maçã").isEmpty());
    }

    @Test
    void normalizeFoldsCaseAccentsAndSpaces() {
        assertEquals("acao e cafe", BoardSearch.normalize("  AÇÃO\n  e   Café "));
        assertEquals("", BoardSearch.normalize(null));
    }

    // ------------------------------------------------------- o que varre

    @Test
    void searchesTheTextOfEveryKindOfCard() {
        Board board = new Board("b");
        Card plain = add(board, "ideia comum sobre o projeto");
        Card frame = Card.create(CardShape.FRAME, 0, 0);
        frame.setText("Seção do projeto");
        board.addCard(frame);
        Card image = Card.createImage("foto.png", 0, 0, 160, 90);
        image.setText("Legenda do projeto");
        board.addCard(image);
        Card door = add(board, "Projeto filho");
        door.setChildBoard(new Board("filho"));
        add(board, "outra coisa");

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "projeto");

        assertEquals(4, hits.size());
        assertTrue(hits.stream().allMatch(h -> h.field() == BoardSearch.Field.TEXT));
        assertEquals(List.of("Seção do projeto", "ideia comum sobre o projeto", "Legenda do projeto", "Projeto filho"),
                texts(hits), "ordem do board: as seções ficam no fundo da pilha, antes dos cards");
    }

    @Test
    void searchesTheDetailTextOfATextPanel() {
        Board board = new Board("b");
        Card panel = add(board, "Resumo");
        panel.setPanelMode(PanelMode.TEXT);
        panel.setDetailText("Aqui o detalhe longo menciona o orçamento aprovado.");

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "ORCAMENTO");

        assertEquals(1, hits.size());
        assertEquals(panel, hits.get(0).card());
        assertEquals(BoardSearch.Field.DETAIL, hits.get(0).field());
        assertTrue(hits.get(0).snippet().contains("orçamento"), "o trecho mostra o texto original, com acento");
    }

    @Test
    void searchesTheItemsOfAListPanel() {
        Board board = new Board("b");
        Card list = add(board, "Tarefas");
        list.setPanelMode(PanelMode.LIST);
        list.setListItemTexts(List.of("comprar pão", "ligar para o João", "pagar a conta"));

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "joao");

        assertEquals(1, hits.size());
        assertEquals(BoardSearch.Field.LIST_ITEM, hits.get(0).field());
        assertEquals("ligar para o João", hits.get(0).snippet());
        assertTrue(BoardSearch.searchBoard(board, "pao").size() == 1, "acha o primeiro item também");
    }

    @Test
    void aCardAppearsOnlyOnceEvenIfSeveralFieldsMatch() {
        Board board = new Board("b");
        Card panel = add(board, "Compras de mercado");
        panel.setPanelMode(PanelMode.LIST);
        panel.setListItemTexts(List.of("mercado A", "mercado B"));

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "mercado");

        assertEquals(1, hits.size());
        assertEquals(BoardSearch.Field.TEXT, hits.get(0).field(), "o texto do card vem primeiro");
    }

    @Test
    void panelContentOfAnotherModeIsNotSearched() {
        Board board = new Board("b");
        Card card = add(board, "simples");
        card.setDetailText("escondido"); // sem modo de painel: não aparece em lugar nenhum
        assertTrue(BoardSearch.searchBoard(board, "escondido").isEmpty());
    }

    @Test
    void searchesTheTextOfArrows() {
        Board board = new Board("b");
        Card a = add(board, "origem");
        Card b = add(board, "destino");
        Connection c = new Connection(a, b);
        c.setLabel("depende de");
        board.addConnection(c);
        Connection plain = new Connection(b, a);
        board.addConnection(plain);

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "DEPENDE");

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).isArrow());
        assertEquals(c, hits.get(0).connection());
        assertNull(hits.get(0).card());
        assertNull(hits.get(0).cardId());
        assertEquals(c.getId(), hits.get(0).connectionId());
        assertEquals(BoardSearch.Field.ARROW, hits.get(0).field());
    }

    @Test
    void doesNotSearchLinksColorsOrImageFileNames() {
        Board board = new Board("b");
        Card image = Card.createImage("relatorio-final.png", 0, 0, 100, 100);
        image.setLink("https://exemplo.com/pagina");
        image.setColor("#FFD8A8");
        board.addCard(image);
        assertTrue(BoardSearch.searchBoard(board, "relatorio").isEmpty());
        assertTrue(BoardSearch.searchBoard(board, "exemplo").isEmpty());
        assertTrue(BoardSearch.searchBoard(board, "FFD8A8").isEmpty());
    }

    // ------------------------------------------------------------ ordem

    @Test
    void cardsComeInBoardOrderThenArrows() {
        Board board = new Board("b");
        Card first = add(board, "tema 1");
        Card second = add(board, "tema 2");
        Connection c = new Connection(first, second);
        c.setLabel("tema ligação");
        board.addConnection(c);
        Card third = add(board, "tema 3");

        List<BoardSearch.Hit> hits = BoardSearch.searchBoard(board, "tema");

        assertEquals(List.of("tema 1", "tema 2", "tema 3", "tema ligação"), texts(hits));
        assertEquals(java.util.Arrays.asList(first.getId(), second.getId(), third.getId(), null),
                hits.stream().map(BoardSearch.Hit::cardId).toList());
    }

    // ------------------------------------------------------------ trecho

    @Test
    void snippetKeepsContextAndMarksTheCuts() {
        Board board = new Board("b");
        add(board, "Início do texto muito longo que tem, bem no meio dele, a palavra MARCADA e depois "
                + "continua por mais um bom pedaço de frase até o fim.");

        String snippet = BoardSearch.searchBoard(board, "marcada").get(0).snippet();

        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet);
        assertTrue(snippet.contains("MARCADA"));
        assertFalse(snippet.contains("Início"));
        assertFalse(snippet.contains("\n"));
    }

    @Test
    void shortTextIsTheWholeSnippetOnOneLine() {
        Board board = new Board("b");
        add(board, "linha um\nlinha dois");
        assertEquals("linha um linha dois", BoardSearch.searchBoard(board, "dois").get(0).snippet());
    }

    // ------------------------------------------------------- aninhados

    /** principal (A, porta1) -> filho "Filho" (B, porta2) -> neto "Neto" (C); mais irmão "Outro" (D). */
    private static final class Tree {
        final Board root = new Board("Principal");
        final Card a = add(root, "alfa projeto");
        final Card door1 = add(root, "Filho");
        final Board child = new Board("filho");
        final Card b = add(child, "beta projeto");
        final Card door2 = add(child, "Neto");
        final Board grand = new Board("neto");
        final Card c = add(grand, "gama projeto");
        final Card door3 = add(root, "Outro");
        final Board other = new Board("outro");
        final Card d = add(other, "delta projeto");

        Tree() {
            door1.setChildBoard(child);
            door2.setChildBoard(grand);
            door3.setChildBoard(other);
        }
    }

    @Test
    void currentBoardScopeIgnoresTheOtherBoards() {
        Tree t = new Tree();
        List<BoardSearch.Hit> hits = BoardSearch.search(t.root, t.child, "projeto", BoardSearch.Scope.CURRENT_BOARD);

        assertEquals(List.of(t.b), hits.stream().map(BoardSearch.Hit::card).toList());
        assertEquals(t.child.getId(), hits.get(0).boardId());
        assertEquals("Principal › Filho", hits.get(0).pathLabel(), "o caminho vai do principal até o board do resultado");
    }

    @Test
    void allBoardsScopeIsRecursiveAndGivesThePath() {
        Tree t = new Tree();
        List<BoardSearch.Hit> hits = BoardSearch.search(t.root, t.root, "projeto", BoardSearch.Scope.ALL_BOARDS);

        // Ordem do PDF: principal, filho, neto (filho do filho), depois o irmão.
        assertEquals(List.of(t.a, t.b, t.c, t.d), hits.stream().map(BoardSearch.Hit::card).toList());
        assertEquals(List.of("Principal", "Principal › Filho", "Principal › Filho › Neto", "Principal › Outro"),
                hits.stream().map(BoardSearch.Hit::pathLabel).toList());
        assertEquals(List.of(t.root.getId(), t.child.getId(), t.grand.getId(), t.other.getId()),
                hits.stream().map(BoardSearch.Hit::boardId).toList());
        assertEquals(t.c.getId(), hits.get(2).cardId());
        assertEquals(t.grand, hits.get(2).board());
    }

    @Test
    void allBoardsFindsTheNameOfAChildBoardThroughItsCard() {
        Tree t = new Tree();
        List<BoardSearch.Hit> hits = BoardSearch.search(t.root, t.root, "neto", BoardSearch.Scope.ALL_BOARDS);
        assertEquals(List.of(t.door2), hits.stream().map(BoardSearch.Hit::card).toList());
        assertEquals("Principal › Filho", hits.get(0).pathLabel(), "o card está no board Filho");
    }

    @Test
    void allBoardsScopeFindsArrowsInNestedBoards() {
        Tree t = new Tree();
        Card x = add(t.grand, "x");
        Connection c = new Connection(t.c, x);
        c.setLabel("bloqueia o projeto");
        t.grand.addConnection(c);

        List<BoardSearch.Hit> hits = BoardSearch.search(t.root, t.root, "bloqueia", BoardSearch.Scope.ALL_BOARDS);

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).isArrow());
        assertEquals("Principal › Filho › Neto", hits.get(0).pathLabel());
    }

    @Test
    void nothingFoundInTheWholeTree() {
        Tree t = new Tree();
        assertTrue(BoardSearch.search(t.root, t.root, "inexistente", BoardSearch.Scope.ALL_BOARDS).isEmpty());
    }

    @Test
    void doesNotChangeTheModel() {
        Tree t = new Tree();
        BoardSnapshot before = t.root.snapshot();
        BoardSearch.search(t.root, t.root, "projeto", BoardSearch.Scope.ALL_BOARDS);
        assertEquals(before, t.root.snapshot());
    }
}
