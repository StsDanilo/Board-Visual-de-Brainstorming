package com.danilo.boardvisual.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    /** Adiciona o card. Retorna false se já existir um card com o mesmo id. */
    public boolean addCard(Card card) {
        Objects.requireNonNull(card, "card");
        if (findCard(card.getId()).isPresent()) {
            return false;
        }
        cards.add(card);
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

    /**
     * Cria uma conexão de {@code source} para {@code target}.
     * Retorna vazio se a conexão for inválida (mesmo card, card fora do board
     * ou conexão repetida).
     */
    public Optional<Connection> connect(Card source, Card target) {
        Connection connection = new Connection(source, target);
        return addConnection(connection) ? Optional.of(connection) : Optional.empty();
    }

    /** Adiciona uma conexão já existente (usado ao carregar do arquivo). */
    public boolean addConnection(Connection connection) {
        Card source = connection.getSource();
        Card target = connection.getTarget();
        if (source == target
                || !cards.contains(source)
                || !cards.contains(target)
                || isConnected(source, target)) {
            return false;
        }
        connections.add(connection);
        return true;
    }

    /** Indica se já existe uma seta de {@code source} para {@code target}. */
    public boolean isConnected(Card source, Card target) {
        return connections.stream().anyMatch(c -> c.getSource() == source && c.getTarget() == target);
    }
}
