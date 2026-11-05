package com.danilo.boardvisual.model;

import com.danilo.boardvisual.persistence.BoardStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copiar e colar cards, sem tela (3.25 do DOCUMENTACAO.md): o que a foto guarda
 * e o que a colagem faz (ids novos, links, travado, seção, posição, camadas).
 */
class CardClipTest {

    private final Board source = new Board("origem");

    private Card card(String id, double x, double y, String text) {
        Card card = new Card(id, x, y);
        card.setText(text);
        return card;
    }

    private Card add(Board board, Card card) {
        assertTrue(board.addCard(card));
        return card;
    }

    private static List<String> texts(Board board) {
        return board.getCards().stream().map(Card::getText).toList();
    }

    private CardClip copyOf(Card... cards) {
        return CardClip.of(source, List.of(cards)).orElseThrow();
    }

    /** Cola no board como a tela faz: os cards (na ordem) e depois as setas. */
    private static CardClip.Pasted pasteInto(Board target, CardClip clip, double x, double y) {
        CardClip.Pasted pasted = clip.pasteCenteredAt(x, y);
        pasted.cards().forEach(target::addCard);
        pasted.connections().forEach(target::addConnection);
        return pasted;
    }

    // ------------------------------------------------------------ o que se copia

    @Test
    void nothingToCopyWhenTheSelectionIsEmptyOrNotInTheBoard() {
        add(source, card("a", 0, 0, "A"));

        assertEquals(Optional.empty(), CardClip.of(source, List.of()));
        assertEquals(Optional.empty(), CardClip.of(source, List.of(new Card("fora", 0, 0))),
                "um card que não está no board não é copiado");
    }

    @Test
    void copiesOnlyTheSelectedCardsAndTheConnectionsBetweenThem() {
        Card a = add(source, card("a", 0, 0, "A"));
        Card b = add(source, card("b", 300, 0, "B"));
        Card outside = add(source, card("c", 600, 0, "C"));
        Connection ab = source.connect(a, b).orElseThrow();
        ab.setLabel("depende de");
        ab.setStyle(ConnectionStyle.DASHED);
        source.connect(b, outside);
        source.connect(outside, a);

        CardClip clip = copyOf(a, b);
        CardClip.Pasted pasted = clip.pasteCenteredAt(0, 0);

        assertEquals(2, clip.cardCount());
        assertEquals(1, clip.connectionCount(), "as setas de e para o card de fora ficam de fora");
        assertEquals(List.of("A", "B"), pasted.cards().stream().map(Card::getText).toList());
        Connection copied = pasted.connections().get(0);
        assertEquals("depende de", copied.getLabel(), "o texto da seta vai junto");
        assertEquals(ConnectionStyle.DASHED, copied.getStyle(), "e o estilo");
        assertSame(pasted.cards().get(0), copied.getSource());
        assertSame(pasted.cards().get(1), copied.getTarget());
        assertNotEquals(ab.getId(), copied.getId(), "a seta colada tem id novo");
    }

    @Test
    void copyIsAPhotoIndependentFromTheSource() {
        Card a = add(source, card("a", 10, 20, "original"));
        a.setColor("#FFD6D6");
        CardClip clip = copyOf(a);

        a.setText("mudou depois");
        a.setX(999);
        source.removeCard(a);

        Board target = new Board("destino");
        CardClip.Pasted pasted = pasteInto(target, clip, clip.centerX(), clip.centerY());

        Card pastedCard = pasted.cards().get(0);
        assertEquals("original", pastedCard.getText());
        assertEquals("#FFD6D6", pastedCard.getColor());
        assertEquals(10, pastedCard.getX(), 1e-9, "centralizada no centro original: fica no mesmo lugar");
        assertEquals(20, pastedCard.getY(), 1e-9);
    }

    // ------------------------------------------------------------ colar: ids e independência

    @Test
    void pasteInTheSameBoardCreatesNewCardsAndLeavesTheOriginalsAlone() {
        Card a = add(source, card("a", 0, 0, "A"));
        Card b = add(source, card("b", 300, 0, "B"));
        source.connect(a, b);
        CardClip clip = copyOf(a, b);

        CardClip.Pasted pasted = pasteInto(source, clip, 500, 500);

        assertEquals(List.of("A", "B", "A", "B"), texts(source));
        assertEquals(2, source.getConnections().size(), "a seta copiada se soma à original");
        Set<String> ids = new HashSet<>();
        source.getCards().forEach(card -> ids.add(card.getId()));
        assertEquals(4, ids.size(), "ids novos: nenhum repete");
        assertSame(a, source.getCards().get(0));
        assertEquals(0, a.getX(), 1e-9, "o original não se mexe");
        assertEquals(source.getCards().get(2), pasted.cards().get(0));
    }

    @Test
    void pastingTwiceGivesTwoIndependentCopies() {
        Card a = add(source, card("a", 0, 0, "A"));
        CardClip clip = copyOf(a);

        CardClip.Pasted first = clip.pasteCenteredAt(100, 100);
        CardClip.Pasted second = clip.pasteCenteredAt(100, 100);

        assertNotSame(first.cards().get(0), second.cards().get(0));
        assertNotEquals(first.cards().get(0).getId(), second.cards().get(0).getId());
        first.cards().get(0).setText("só a primeira muda");
        assertEquals("A", second.cards().get(0).getText());
    }

    @Test
    void pastesOnAnotherBoardKeepingTheArrangement() {
        Card a = add(source, card("a", 100, 100, "A"));
        Card b = add(source, card("b", 500, 300, "B"));
        a.resize(200, 120);
        b.resize(200, 120);
        CardClip clip = copyOf(a, b); // caixa: x 100..700, y 100..420 (centro 400, 260)

        Board other = new Board("outro");
        CardClip.Pasted pasted = pasteInto(other, clip, 1000, 1000);

        Card pa = pasted.cards().get(0);
        Card pb = pasted.cards().get(1);
        assertEquals(400, pb.getX() - pa.getX(), 1e-9, "a distância entre os cards não muda");
        assertEquals(200, pb.getY() - pa.getY(), 1e-9);
        double left = Math.min(pa.getX(), pb.getX());
        double right = Math.max(pa.getX() + pa.getWidth(), pb.getX() + pb.getWidth());
        double top = Math.min(pa.getY(), pb.getY());
        double bottom = Math.max(pa.getY() + pa.getHeight(), pb.getY() + pb.getHeight());
        assertEquals(1000, (left + right) / 2, 1e-9, "o centro do conjunto cai no ponto pedido");
        assertEquals(1000, (top + bottom) / 2, 1e-9);
        assertEquals(2, other.getCards().size());
    }

    // ------------------------------------------------------------ travado

    @Test
    void theCopyOfALockedCardIsBornUnlockedButTheOriginalStaysLocked() {
        Card locked = add(source, card("a", 0, 0, "travado"));
        locked.setLocked(true);
        Card free = add(source, card("b", 300, 0, "livre"));

        CardClip.Pasted pasted = copyOf(locked, free).pasteCenteredAt(0, 0);

        assertFalse(pasted.cards().get(0).isLocked());
        assertFalse(pasted.cards().get(1).isLocked());
        assertTrue(locked.isLocked());
    }

    // ------------------------------------------------------------ boards aninhados e links

    private Card holderWithChild(String id, String childId, String text) {
        Card holder = add(source, card(id, 0, 0, text));
        Board child = new Board(childId, "");
        Card inside = card("dentro", 5, 5, text + " / dentro");
        Card other = card("dentro2", 300, 5, text + " / dentro2");
        child.addCard(inside);
        child.addCard(other);
        child.connect(inside, other).orElseThrow().setLabel("seta de dentro");
        holder.setChildBoard(child);
        return holder;
    }

    @Test
    void aNestedBoardCardCarriesTheWholeContentWithNewBoardIds() {
        Card holder = holderWithChild("h", "board-h", "Projeto");
        Board grandChild = new Board("board-neto", "");
        grandChild.addCard(card("neto", 1, 1, "neto"));
        holder.getChildBoard().findCard("dentro").orElseThrow().setChildBoard(grandChild);

        CardClip.Pasted pasted = pasteInto(source, copyOf(holder), 0, 0);

        Card copy = pasted.cards().get(0);
        Board copiedChild = copy.getChildBoard();
        assertNotNull(copiedChild);
        assertNotSame(holder.getChildBoard(), copiedChild);
        assertEquals(List.of("Projeto / dentro", "Projeto / dentro2"), texts(copiedChild));
        assertEquals("seta de dentro", copiedChild.getConnections().get(0).getLabel());
        Board copiedGrandChild = copiedChild.findCard("dentro").orElseThrow().getChildBoard();
        assertEquals(List.of("neto"), texts(copiedGrandChild));

        // Nenhum board da árvore repete id: o link para outro board acha o destino pelo id.
        List<BoardTree.Entry> entries = BoardTree.entries(source);
        assertEquals(5, entries.size(), "principal + 2 originais + 2 copiados");
        assertEquals(5, BoardTree.ids(source).size());
        // O original ficou como estava.
        assertEquals("board-h", holder.getChildBoard().getId());
        assertEquals(List.of("Projeto / dentro", "Projeto / dentro2"), texts(holder.getChildBoard()));
    }

    @Test
    void changingThePastedNestedBoardDoesNotTouchTheOriginal() {
        Card holder = holderWithChild("h", "board-h", "Projeto");

        Card copy = copyOf(holder).pasteCenteredAt(0, 0).cards().get(0);
        copy.getChildBoard().addCard(card("novo", 0, 0, "só na cópia"));

        assertEquals(2, holder.getChildBoard().getCards().size());
        assertEquals(3, copy.getChildBoard().getCards().size());
    }

    @Test
    void linkToABoardInsideTheCopiedPartPointsToTheCopy() {
        Card holder = holderWithChild("h", "board-h", "Projeto");
        Card shortcut = add(source, card("atalho", 300, 0, "atalho"));
        shortcut.setBoardLink("board-h");

        CardClip.Pasted pasted = copyOf(holder, shortcut).pasteCenteredAt(0, 0);

        Card copiedHolder = pasted.cards().get(0);
        Card copiedShortcut = pasted.cards().get(1);
        assertEquals(copiedHolder.getChildBoard().getId(), copiedShortcut.getBoardLink(),
                "o atalho colado leva à cópia do board que veio junto");
        assertNotEquals("board-h", copiedShortcut.getBoardLink());
        assertEquals("board-h", pasted.boardIds().entrySet().stream()
                .filter(e -> e.getValue().equals(copiedShortcut.getBoardLink())).findFirst().orElseThrow().getKey());
        assertEquals("board-h", shortcut.getBoardLink(), "o original continua apontando para o original");
    }

    @Test
    void linkToABoardOutsideTheCopiedPartKeepsPointingToTheOriginal() {
        Card holder = holderWithChild("h", "board-h", "Projeto");
        Card shortcut = add(source, card("atalho", 300, 0, "atalho"));
        shortcut.setBoardLink("board-h"); // aponta para o board de um card que NÃO foi copiado

        CardClip.Pasted pasted = copyOf(shortcut).pasteCenteredAt(0, 0);

        assertEquals("board-h", pasted.cards().get(0).getBoardLink());
        assertTrue(pasted.boardIds().isEmpty());
        assertNotNull(holder.getChildBoard());
    }

    @Test
    void linkToTheSourceBoardItselfOrToAnUnknownBoardIsKept() {
        Card holder = holderWithChild("h", "board-h", "Projeto");
        Card toSource = add(source, card("p", 300, 0, "para a origem"));
        toSource.setBoardLink(source.getId());
        Card toNowhere = add(source, card("q", 600, 0, "quebrado"));
        toNowhere.setBoardLink("id-que-nao-existe");

        CardClip.Pasted pasted = copyOf(holder, toSource, toNowhere).pasteCenteredAt(0, 0);

        assertEquals(source.getId(), pasted.cards().get(1).getBoardLink(), "a origem não faz parte da parte copiada");
        assertEquals("id-que-nao-existe", pasted.cards().get(2).getBoardLink());
    }

    @Test
    void linksInsideTheCopiedSubtreeAreRemappedAtAnyDepth() {
        // Dois boards irmãos dentro de um card; um card de dentro de B aponta para A.
        Card holder = add(source, card("h", 0, 0, "Raiz"));
        Board child = new Board("board-filho", "");
        Card a = card("a", 0, 0, "A");
        a.setChildBoard(new Board("board-a", ""));
        Card b = card("b", 300, 0, "B");
        Board boardB = new Board("board-b", "");
        Card pointer = card("ponteiro", 0, 0, "ponteiro");
        pointer.setBoardLink("board-a");
        Card external = card("externo", 300, 0, "externo");
        external.setBoardLink("board-fora");
        boardB.addCard(pointer);
        boardB.addCard(external);
        b.setChildBoard(boardB);
        child.addCard(a);
        child.addCard(b);
        holder.setChildBoard(child);

        CardClip.Pasted pasted = copyOf(holder).pasteCenteredAt(0, 0);

        Board copiedChild = pasted.cards().get(0).getChildBoard();
        Board copiedA = copiedChild.findCard("a").orElseThrow().getChildBoard();
        Board copiedB = copiedChild.findCard("b").orElseThrow().getChildBoard();
        assertEquals(copiedA.getId(), copiedB.findCard("ponteiro").orElseThrow().getBoardLink(),
                "o link de dentro aponta para a cópia do board irmão");
        assertEquals("board-fora", copiedB.findCard("externo").orElseThrow().getBoardLink(),
                "o link para fora da parte copiada continua o mesmo");
        assertEquals("board-a", boardB.findCard("ponteiro").orElseThrow().getBoardLink(), "o original não muda");
        assertEquals(3, pasted.boardIds().size(), "os três boards da parte copiada: o do card, o A e o B");
    }

    // ------------------------------------------------------------ seções e camadas

    @Test
    void copyingASectionCopiesOnlyTheSectionNotTheCardsOnTopOfIt() {
        Card frame = card("s", 0, 0, "Seção");
        frame.setShape(CardShape.FRAME);
        frame.resize(600, 400);
        add(source, frame);
        Card inside = add(source, card("dentro", 50, 100, "dentro da seção"));

        CardClip clip = copyOf(frame);
        assertEquals(1, clip.cardCount());
        CardClip.Pasted pasted = clip.pasteCenteredAt(0, 0);
        assertEquals(1, pasted.cards().size());
        assertTrue(pasted.cards().get(0).isFrame());

        // Com o card de dentro também selecionado, os dois vão.
        assertEquals(2, copyOf(frame, inside).cardCount());
    }

    @Test
    void sectionsStayBelowTheCommonCardsAndPastedCardsGoToTheTop() {
        Card frame = card("s", 0, 0, "Seção");
        frame.setShape(CardShape.FRAME);
        add(source, frame);
        Card a = add(source, card("a", 0, 0, "A"));
        Card b = add(source, card("b", 300, 0, "B"));
        // Pilha: [Seção, A, B]. Copia na ordem de seleção "invertida": B, A e a seção.
        CardClip clip = CardClip.of(source, List.of(b, a, frame)).orElseThrow();

        Board target = new Board("destino");
        Card existing = add(target, card("x", 0, 0, "X"));
        Card existingFrame = card("sx", 0, 0, "Seção X");
        existingFrame.setShape(CardShape.FRAME);
        add(target, existingFrame);
        pasteInto(target, clip, 0, 0);

        assertEquals(List.of("Seção X", "Seção", "X", "A", "B"), texts(target),
                "seções embaixo (a colada no topo das seções); cards colados no topo, na ordem original");
        assertTrue(target.getCards().get(0).isFrame() && target.getCards().get(1).isFrame());
        assertSame(existing, target.getCards().get(2));
    }

    @Test
    void connectionsIgnoreSectionsAlsoWhenPasting() {
        Card frame = card("s", 0, 0, "Seção");
        frame.setShape(CardShape.FRAME);
        add(source, frame);
        Card a = add(source, card("a", 0, 0, "A"));
        Card b = add(source, card("b", 300, 0, "B"));
        source.connect(a, b);

        CardClip.Pasted pasted = copyOf(frame, a, b).pasteCenteredAt(0, 0);

        assertEquals(1, pasted.connections().size());
        assertFalse(pasted.connections().get(0).getSource().isFrame());
    }

    // ------------------------------------------------------------ imagem, texto

    @Test
    void reportsTheImagesOfTheCopiedCardsIncludingNestedBoards() {
        Card photo = add(source, card("f", 0, 0, "foto"));
        photo.setImageFile("a.png");
        Card holder = holderWithChild("h", "board-h", "Projeto");
        holder.getChildBoard().findCard("dentro").orElseThrow().setImageFile("b.png");
        Card notCopied = add(source, card("n", 0, 0, "n"));
        notCopied.setImageFile("c.png");

        CardClip clip = copyOf(photo, holder);

        assertEquals(Set.of("a.png", "b.png"), clip.imageFiles());
    }

    @Test
    void plainTextIsTheTextOfTheCardsInStackOrderWithoutBlankOnes() {
        add(source, card("a", 0, 0, "primeiro"));
        add(source, card("b", 0, 0, "   "));
        add(source, card("c", 0, 0, "terceiro"));

        CardClip clip = CardClip.of(source, source.getCards()).orElseThrow();

        assertEquals("primeiro\nterceiro", clip.plainText());
    }

    // ------------------------------------------------------------ salvar e abrir depois de colar

    @Test
    void saveAndReopenAfterPastingKeepsEverything(@TempDir Path dir) throws IOException {
        Card holder = holderWithChild("h", "board-h", "Projeto");
        Card shortcut = add(source, card("atalho", 300, 0, "atalho"));
        shortcut.setBoardLink("board-h");
        source.connect(holder, shortcut).orElseThrow().setLabel("leva a");
        Board root = new Board("raiz");
        pasteInto(root, copyOf(holder, shortcut), 100, 100);
        pasteInto(root, copyOf(holder, shortcut), 100, 100);
        BoardStorage storage = new BoardStorage();
        Path folder = dir.resolve("salvo");

        storage.save(root, folder);
        Board loaded = storage.load(folder).board();

        assertEquals(root.snapshot(), loaded.snapshot(), "o que foi colado volta do arquivo igual");
        assertEquals(BoardTree.ids(root), BoardTree.ids(loaded));
        assertEquals(3, BoardTree.ids(loaded).size(), "principal + o filho de cada um dos 2 cards colados: ids únicos");
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    private static void assertNotNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value);
    }
}
