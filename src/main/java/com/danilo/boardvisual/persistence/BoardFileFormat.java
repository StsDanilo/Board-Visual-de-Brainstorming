package com.danilo.boardvisual.persistence;

import java.util.List;

/**
 * Estrutura exata do arquivo JSON salvo em disco.
 *
 * Fica separada do modelo de propósito: o modelo usa JavaFX properties e
 * referências entre objetos, o arquivo usa valores simples e ids. Assim o
 * modelo pode evoluir sem quebrar arquivos antigos (basta tratar a versão).
 */
final class BoardFileFormat {

    static final int CURRENT_VERSION = 1;

    private BoardFileFormat() {
    }

    record BoardData(int version, String id, String name, List<CardData> cards, List<ConnectionData> connections) {
    }

    /**
     * panelMode, detailText e listItems foram adicionados depois da versão 1;
     * arquivos antigos simplesmente não os têm (viram card comum ao abrir).
     */
    record CardData(String id, double x, double y, double width, double height,
                    String text, String color, String shape,
                    String panelMode, String detailText, List<String> listItems) {
    }

    record ConnectionData(String id, String sourceId, String targetId) {
    }
}
