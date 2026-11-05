package com.danilo.boardvisual.model;

/**
 * Estilo de desenho de uma seta ({@link Connection}). O estilo só muda o
 * desenho: as regras de conexão (sem seta para si mesmo, sem A→B repetida) e a
 * direção (origem → destino) são as mesmas para todos.
 *
 * Estilo novo = constante nova aqui + o ícone em {@code Icons.connectionStyle}
 * (switch exaustivo) + o desenho em {@code ConnectionView} (que só consulta os
 * métodos abaixo). O arquivo grava o nome da constante.
 */
public enum ConnectionStyle {

    /** Linha cheia com ponta no destino (o padrão, a seta de sempre). */
    ARROW("Seta simples", false, false, true),
    /** Linha tracejada com ponta no destino. */
    DASHED("Seta tracejada", true, false, true),
    /** Linha cheia com ponta nas duas extremidades. */
    DOUBLE("Seta de ponta dupla", false, true, true),
    /** Linha cheia sem ponta: só liga os dois cards. */
    LINE("Linha sem ponta", false, false, false);

    /** O estilo das setas criadas antes dos estilos existirem (e o de um nome desconhecido no arquivo). */
    public static final ConnectionStyle DEFAULT = ARROW;

    private final String label;
    private final boolean dashed;
    private final boolean startArrow;
    private final boolean endArrow;

    ConnectionStyle(String label, boolean dashed, boolean startArrow, boolean endArrow) {
        this.label = label;
        this.dashed = dashed;
        this.startArrow = startArrow;
        this.endArrow = endArrow;
    }

    /** Nome para o usuário (dicas da barra de ferramentas). */
    public String getLabel() {
        return label;
    }

    public boolean isDashed() {
        return dashed;
    }

    /** Ponta na origem (só a ponta dupla). */
    public boolean hasStartArrow() {
        return startArrow;
    }

    /** Ponta no destino (todos menos a linha sem ponta). */
    public boolean hasEndArrow() {
        return endArrow;
    }

    /** Lê o nome gravado no arquivo; ausente ou desconhecido vira {@link #DEFAULT}. */
    public static ConnectionStyle fromName(String name) {
        if (name != null) {
            for (ConnectionStyle style : values()) {
                if (style.name().equalsIgnoreCase(name.strip())) {
                    return style;
                }
            }
        }
        return DEFAULT;
    }
}
