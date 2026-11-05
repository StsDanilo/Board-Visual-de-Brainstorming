package com.danilo.boardvisual.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Regras das seções (frames, {@link CardShape#FRAME}), em Java puro e testável.
 *
 * <b>Conteúdo é só geometria:</b> um card está dentro de uma seção quando o
 * <b>centro</b> dele cai na área da seção (bordas incluídas). Não há vínculo
 * guardado no arquivo: soltar um card por cima de uma seção já o coloca nela,
 * e tirá-lo de cima já o tira. Quem decide o conteúdo de um arrasto chama
 * {@link #carriedBy} uma vez, no início do gesto.
 *
 * <b>Camadas:</b> as seções ficam sempre abaixo dos cards comuns na ordem de
 * empilhamento ({@link #layered}); quem garante é o {@link Board}.
 */
public final class Frames {

    /** Altura da faixa do título, no topo da seção (tela, texto e PDF usam a mesma). */
    public static final double TITLE_HEIGHT = 34;

    private Frames() {
    }

    /** Se o centro de {@code card} cai na área de {@code frame} (bordas incluídas). */
    public static boolean contains(Card frame, Card card) {
        if (frame == card) {
            return false;
        }
        double cx = card.getCenterX();
        double cy = card.getCenterY();
        return cx >= frame.getX() && cx <= frame.getX() + frame.getWidth()
                && cy >= frame.getY() && cy <= frame.getY() + frame.getHeight();
    }

    /** Cards de {@code cards} que estão dentro de {@code frame} (na ordem da lista). */
    public static List<Card> contentsOf(Card frame, Collection<Card> cards) {
        List<Card> inside = new ArrayList<>();
        for (Card card : cards) {
            if (contains(frame, card)) {
                inside.add(card);
            }
        }
        return inside;
    }

    /**
     * O que vai junto quando as seções {@code moving} são arrastadas: os cards
     * de {@code cards} dentro delas e, se um deles for outra seção, também o
     * conteúdo dela (seção dentro de seção leva o que tem dentro). Cada card
     * aparece uma vez só, mesmo dentro de duas seções movidas: ninguém se move
     * em dobro. Os próprios {@code moving} não entram no resultado.
     *
     * Não olha o travado: um card travado dentro de uma seção movida vai junto
     * (o cadeado protege contra arrastar o card sozinho, não contra mover a
     * seção que o organiza). Quem decide se a seção em si se move (travada não
     * se move) é o controller, antes de chamar aqui.
     */
    public static Set<Card> carriedBy(Collection<Card> moving, Collection<Card> cards) {
        Set<Card> start = new LinkedHashSet<>(moving);
        Set<Card> carried = new LinkedHashSet<>();
        Deque<Card> pending = new ArrayDeque<>();
        for (Card card : moving) {
            if (card.isFrame()) {
                pending.add(card);
            }
        }
        while (!pending.isEmpty()) {
            Card frame = pending.poll();
            for (Card card : cards) {
                if (start.contains(card) || carried.contains(card) || !contains(frame, card)) {
                    continue;
                }
                carried.add(card);
                if (card.isFrame()) {
                    pending.add(card);
                }
            }
        }
        return carried;
    }

    /**
     * Se a caixa de seleção (x, y, largura, altura, em coordenadas do board)
     * pega o card. Card comum: basta tocar. Seção: só inteira dentro da caixa;
     * senão começar uma caixa no meio vazio de uma seção, para pegar os cards
     * dela, pegaria a seção junto.
     */
    public static boolean marqueeSelects(Card card, double x, double y, double width, double height) {
        if (card.isFrame()) {
            return card.getX() >= x && card.getY() >= y
                    && card.getX() + card.getWidth() <= x + width
                    && card.getY() + card.getHeight() <= y + height;
        }
        return card.getX() <= x + width && card.getX() + card.getWidth() >= x
                && card.getY() <= y + height && card.getY() + card.getHeight() >= y;
    }

    /**
     * A mesma lista com as seções antes dos cards comuns, mantendo a ordem
     * relativa dentro de cada grupo (seções entre si, cards entre si). É o
     * invariante das camadas: seção nunca fica por cima de um card comum.
     */
    public static List<Card> layered(List<Card> order) {
        List<Card> result = new ArrayList<>(order.size());
        for (Card card : order) {
            if (card.isFrame()) {
                result.add(card);
            }
        }
        for (Card card : order) {
            if (!card.isFrame()) {
                result.add(card);
            }
        }
        return result;
    }
}
