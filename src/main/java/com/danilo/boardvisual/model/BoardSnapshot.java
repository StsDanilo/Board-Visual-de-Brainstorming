package com.danilo.boardvisual.model;

import java.util.List;

/**
 * Foto imutável do conteúdo de um board (cards e conexões), usada pelo
 * desfazer/refazer. Records comparam por valor, então dá para saber se uma
 * ação mudou algo comparando a foto de antes com a de depois.
 */
public record BoardSnapshot(List<CardState> cards, List<ConnectionState> connections) {

    public BoardSnapshot {
        cards = List.copyOf(cards);
        connections = List.copyOf(connections);
    }

    public record CardState(String id, double x, double y, double width, double height,
                            String text, String color, CardShape shape,
                            PanelMode panelMode, String detailText, List<String> listItems) {

        public CardState {
            listItems = List.copyOf(listItems);
        }

        static CardState of(Card card) {
            return new CardState(card.getId(), card.getX(), card.getY(), card.getWidth(), card.getHeight(),
                    card.getText(), card.getColor(), card.getShape(),
                    card.getPanelMode(), card.getDetailText(), card.getListItemTexts());
        }

        void applyTo(Card card) {
            card.setX(x);
            card.setY(y);
            card.setWidth(width);
            card.setHeight(height);
            card.setText(text);
            card.setColor(color);
            card.setShape(shape);
            card.setPanelMode(panelMode);
            card.setDetailText(detailText);
            card.setListItemTexts(listItems);
        }
    }

    public record ConnectionState(String id, String sourceId, String targetId) {

        static ConnectionState of(Connection connection) {
            return new ConnectionState(connection.getId(),
                    connection.getSource().getId(), connection.getTarget().getId());
        }
    }
}
