package com.danilo.boardvisual.model;

/**
 * Movimento na ordem de empilhamento dos cards (quem fica por cima de quem).
 * A ordem é a posição do card em {@link Board#getCards()}: o primeiro fica
 * no fundo, o último no topo. Ver {@link Board#reorder}.
 */
public enum StackMove {
    /** Um nível para cima: troca de lugar com o card logo acima. */
    FORWARD,
    /** Um nível para baixo: troca de lugar com o card logo abaixo. */
    BACKWARD,
    /** Para o topo, acima de todos. */
    TO_FRONT,
    /** Para o fundo, abaixo de todos. */
    TO_BACK
}
