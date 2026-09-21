package com.danilo.boardvisual.model;

/**
 * Tipo de conteúdo do painel flutuante de um card.
 *
 * {@link #NONE} é um card comum; os outros abrem um painel ao clicar no
 * botão do card.
 */
public enum PanelMode {
    NONE,
    /** Caixa de texto maior, para detalhar o que o card resume. */
    TEXT,
    /** Lista de itens. */
    LIST;

    /** Converte o nome salvo em arquivo; ausente ou desconhecido vira card comum. */
    public static PanelMode fromName(String name) {
        if (name != null) {
            for (PanelMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return NONE;
    }
}
