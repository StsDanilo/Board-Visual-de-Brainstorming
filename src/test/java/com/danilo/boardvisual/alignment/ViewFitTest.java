package com.danilo.boardvisual.alignment;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A conta do "zoom para caber tudo" (Shift+1) e "caber a seleção" (Shift+2), sem tela. */
class ViewFitTest {

    private static final double EPS = 1e-9;
    private static final double MIN = 0.2;
    private static final double MAX = 3.0;
    private static final double VIEW_W = 1000;
    private static final double VIEW_H = 600;

    private static ViewFit.Result fit(List<Box> boxes, ViewFit.Margins margins) {
        return ViewFit.fit(boxes, VIEW_W, VIEW_H, margins, MIN, MAX);
    }

    /** Onde um ponto do board cai na tela com o resultado. */
    private static double screenX(ViewFit.Result r, double worldX) {
        return r.panX() + worldX * r.zoom();
    }

    private static double screenY(ViewFit.Result r, double worldY) {
        return r.panY() + worldY * r.zoom();
    }

    // ------------------------------------------------------------------ vazio

    @Test
    void emptyListIsTheSameAsResetView() {
        ViewFit.Result r = fit(List.of(), ViewFit.Margins.uniform(50));
        assertEquals(1.0, r.zoom(), EPS);
        assertEquals(0, r.panX(), EPS);
        assertEquals(0, r.panY(), EPS);
        assertEquals(ViewFit.Limit.NONE, r.limit());
        assertEquals(ViewFit.reset(), r);
    }

    @Test
    void emptyBoardHasNoBoxes() {
        assertTrue(ViewFit.boxesOf(new Board("vazio")).isEmpty());
    }

    // ------------------------------------------------------------ um e muitos

    @Test
    void oneSmallCardIsCappedAtMaxZoomAndCentered() {
        // 200x120 caberia a mais de 3x: para em 300% e fica no meio da tela.
        ViewFit.Result r = fit(List.of(new Box(400, 300, 200, 120)), ViewFit.Margins.uniform(50));
        assertEquals(MAX, r.zoom(), EPS);
        assertEquals(ViewFit.Limit.MAX_ZOOM, r.limit());
        assertEquals(VIEW_W / 2, screenX(r, 500), EPS, "centro do card no centro horizontal");
        assertEquals(VIEW_H / 2, screenY(r, 360), EPS, "centro do card no centro vertical");
    }

    @Test
    void fitFollowsTheTighterAxisAndLeavesTheMargin() {
        // Conteúdo 2000 x 400; área útil 900 x 500 -> a largura manda: 0,45.
        ViewFit.Result r = fit(List.of(new Box(0, 0, 2000, 400)), ViewFit.Margins.uniform(50));
        assertEquals(900.0 / 2000, r.zoom(), EPS);
        assertEquals(ViewFit.Limit.NONE, r.limit());
        assertEquals(50, screenX(r, 0), EPS, "margem da esquerda");
        assertEquals(VIEW_W - 50, screenX(r, 2000), EPS, "margem da direita");
        assertEquals(VIEW_H / 2, screenY(r, 200), EPS, "sobra na altura: centralizado na vertical");
    }

    @Test
    void tallContentIsLimitedByHeight() {
        ViewFit.Result r = fit(List.of(new Box(-100, -100, 300, 1000)), ViewFit.Margins.uniform(50));
        assertEquals(500.0 / 1000, r.zoom(), EPS);
        assertEquals(50, screenY(r, -100), EPS);
        assertEquals(VIEW_H - 50, screenY(r, 900), EPS);
    }

    @Test
    void manyBoxesUseTheirUnion() {
        List<Box> boxes = List.of(new Box(0, 0, 200, 120), new Box(800, 0, 200, 120), new Box(0, 480, 200, 120));
        // União: 0..1000 x 0..600; útil 900 x 500 -> min(0,9; 0,8333) = 0,8333.
        ViewFit.Result r = fit(boxes, ViewFit.Margins.uniform(50));
        assertEquals(500.0 / 600, r.zoom(), EPS);
        assertEquals(VIEW_W / 2, screenX(r, 500), EPS);
        assertEquals(VIEW_H / 2, screenY(r, 300), EPS);
        // E todas as caixas ficam inteiras dentro da tela.
        for (Box b : boxes) {
            assertTrue(ViewReveal.isFullyVisible(b, r.panX(), r.panY(), r.zoom(), VIEW_W, VIEW_H));
        }
    }

    @Test
    void asymmetricMarginsShiftTheContentAwayFromTheToolbar() {
        // 88 px à esquerda (barra de ferramentas), 40 nos demais lados.
        ViewFit.Margins margins = new ViewFit.Margins(40, 40, 40, 88);
        ViewFit.Result r = fit(List.of(new Box(0, 0, 1000, 100)), margins);
        assertEquals((VIEW_W - 88 - 40) / 1000, r.zoom(), EPS);
        assertEquals(88, screenX(r, 0), EPS);
        assertEquals(VIEW_W - 40, screenX(r, 1000), EPS);
    }

    // ----------------------------------------------------------------- limites

    @Test
    void tinyContentNeverZoomsPastMax() {
        ViewFit.Result r = fit(List.of(new Box(10, 10, 5, 5)), ViewFit.Margins.uniform(50));
        assertEquals(MAX, r.zoom(), EPS);
        assertEquals(ViewFit.Limit.MAX_ZOOM, r.limit());
    }

    @Test
    void hugeContentStopsAtMinZoomAndIsCentered() {
        // 20000 x 2000 pede 4,5%: para em 20% e mostra o centro do conteúdo.
        ViewFit.Result r = fit(List.of(new Box(-10000, -1000, 20000, 2000)), ViewFit.Margins.uniform(50));
        assertEquals(MIN, r.zoom(), EPS);
        assertEquals(ViewFit.Limit.MIN_ZOOM, r.limit());
        assertEquals(VIEW_W / 2, screenX(r, 0), EPS, "o centro do conteúdo fica no centro da tela");
        assertEquals(VIEW_H / 2, screenY(r, 0), EPS);
        assertTrue(screenX(r, -10000) < 0 && screenX(r, 10000) > VIEW_W, "cortado dos dois lados, igualmente");
    }

    @Test
    void contentThatFitsExactlyAtMinZoomIsNotFlaggedAsTooBig() {
        // Útil 900 x 500; a 20% cabe conteúdo de 4500 x 2500: exatamente no limite.
        ViewFit.Result r = fit(List.of(new Box(0, 0, 4500, 2500)), ViewFit.Margins.uniform(50));
        assertEquals(MIN, r.zoom(), EPS);
        assertEquals(ViewFit.Limit.NONE, r.limit());
    }

    // ------------------------------------------------------------ casos de borda

    @Test
    void marginsThatEatTheWindowShrinkToFit() {
        // Janela minúscula: margens de 400 não podem zerar a área útil.
        ViewFit.Result r = ViewFit.fit(List.of(new Box(0, 0, 200, 100)), 300, 200,
                ViewFit.Margins.uniform(400), MIN, MAX);
        assertTrue(r.zoom() > 0);
        assertTrue(Double.isFinite(r.panX()) && Double.isFinite(r.panY()));
        assertTrue(ViewReveal.isFullyVisible(new Box(0, 0, 200, 100), r.panX(), r.panY(), r.zoom(), 300, 200));
    }

    @Test
    void zeroSizedViewStillGivesAFiniteResult() {
        ViewFit.Result r = ViewFit.fit(List.of(new Box(0, 0, 200, 100)), 0, 0, ViewFit.Margins.uniform(50), MIN, MAX);
        assertEquals(MIN, r.zoom(), EPS);
        assertTrue(Double.isFinite(r.panX()) && Double.isFinite(r.panY()));
    }

    @Test
    void degenerateBoxWithoutAreaIsHandled() {
        ViewFit.Result r = fit(List.of(new Box(100, 100, 0, 0)), ViewFit.Margins.uniform(50));
        assertEquals(MAX, r.zoom(), EPS);
        assertEquals(VIEW_W / 2, screenX(r, 100), EPS);
        assertEquals(VIEW_H / 2, screenY(r, 100), EPS);
    }

    // ----------------------------------------------------------- caixas do board

    @Test
    void boxesOfCoversEveryKindOfCard() {
        Board board = new Board("b");
        Card plain = Card.create(CardShape.RECTANGLE, 100, 100);
        Card ellipse = Card.create(CardShape.ELLIPSE, 900, 100);
        Card frame = Card.create(CardShape.FRAME, 500, 700); // seção
        Card image = Card.createImage("foto.png", -300, 300, 160, 90);
        board.addCard(plain);
        board.addCard(ellipse);
        board.addCard(frame);
        board.addCard(image);

        List<Box> boxes = ViewFit.boxesOf(board);
        assertEquals(4, boxes.size());
        Box union = boxes.get(0);
        for (Box b : boxes) {
            union = union.union(b);
        }
        assertEquals(image.getX(), union.x(), EPS, "a imagem é a mais à esquerda");
        assertEquals(frame.getY() + frame.getHeight(), union.y() + union.height(), EPS, "a seção é a mais embaixo");
        assertEquals(ellipse.getX() + ellipse.getWidth(), union.x() + union.width(), EPS);
    }

    @Test
    void arrowLabelsCountAndCanWidenTheContent() {
        Board board = new Board("b");
        Card a = new Card(0, 0);
        Card b = new Card(210, 0); // 10 de folga entre os dois cards
        board.addCard(a);
        board.addCard(b);
        Connection c = new Connection(a, b);
        board.addConnection(c);
        assertEquals(2, ViewFit.boxesOf(board).size(), "seta sem texto não conta");

        c.setLabel("uma legenda bem comprida que passa dos cards vizinhos e sai pelos lados");
        List<Box> boxes = ViewFit.boxesOf(board);
        assertEquals(3, boxes.size());
        Box label = boxes.get(2);
        assertEquals(ViewFit.LABEL_MAX_WIDTH, label.width(), EPS, "limitada à largura máxima");
        assertEquals(205, label.x() + label.width() / 2, 1.0, "centrada no meio da seta (entre os dois cards)");
    }

    @Test
    void selectionBoxesIgnoreCardsOutsideTheBoardAndOtherCards() {
        Board board = new Board("b");
        Card a = new Card(0, 0);
        Card b = new Card(500, 0);
        Card outside = new Card(9000, 9000);
        board.addCard(a);
        board.addCard(b);

        List<Box> boxes = ViewFit.boxesOf(board, List.of(b, outside));
        assertEquals(1, boxes.size());
        assertEquals(500, boxes.get(0).x(), EPS);
    }

    @Test
    void selectionLabelsOnlyWhenBothEndsAreSelected() {
        Board board = new Board("b");
        Card a = new Card(0, 0);
        Card b = new Card(400, 0);
        Card c = new Card(800, 0);
        board.addCard(a);
        board.addCard(b);
        board.addCard(c);
        Connection ab = new Connection(a, b);
        Connection bc = new Connection(b, c);
        board.addConnection(ab);
        board.addConnection(bc);
        ab.setLabel("ab");
        bc.setLabel("bc");

        assertEquals(5, ViewFit.boxesOf(board).size(), "3 cards + 2 textos");
        assertEquals(3, ViewFit.boxesOf(board, List.of(a, b)).size(), "2 cards + o texto de ab");
        assertEquals(1, ViewFit.boxesOf(board, List.of(a)).size());
        assertFalse(ViewFit.boxesOf(board, List.of()).iterator().hasNext());
    }

    @Test
    void selectionFitEnclosesOnlyTheSelectedCards() {
        Board board = new Board("b");
        Card far = new Card(5000, 5000);
        Card near = new Card(100, 100);
        board.addCard(far);
        board.addCard(near);

        ViewFit.Result all = fit(ViewFit.boxesOf(board), ViewFit.Margins.uniform(50));
        ViewFit.Result sel = fit(ViewFit.boxesOf(board, List.of(near)), ViewFit.Margins.uniform(50));
        assertTrue(sel.zoom() > all.zoom());
        assertEquals(MAX, sel.zoom(), EPS, "um card sozinho vai ao zoom máximo");
        assertEquals(VIEW_W / 2, screenX(sel, near.getCenterX()), EPS);
    }
}
