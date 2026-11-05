package com.danilo.boardvisual.model;

/**
 * Formato visual de um card, com o tamanho padrão de cada um.
 *
 * Elipse e losango são maiores que o retângulo porque a área útil para texto
 * dentro deles é menor.
 *
 * {@link #FRAME} não é um formato de card comum: é a <b>seção</b> (frame), uma
 * área colorida com título no topo que fica sempre atrás dos cards e os
 * organiza por tema ({@link Frames}). Não aparece no seletor de formato; só a
 * ferramenta Seção cria, e um card nunca vira nem deixa de ser seção.
 */
public enum CardShape {
    RECTANGLE(200, 120),
    ELLIPSE(230, 150),
    DIAMOND(260, 170),
    FRAME(480, 320);

    private final double defaultWidth;
    private final double defaultHeight;

    CardShape(double defaultWidth, double defaultHeight) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    public double getDefaultWidth() {
        return defaultWidth;
    }

    public double getDefaultHeight() {
        return defaultHeight;
    }

    /** Converte o nome salvo em arquivo, caindo no padrão se for desconhecido. */
    public static CardShape fromName(String name) {
        if (name != null) {
            for (CardShape shape : values()) {
                if (shape.name().equalsIgnoreCase(name)) {
                    return shape;
                }
            }
        }
        return RECTANGLE;
    }
}
