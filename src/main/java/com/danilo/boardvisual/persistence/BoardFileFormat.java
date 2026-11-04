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

    /** Versão gravada hoje: o save é uma pasta ({@code board.json} + {@code imagens/}). */
    static final int CURRENT_VERSION = 2;

    /**
     * Primeira versão em que o save é uma pasta. As anteriores (1) eram um
     * único .json solto; o conteúdo do JSON é o mesmo, muda só o contêiner.
     * Campos novos entram com valor padrão, sem subir a versão de novo.
     */
    static final int FIRST_FOLDER_VERSION = 2;

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
     *
     * imageFile (nome do arquivo em imagens/, null = sem imagem) veio com as
     * imagens nos cards; arquivos anteriores não o têm (card sem imagem). O
     * Gson não grava campos nulos, então cards sem imagem nem citam o campo.
     *
     * link (endereço web, null = sem link) veio com o link externo, do mesmo
     * jeito: sem novo bump de versão, ausente nos saves anteriores e nos cards
     * sem link. Ao abrir, um link que não seja http(s) válido é descartado.
     */
    record CardData(String id, double x, double y, double width, double height,
                    Double baseWidth, Double baseHeight, Double fontSize,
                    String text, String color, String shape,
                    String panelMode, String detailText, List<String> listItems,
                    String imageFile, String link, BoardData childBoard) {
    }

    record ConnectionData(String id, String sourceId, String targetId) {
    }
}
