package com.danilo.boardvisual.view;

import javafx.scene.image.Image;

/**
 * Dá ao {@link CardView} a imagem de um card a partir do nome do arquivo.
 *
 * A view não sabe onde as imagens moram (pasta do save, pasta de trabalho de um
 * board ainda não salvo): quem sabe é o controller, que passa uma implementação
 * ao {@link BoardView} e ao {@link BoardRenderer}.
 */
@FunctionalInterface
public interface CardImageLoader {

    /** Nenhuma imagem disponível: todo card de imagem mostra o aviso de imagem não encontrada. */
    CardImageLoader NONE = fileName -> null;

    /**
     * Imagem já carregada por completo (pronta para desenhar ou tirar snapshot),
     * ou {@code null} se o arquivo não existe ou não pôde ser aberto.
     */
    Image load(String fileName);
}
