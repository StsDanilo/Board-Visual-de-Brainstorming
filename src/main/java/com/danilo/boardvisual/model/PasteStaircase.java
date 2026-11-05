package com.danilo.boardvisual.model;

/**
 * A "escada" do colar: colar de novo (e de novo) o mesmo conteúdo no mesmo lugar
 * não empilha as cópias exatamente umas sobre as outras; cada colagem desce um
 * degrau (para a direita e para baixo), e todas ficam visíveis (como o Duplicar).
 *
 * Java puro. Quem cola informa o board e o ponto-base (o centro da área visível,
 * ou onde se clicou); uma colagem "continua a escada" se for no <b>mesmo board</b>
 * e no <b>mesmo ponto-base</b> da anterior. Mudou de board, deu pan ou zoom (o
 * centro da área visível andou) ou copiou outra coisa: recomeça do ponto-base.
 */
public final class PasteStaircase {

    /** Deslocamento de cada degrau, em coordenadas do board (o mesmo do Duplicar). */
    public static final double STEP = 24;
    /** Dois pontos-base mais próximos que isto são o mesmo ponto. */
    private static final double SAME_POINT = 0.5;

    /** Onde o conjunto colado deve ficar centralizado. */
    public record Spot(double x, double y) {
    }

    private String boardId;
    private double baseX;
    private double baseY;
    private int steps;

    /** Recomeça: a próxima colagem vai exatamente no ponto-base. */
    public void reset() {
        boardId = null;
        steps = 0;
    }

    /** O ponto da próxima colagem no board, a partir do ponto-base. */
    public Spot next(String board, double x, double y) {
        boolean continues = board.equals(boardId)
                && Math.abs(x - baseX) <= SAME_POINT && Math.abs(y - baseY) <= SAME_POINT;
        steps = continues ? steps + 1 : 0;
        boardId = board;
        baseX = x;
        baseY = y;
        return new Spot(x + steps * STEP, y + steps * STEP);
    }
}
