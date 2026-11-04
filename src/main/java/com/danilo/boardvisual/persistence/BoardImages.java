package com.danilo.boardvisual.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A pasta {@code imagens/} de um save: um arquivo por imagem, compartilhada por
 * toda a árvore de boards daquele save.
 *
 * Aqui só há operações sobre os arquivos (copiar, limpar). Quem decide quais
 * nomes estão em uso é o chamador, porque o modelo ainda não tem imagens.
 */
public final class BoardImages {

    /** Nome da subpasta de imagens dentro da pasta do save. */
    public static final String FOLDER_NAME = "imagens";

    private BoardImages() {
    }

    /** Pasta de imagens do save (pode ainda não existir: ela nasce com a primeira imagem). */
    public static Path dirOf(Path saveFolder) {
        return saveFolder.resolve(FOLDER_NAME);
    }

    /**
     * Copia os arquivos de {@code sourceDir} para a pasta de imagens do save,
     * criando-a se preciso. Arquivos já existentes no destino com o mesmo
     * tamanho são mantidos (o nome de uma imagem é único e ela nunca é editada).
     * Cada cópia vai para um temporário e é movida no fim, como o JSON.
     *
     * Não faz nada se a origem não existe ou é a própria pasta de destino.
     */
    static void copyInto(Path sourceDir, Path saveFolder) throws IOException {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            return;
        }
        Path targetDir = dirOf(saveFolder);
        if (Files.isDirectory(targetDir) && Files.isSameFile(sourceDir, targetDir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(sourceDir)) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        if (files.isEmpty()) {
            return;
        }
        Files.createDirectories(targetDir);
        for (Path file : files) {
            Path target = targetDir.resolve(file.getFileName().toString());
            if (Files.isRegularFile(target) && Files.size(target) == Files.size(file)) {
                continue;
            }
            Path temp = targetDir.resolve(file.getFileName() + ".tmp");
            Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Apaga da pasta de imagens do save os arquivos cujo nome não está em
     * {@code referencedNames}, e devolve os nomes apagados (em ordem). Subpastas
     * não são tocadas. Se a pasta de imagens não existe, não há o que fazer.
     *
     * Chamar só <b>depois</b> de gravar o {@code board.json}: assim, se algo falhar
     * antes, o JSON antigo continua apontando para arquivos que ainda existem.
     *
     * Atenção ao desfazer: se o conjunto vier só do board atual, um card
     * excluído e restaurado com Ctrl+Z depois de salvar ficaria sem a imagem. O
     * conjunto precisa incluir também os nomes citados nas fotos do histórico.
     */
    public static List<String> removeUnused(Path saveFolder, Set<String> referencedNames) throws IOException {
        Path dir = dirOf(saveFolder);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> removed = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (!referencedNames.contains(name)) {
                    Files.delete(file);
                    removed.add(name);
                }
            }
        }
        return removed;
    }
}
