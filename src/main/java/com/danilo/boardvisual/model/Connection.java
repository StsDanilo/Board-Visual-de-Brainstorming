package com.danilo.boardvisual.model;

import java.util.Objects;
import java.util.UUID;

/** Uma seta direcionada ligando dois cards do mesmo board. */
public class Connection {

    private final String id;
    private final Card source;
    private final Card target;

    public Connection(Card source, Card target) {
        this(UUID.randomUUID().toString(), source, target);
    }

    public Connection(String id, Card source, Card target) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
    }

    public String getId() {
        return id;
    }

    public Card getSource() {
        return source;
    }

    public Card getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "Connection[" + source.getId() + " -> " + target.getId() + "]";
    }
}
