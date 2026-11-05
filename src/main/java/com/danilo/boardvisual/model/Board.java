package com.danilo.boardvisual.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Um board: conjunto de cards e das conexões entre eles.
 *
 * As listas são expostas como somente leitura e observáveis. Toda alteração
 * passa pelos métodos daqui, que garantem a consistência (ex.: não existe
 * conexão apontando para um card fora do board). A view escuta as listas e
 * cria/remove os nós visuais sozinha.
 */
public class Board {

    private final String id;
    private final StringProperty name = new SimpleStringProperty(this, "name");

    private final ObservableList<Card> cards = FXCollections.observableArrayList();
    private final ObservableList<Card> readOnlyCards = FXCollections.unmodifiableObservableList(cards);

    private final ObservableList<Connection> connections = FXCollections.observableArrayList();
    private final ObservableList<Connection> readOnlyConnections = FXCollections.unmodifiableObservableList(connections);

    public Board(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public Board(String id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        setName(name);
    }

    public String getId() {
        return id;
    }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }

    public ObservableList<Card> getCards() {
        return readOnlyCards;
    }

    public ObservableList<Connection> getConnections() {
        return readOnlyConnections;
    }

    /**
     * Adiciona o card no topo da camada dele: um card comum vai para o topo da
     * pilha; uma seção ({@link Card#isFrame()}) vai para o topo das seções, logo
     * abaixo de todos os cards comuns (seção nunca cobre card, {@link Frames#layered}).
     * Retorna false se já existir um card com o mesmo id.
     */
    public boolean addCard(Card card) {
        Objects.requireNonNull(card, "card");
        if (findCard(card.getId()).isPresent()) {
            return false;
        }
        if (card.isFrame()) {
            cards.add((int) cards.stream().filter(Card::isFrame).count(), card);
        } else {
            cards.add(card);
        }
        return true;
    }

    /** Remove o card e todas as conexões ligadas a ele. */
    public boolean removeCard(Card card) {
        if (!cards.contains(card)) {
            return false;
        }
        connections.removeIf(c -> c.getSource() == card || c.getTarget() == card);
        cards.remove(card);
        return true;
    }

    public Optional<Card> findCard(String cardId) {
        return cards.stream().filter(c -> c.getId().equals(cardId)).findFirst();
    }

    // ------------------------------------------------- ordem de empilhamento

    /**
     * Muda a ordem de empilhamento dos cards informados (cards fora do board são
     * ignorados). A ordem é a da lista {@link #getCards()}: o primeiro fica no
     * fundo, o último no topo; é ela que a tela, o PDF e o PNG desenham e que o
     * arquivo salva.
     *
     * Seções ({@link Card#isFrame()}) e cards comuns são <b>duas camadas</b>: as
     * seções só mudam de lugar entre si e ficam sempre abaixo de todos os cards
     * comuns ({@link Frames#layered}). "Trazer para o topo" numa seção a leva ao
     * topo das seções, não por cima dos cards; com seções e cards selecionados
     * juntos, cada grupo se move dentro da sua camada.
     *
     * Vários cards de uma vez mantêm a ordem relativa entre eles. Um nível
     * ({@link StackMove#FORWARD}/{@link StackMove#BACKWARD}) = uma posição na
     * lista: cada card troca de lugar com o vizinho que não está sendo movido
     * (um card já no topo, ou logo abaixo de outro movido que está no topo,
     * fica onde está).
     *
     * A lista muda por <b>permutação</b>: quem escuta recebe um aviso de "mudou
     * a ordem", não de "saiu e entrou" (a tela reposiciona os nós sem recriá-los).
     *
     * @return se a ordem mudou
     */
    public boolean reorder(Collection<Card> moving, StackMove move) {
        List<Card> order = reorderedCards(moving, move);
        if (order.equals(cards)) {
            return false;
        }
        applyOrder(order);
        return true;
    }

    /** Se {@link #reorder} mudaria alguma coisa (ex.: para desabilitar "Trazer para frente" no topo). */
    public boolean canReorder(Collection<Card> moving, StackMove move) {
        return !reorderedCards(moving, move).equals(cards);
    }

    /** A ordem que {@link #reorder} produziria, sem alterar o board. */
    public List<Card> reorderedCards(Collection<Card> moving, StackMove move) {
        Set<Card> selected = new HashSet<>(moving);
        selected.retainAll(cards);
        List<Card> layered = Frames.layered(cards);
        if (selected.isEmpty()) {
            return layered;
        }
        // Cada camada (seções, depois cards comuns) se reordena sozinha.
        List<Card> order = new ArrayList<>();
        order.addAll(reorderLayer(layered.stream().filter(Card::isFrame).toList(), selected, move));
        order.addAll(reorderLayer(layered.stream().filter(c -> !c.isFrame()).toList(), selected, move));
        return order;
    }

    private static List<Card> reorderLayer(List<Card> layer, Set<Card> selected, StackMove move) {
        List<Card> order = new ArrayList<>(layer);
        switch (move) {
            case TO_FRONT, TO_BACK -> {
                List<Card> others = order.stream().filter(c -> !selected.contains(c)).toList();
                List<Card> chosen = order.stream().filter(selected::contains).toList();
                order.clear();
                order.addAll(move == StackMove.TO_FRONT ? others : chosen);
                order.addAll(move == StackMove.TO_FRONT ? chosen : others);
            }
            // Do topo para baixo: um bloco de selecionados sobe junto, um nível só.
            case FORWARD -> {
                for (int i = order.size() - 2; i >= 0; i--) {
                    if (selected.contains(order.get(i)) && !selected.contains(order.get(i + 1))) {
                        Collections.swap(order, i, i + 1);
                    }
                }
            }
            case BACKWARD -> {
                for (int i = 1; i < order.size(); i++) {
                    if (selected.contains(order.get(i)) && !selected.contains(order.get(i - 1))) {
                        Collections.swap(order, i, i - 1);
                    }
                }
            }
        }
        return order;
    }

    /**
     * Põe os cards (os mesmos objetos, em outra ordem) na ordem informada,
     * avisando como permutação. Ordenar a lista observável é o que gera esse
     * aviso; um {@code setAll} avisaria "removidos e adicionados".
     */
    private void applyOrder(List<Card> order) {
        Map<Card, Integer> position = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            position.put(order.get(i), i);
        }
        cards.sort(Comparator.comparingInt(position::get));
    }

    /**
     * Cria uma conexão de {@code source} para {@code target}.
     * Retorna vazio se a conexão for inválida (mesmo card, card fora do board,
     * seção ou conexão repetida).
     */
    public Optional<Connection> connect(Card source, Card target) {
        return connect(source, target, ConnectionStyle.DEFAULT);
    }

    /**
     * Como {@link #connect(Card, Card)}, com a seta já nascendo no estilo
     * informado (o escolhido na barra de ferramentas). As regras são as mesmas
     * para todos os estilos: uma linha sem ponta de A para B também impede
     * outra de A para B, e B→A continua permitida.
     */
    public Optional<Connection> connect(Card source, Card target, ConnectionStyle style) {
        Connection connection = new Connection(source, target);
        connection.setStyle(style);
        return addConnection(connection) ? Optional.of(connection) : Optional.empty();
    }

    /** Remove a seta (os cards ficam). */
    public boolean removeConnection(Connection connection) {
        return connections.remove(connection);
    }

    public Optional<Connection> findConnection(String connectionId) {
        return connections.stream().filter(c -> c.getId().equals(connectionId)).findFirst();
    }

    /**
     * Se existe a seta no sentido contrário (B→A para uma A→B). As duas ficam
     * sobre a mesma reta; a tela afasta os textos delas para não se cobrirem.
     */
    public boolean hasReverse(Connection connection) {
        return isConnected(connection.getTarget(), connection.getSource());
    }

    /**
     * Adiciona uma conexão já existente (usado ao carregar do arquivo). Seções
     * não têm setas (nem de nem para elas): uma seta assim é recusada.
     */
    public boolean addConnection(Connection connection) {
        Card source = connection.getSource();
        Card target = connection.getTarget();
        if (source == target
                || source.isFrame()
                || target.isFrame()
                || !cards.contains(source)
                || !cards.contains(target)
                || isConnected(source, target)) {
            return false;
        }
        connections.add(connection);
        return true;
    }

    /**
     * Cópia independente do board inteiro (cards, setas e boards filhos, em
     * qualquer profundidade). Usada ao duplicar um card que contém um board.
     *
     * Ids: o board copiado e <b>todos os boards dentro dele</b> ganham ids
     * novos, porque o id de um board é único na árvore inteira (o "link para
     * outro board" acha o destino por ele, {@link BoardTree}). Por isso um link
     * para outro board continua apontando para o <b>original</b>, mesmo dentro
     * da cópia. Cards e setas reaproveitam os ids da foto (ids de card só
     * precisam ser únicos dentro do próprio board).
     */
    public Board deepCopy() {
        Board copy = new Board(getName());
        copy.restore(snapshot());
        copy.renewChildBoardIds();
        return copy;
    }

    /**
     * Troca cada board filho (em qualquer profundidade) por um board de id
     * novo com o mesmo conteúdo. Os cards e setas mudam de board, não são
     * recriados.
     */
    private void renewChildBoardIds() {
        for (Card card : cards) {
            Board old = card.getChildBoard();
            if (old == null) {
                continue;
            }
            Board fresh = new Board(old.getName());
            fresh.cards.setAll(old.cards);
            fresh.connections.setAll(old.connections);
            old.connections.clear();
            old.cards.clear();
            fresh.renewChildBoardIds();
            card.setChildBoard(fresh);
        }
    }

    // ------------------------------------------------------ desfazer/refazer

    public BoardSnapshot snapshot() {
        return new BoardSnapshot(
                cards.stream().map(BoardSnapshot.CardState::of).toList(),
                connections.stream().map(BoardSnapshot.ConnectionState::of).toList());
    }

    /**
     * Faz o board voltar a ficar igual à foto, alterando o mínimo possível:
     * cards que continuam existindo são atualizados no lugar (mesmo objeto),
     * então a view só cria ou remove nós para o que realmente entrou ou saiu.
     *
     * Boards filhos de cards que continuam existindo não são tocados: o
     * desfazer de um board age só nele, não no que foi feito dentro dos
     * filhos. Cards recriados (ex.: desfazer uma exclusão) voltam com o board
     * filho completo da foto.
     */
    public void restore(BoardSnapshot snapshot) {
        Set<String> connectionIds = snapshot.connections().stream()
                .map(BoardSnapshot.ConnectionState::id)
                .collect(Collectors.toSet());
        connections.removeIf(c -> !connectionIds.contains(c.getId()));

        Map<String, BoardSnapshot.CardState> cardStates = new LinkedHashMap<>();
        snapshot.cards().forEach(state -> cardStates.put(state.id(), state));
        List.copyOf(cards).stream()
                .filter(card -> !cardStates.containsKey(card.getId()))
                .forEach(this::removeCard);

        for (BoardSnapshot.CardState state : cardStates.values()) {
            Optional<Card> existing = findCard(state.id());
            Card card = existing.orElseGet(() -> new Card(state.id(), state.x(), state.y()));
            state.applyTo(card);
            if (existing.isEmpty()) {
                cards.add(card);
            }
        }

        // A ordem de empilhamento também faz parte da foto: desfazer um "trazer
        // para frente" (ou uma exclusão) devolve cada card ao seu lugar na pilha.
        // As seções ficam sempre embaixo (uma foto do próprio board já vem assim).
        List<Card> order = Frames.layered(
                cardStates.keySet().stream().map(id -> findCard(id).orElseThrow()).toList());
        if (!order.equals(cards)) {
            applyOrder(order);
        }

        // Setas que continuam existindo são atualizadas no lugar (texto e estilo),
        // como os cards; as que faltam são recriadas.
        for (BoardSnapshot.ConnectionState state : snapshot.connections()) {
            Optional<Connection> present = findConnection(state.id());
            if (present.isPresent()) {
                state.applyTo(present.get());
                continue;
            }
            Optional<Card> source = findCard(state.sourceId());
            Optional<Card> target = findCard(state.targetId());
            if (source.isPresent() && target.isPresent()) {
                Connection connection = new Connection(state.id(), source.get(), target.get());
                state.applyTo(connection);
                addConnection(connection);
            }
        }
    }

    /**
     * Acrescenta a {@code names} as imagens usadas pelos cards deste board e de
     * todos os boards aninhados (a árvore inteira compartilha a pasta imagens/).
     */
    public void collectImageFiles(Set<String> names) {
        for (Card card : cards) {
            if (card.hasImage()) {
                names.add(card.getImageFile());
            }
            if (card.hasChildBoard()) {
                card.getChildBoard().collectImageFiles(names);
            }
        }
    }

    /** Indica se já existe uma seta de {@code source} para {@code target}. */
    public boolean isConnected(Card source, Card target) {
        return connections.stream().anyMatch(c -> c.getSource() == source && c.getTarget() == target);
    }
}
