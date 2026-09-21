package com.danilo.boardvisual.alignment;

/**
 * Linha guia a desenhar, em coordenadas do board.
 *
 * @param axis     eixo do alinhamento: X = linha vertical em {@code position};
 *                 Y = linha horizontal em {@code position}
 * @param position coordenada da linha (x para verticais, y para horizontais)
 * @param start    onde a linha começa, no eixo perpendicular
 * @param end      onde a linha termina, no eixo perpendicular
 */
public record Guide(Axis axis, double position, double start, double end) {
}
