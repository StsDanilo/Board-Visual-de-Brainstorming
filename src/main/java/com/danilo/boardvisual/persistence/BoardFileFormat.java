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
     * panelMode, detailText, listItems e childBoard foram adicionados depois da
     * versão 1; arquivos antigos simplesmente não os têm (viram card comum).
     * childBoard é um board completo, aninhado dentro do card (recursivo).
     *
     * baseWidth/baseHeight (tamanho definido à mão) e fontSize (null =
     * automático) também vieram depois; sem eles, o tamanho definido à mão é
     * o próprio tamanho salvo.
     */
    record CardData(String id, double x, double y, double width, double height,
                    Double baseWidth, Double baseHeight, Double fontSize,
                    String text, String color, String shape,
                    String panelMode, String detailText, List<String> listItems,
                    BoardData childBoard) {
    }

    record ConnectionData(String id, String sourceId, String targetId) {
    }
}
