package com.danilo.boardvisual.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * O que o {@code Ctrl+C} guardou: os cards copiados, as setas entre eles e tudo
 * que está dentro deles (boards aninhados, em qualquer profundidade). Java puro:
 * monta a cópia e aplica a colagem sem tela, então é testável sem interface.
 *
 * <b>Foto imutável.</b> O conteúdo é um {@link BoardSnapshot} (records imutáveis),
 * independente do board de onde veio: depois de copiar, o original pode mudar ou
 * ser excluído e a colagem continua saindo como era na hora da cópia. Pode ser
 * colada quantas vezes se quiser, no mesmo board ou em outro (inclusive o de um
 * save aberto depois).
 *
 * <b>O que entra:</b> só os cards selecionados (uma seção copia só a seção, não
 * os cards que estão sobre ela, a menos que eles também estejam selecionados) e
 * as setas cujas duas pontas estão na seleção, com texto e estilo. Seta para um
 * card de fora fica de fora.
 *
 * <b>O que a colagem faz</b> ({@link #pasteCenteredAt}), em objetos novos, que
 * ainda não pertencem a nenhum board:
 * <ul>
 *   <li>cada card e cada seta nasce com id novo, e cada board aninhado também
 *       ({@link Board#deepCopy}: o id de um board é único na árvore inteira);</li>
 *   <li>a cópia de um card travado nasce <b>destravada</b>, como no Duplicar (só
 *       os cards colados; o que está dentro de um board aninhado fica como era);</li>
 *   <li>o conjunto é posto de modo que o centro da caixa que o envolve fique no
 *       ponto pedido, sem mudar a posição de um card em relação aos outros;</li>
 *   <li>um link para outro board que aponta para <b>dentro da parte copiada</b>
 *       (um board aninhado que veio junto, em qualquer profundidade) passa a
 *       apontar para a <b>cópia</b> dele; um link para qualquer outro board
 *       continua apontando para o original (ver {@link Pasted#boardIds()}).</li>
 * </ul>
 * Entrar no board (quem chama {@code Board.addCard}) mantém as camadas: seções
 * ficam abaixo dos cards comuns, e os cards colados entram no topo da pilha.
 */
public final class CardClip {

    /**
     * O resultado de uma colagem, ainda fora de qualquer board.
     *
     * @param cards       os cards novos, na ordem de empilhamento de onde vieram
     *                    (seções primeiro): é a ordem para adicioná-los ao board
     * @param connections as setas novas entre eles
     * @param boardIds    id do board aninhado original → id da cópia (para conferir
     *                    o remapeamento dos links)
     */
    public record Pasted(List<Card> cards, List<Connection> connections, Map<String, String> boardIds) {
        public Pasted {
            cards = List.copyOf(cards);
            connections = List.copyOf(connections);
            boardIds = Map.copyOf(boardIds);
        }
    }

    private final BoardSnapshot content;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private CardClip(BoardSnapshot content) {
        this.content = content;
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (BoardSnapshot.CardState card : content.cards()) {
            left = Math.min(left, card.x());
            top = Math.min(top, card.y());
            right = Math.max(right, card.x() + card.width());
            bottom = Math.max(bottom, card.y() + card.height());
        }
        this.minX = left;
        this.minY = top;
        this.maxX = right;
        this.maxY = bottom;
    }

    /**
     * Copia {@code selected} (só os que estão em {@code source}) e as setas entre
     * eles. Vazio se nenhum dos cards está no board.
     */
    public static Optional<CardClip> of(Board source, Collection<Card> selected) {
        Set<Card> wanted = new HashSet<>(selected);
        Set<String> ids = source.getCards().stream()
                .filter(wanted::contains).map(Card::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        // A foto do board já vem na ordem de empilhamento; filtrar mantém a ordem.
        BoardSnapshot all = source.snapshot();
        return Optional.of(new CardClip(new BoardSnapshot(
                all.cards().stream().filter(card -> ids.contains(card.id())).toList(),
                all.connections().stream()
                        .filter(c -> ids.contains(c.sourceId()) && ids.contains(c.targetId())).toList())));
    }

    /** A foto do que foi copiado (cards e setas, com os boards aninhados). */
    public BoardSnapshot snapshot() {
        return content;
    }

    public int cardCount() {
        return content.cards().size();
    }

    public int connectionCount() {
        return content.connections().size();
    }

    /** Nomes dos arquivos de imagem usados pelos cards copiados, inclusive nos boards aninhados. */
    public Set<String> imageFiles() {
        Set<String> names = new HashSet<>();
        content.collectImageFiles(names);
        return names;
    }

    /**
     * Os textos dos cards copiados, um por linha, na ordem de empilhamento (o que
     * vai para a área de transferência do sistema, para colar em outro programa).
     */
    public String plainText() {
        return content.cards().stream().map(BoardSnapshot.CardState::text)
                .filter(text -> !text.isBlank()).collect(Collectors.joining("\n"));
    }

    /** Centro, em coordenadas do board, da caixa que envolve os cards copiados. */
    public double centerX() {
        return (minX + maxX) / 2;
    }

    public double centerY() {
        return (minY + maxY) / 2;
    }

    // ------------------------------------------------------------ colar

    /**
     * Monta uma colagem nova, com o centro do conjunto em ({@code centerX},
     * {@code centerY}). Cada chamada gera objetos e ids novos: colar duas vezes
     * dá duas cópias independentes.
     */
    public Pasted pasteCenteredAt(double centerX, double centerY) {
        double dx = centerX - centerX();
        double dy = centerY - centerY();

        // As cópias saem de um board de rascunho com a foto restaurada: Card.copy() e
        // Board.deepCopy dão id novo ao card e a todos os boards de dentro dele.
        Board scratch = new Board("");
        scratch.restore(content);

        List<Card> cards = new ArrayList<>();
        Map<String, Card> copyByOriginalId = new HashMap<>();
        Map<String, String> boardIds = new HashMap<>();
        for (Card original : scratch.getCards()) {
            Card copy = original.copy();
            copy.setX(original.getX() + dx);
            copy.setY(original.getY() + dy);
            copy.setLocked(false);
            if (original.hasChildBoard()) {
                mapBoardIds(original.getChildBoard(), copy.getChildBoard(), boardIds);
            }
            cards.add(copy);
            copyByOriginalId.put(original.getId(), copy);
        }
        remapBoardLinks(cards, boardIds);

        List<Connection> connections = new ArrayList<>();
        for (BoardSnapshot.ConnectionState state : content.connections()) {
            Card source = copyByOriginalId.get(state.sourceId());
            Card target = copyByOriginalId.get(state.targetId());
            if (source == null || target == null) {
                continue;
            }
            // Uma propriedade nova da seta entra aqui (o ConnectionAllPropertiesTest confere).
            Connection connection = new Connection(source, target);
            connection.setLabel(state.label());
            connection.setStyle(state.style());
            connections.add(connection);
        }
        return new Pasted(cards, connections, boardIds);
    }

    /**
     * Anota, para cada board da subárvore original, o id do board que o copiou. As
     * duas árvores têm a mesma forma e os cards mantêm o id dentro de um board
     * copiado, então se casam pelo id do card.
     */
    private static void mapBoardIds(Board original, Board copy, Map<String, String> ids) {
        ids.put(original.getId(), copy.getId());
        for (Card card : original.getCards()) {
            if (card.hasChildBoard()) {
                copy.findCard(card.getId()).ifPresent(copied -> {
                    if (copied.hasChildBoard()) {
                        mapBoardIds(card.getChildBoard(), copied.getChildBoard(), ids);
                    }
                });
            }
        }
    }

    /** Aponta para a cópia os links para boards que vieram junto (nos cards colados e em todos os de dentro). */
    private static void remapBoardLinks(Collection<Card> cards, Map<String, String> boardIds) {
        for (Card card : cards) {
            if (card.hasBoardLink() && boardIds.containsKey(card.getBoardLink())) {
                card.setBoardLink(boardIds.get(card.getBoardLink()));
            }
            if (card.hasChildBoard()) {
                remapBoardLinks(card.getChildBoard().getCards(), boardIds);
            }
        }
    }
}
