package com.danilo.boardvisual.alignment;

/**
 * "Mostrar este ponto": a conta que a busca de texto usa para levar a visão até o
 * resultado. Sem JavaFX (testável); quem aplica o resultado é o {@code BoardView}.
 *
 * Centralizar mantém o zoom da pessoa, com uma exceção: abaixo de um zoom
 * mínimo legível ({@link #MIN_READABLE_ZOOM}) o texto dos cards não dá para ler,
 * então a visão sobe até ele (nunca passa do zoom máximo). Acima disso o zoom não muda.
 */
public final class ViewReveal {

    /** Abaixo de 50% o texto de um card comum já não se lê: ao ir até um resultado, o zoom sobe até aqui. */
    public static final double MIN_READABLE_ZOOM = 0.5;

    private ViewReveal() {
    }

    /**
     * Pan e zoom que põem o ponto do board no centro da tela, mantendo o zoom atual
     * (ou subindo-o a {@link #MIN_READABLE_ZOOM}, se estava menor).
     */
    public static ViewFit.Result centerOn(double worldX, double worldY, double viewWidth, double viewHeight,
                                          double currentZoom, double maxZoom) {
        double zoom = Math.min(maxZoom, Math.max(currentZoom, MIN_READABLE_ZOOM));
        return new ViewFit.Result(viewWidth / 2 - worldX * zoom, viewHeight / 2 - worldY * zoom, zoom,
                ViewFit.Limit.NONE);
    }

    /** Se a caixa do board aparece inteira na tela com esse pan e zoom. */
    public static boolean isFullyVisible(Box box, double panX, double panY, double zoom,
                                         double viewWidth, double viewHeight) {
        double left = panX + box.x() * zoom;
        double top = panY + box.y() * zoom;
        double right = left + box.width() * zoom;
        double bottom = top + box.height() * zoom;
        return left >= 0 && top >= 0 && right <= viewWidth && bottom <= viewHeight;
    }
}
