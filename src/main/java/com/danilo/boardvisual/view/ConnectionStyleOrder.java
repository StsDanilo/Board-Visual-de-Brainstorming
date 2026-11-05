package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.ConnectionStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordem dos estilos de seta na barra de ferramentas (lógica pura, sem tela).
 *
 * A posição 0 é o lugar principal: o ícone da ferramenta Conexão, e o estilo
 * das setas novas. As outras posições são os botões extras ao lado dele.
 * Escolher um extra troca de lugar com o principal: o escolhido vira o
 * principal e o antigo principal vai para a posição que o escolhido ocupava.
 * Os estilos são sempre os mesmos, só trocam de posição.
 */
public final class ConnectionStyleOrder {

    private final List<ConnectionStyle> order = new ArrayList<>(List.of(ConnectionStyle.values()));

    /** Começa na ordem do enum: seta simples no lugar principal. */
    public ConnectionStyleOrder() {
    }

    /** O estilo do lugar principal (o das setas novas). */
    public ConnectionStyle primary() {
        return order.get(0);
    }

    /** Os estilos dos botões extras, na ordem em que aparecem. */
    public List<ConnectionStyle> extras() {
        return List.copyOf(order.subList(1, order.size()));
    }

    /** Todas as posições (a 0 é a principal). */
    public List<ConnectionStyle> order() {
        return List.copyOf(order);
    }

    /**
     * Põe {@code style} no lugar principal trocando de posição com o atual.
     * Escolher o que já é o principal não muda nada.
     *
     * @return se a ordem mudou
     */
    public boolean choose(ConnectionStyle style) {
        int index = order.indexOf(style);
        if (index <= 0) {
            return false;
        }
        order.set(index, order.get(0));
        order.set(0, style);
        return true;
    }
}
