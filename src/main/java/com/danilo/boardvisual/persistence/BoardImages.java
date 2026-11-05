package com.danilo.boardvisual.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A pasta {@code imagens/} de um save: um arquivo por imagem, compartilhada por
 * toda a árvore de boards daquele save.
 *
 * Aqui só há operações sobre os arquivos (copiar, limpar, achar). Quem decide
 * quais nomes estão em uso é o chamador: os cards de toda a árvore de boards e
 * as fotos do histórico de desfazer (ver {@link #removeUnused}). Trazer uma
 * imagem nova para a pasta é com o {@link ImageImporter}.
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
     * Se {@code name} serve como nome de arquivo dentro de uma pasta de imagens:
     * sem pastas no meio nem {@code ..}. Um board.json editado à mão com
     * {@code "../outro.png"} não pode fazer o programa ler fora da pasta.
     */
    public static boolean isPlainFileName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains(":")
                && !name.equals(".") && !name.equals("..");
    }

    /**
     * Procura o arquivo {@code name} nas pastas informadas, na ordem; vazio se
     * o nome não é um nome de arquivo simples ou se não está em nenhuma.
     */
    public static Optional<Path> find(String name, List<Path> dirs) {
        if (!isPlainFileName(name)) {
            return Optional.empty();
        }
        for (Path dir : dirs) {
            if (dir == null) {
                continue;
            }
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
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
        for (Path file : files) {
            copyFile(file, targetDir);
        }
    }

    /**
     * Copia um arquivo de imagem para {@code targetDir} (criada se preciso) com o
     * mesmo nome. Um arquivo já existente lá com o mesmo tamanho é mantido (o
     * nome de uma imagem é único e ela nunca é editada). A cópia vai para um
     * temporário e é movida no fim, como o JSON.
     */
    public static void copyFile(Path file, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(file.getFileName().toString());
        if (Files.isRegularFile(target) && Files.size(target) == Files.size(file)) {
            return;
        }
        Path temp = targetDir.resolve(file.getFileName() + ".tmp");
        try {
            Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
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
