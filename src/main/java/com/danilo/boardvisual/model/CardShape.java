package com.danilo.boardvisual.model;

/**
 * Formato visual de um card.
 *
 * No MVP só existe o retângulo; elipse e losango entram na próxima etapa
 * adicionando constantes aqui (e o tratamento correspondente na view).
 */
public enum CardShape {
    RECTANGLE;

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
