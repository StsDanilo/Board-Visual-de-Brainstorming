package com.danilo.boardvisual.alignment;

import java.util.List;

/**
 * Resultado do alinhamento: quanto ajustar em cada eixo para grudar, e as
 * linhas guia a mostrar.
 *
 * Ao mover, o ajuste desloca a caixa ({@code x += dx}). Ao redimensionar
 * (arrastando, por exemplo, a borda direita), o mesmo ajuste vai para o
 * tamanho ({@code width += dx}): quem chama é que decide o que fazer com ele.
 */
public record SnapResult(double dx, double dy, List<Guide> guides) {

    public static final SnapResult NONE = new SnapResult(0, 0, List.of());

    public SnapResult {
        guides = List.copyOf(guides);
    }
}
