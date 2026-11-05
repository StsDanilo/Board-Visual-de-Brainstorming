package com.danilo.boardvisual.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * As imagens dos cards copiados (Ctrl+C) nos boards, para a colagem em outro
 * lugar. Os cards guardam só o <b>nome</b> do arquivo (3.17 do DOCUMENTACAO.md),
 * e o arquivo mora na pasta {@code imagens/} do save de origem, ou na pasta de
 * trabalho temporária de um board ainda não salvo. Colar em outro save (ou
 * depois que a origem foi salva por cima, aberta de novo ou excluída) precisaria
 * de um arquivo que pode já não estar lá.
 *
 * Por isso, <b>ao copiar</b>, os arquivos são guardados numa pasta temporária
 * própria da área de transferência ({@link #keep}), que vive até a próxima cópia
 * ou o fim do programa ({@link #discard}). <b>Ao colar</b> ({@link #deliver}): se
 * o arquivo já é achado onde o board atual guarda imagens (o mesmo save, ou a
 * pasta de trabalho), nada é copiado; senão, ele vem da pasta guardada para a
 * pasta de trabalho do destino (que o salvar leva para o {@code imagens/}). Um
 * arquivo que já não existia na hora de copiar fica faltando: o card colado
 * mostra o aviso "Imagem não encontrada", como qualquer card de imagem sem o
 * arquivo. O nome do arquivo não muda: os cards colados apontam para o mesmo nome.
 *
 * Só mexe em arquivos (Java puro, sem tela): o {@code ImageController} diz onde
 * ficam as imagens do board atual.
 */
public final class ClipboardImages {

    /** Onde entram as imagens que chegam de fora do save atual (a pasta de trabalho). */
    @FunctionalInterface
    public interface TargetDir {
        Path get() throws IOException;
    }

    /** Pasta temporária com as imagens da última cópia; criada na primeira que precisa. */
    private Path dir;

    /**
     * Guarda os arquivos {@code names} (procurados em {@code sourceDirs}, na
     * ordem), no lugar do que a cópia anterior tinha guardado.
     *
     * @return os nomes que não foram achados em nenhuma das pastas (ficam sem arquivo)
     */
    public List<String> keep(Collection<String> names, List<Path> sourceDirs) throws IOException {
        discard();
        List<String> missing = new ArrayList<>();
        for (String name : new TreeSet<>(names)) {
            Optional<Path> file = BoardImages.find(name, sourceDirs);
            if (file.isEmpty()) {
                missing.add(name);
                continue;
            }
            if (dir == null) {
                dir = Files.createTempDirectory("boardvisual-copia-");
            }
            BoardImages.copyFile(file.get(), dir);
        }
        return missing;
    }

    /**
     * Garante que as imagens {@code names} existam onde o board atual as procura
     * ({@code searchDirs}): as que já estão lá ficam como estão; as que faltam vêm da
     * pasta guardada na cópia para {@code target}.
     *
     * @return os nomes que não estão em {@code searchDirs} nem na pasta guardada
     */
    public List<String> deliver(Collection<String> names, List<Path> searchDirs, TargetDir target) throws IOException {
        List<String> missing = new ArrayList<>();
        for (String name : new TreeSet<>(names)) {
            if (BoardImages.find(name, searchDirs).isPresent()) {
                continue; // o mesmo save (ou a pasta de trabalho): reaproveita o arquivo
            }
            Optional<Path> kept = dir == null ? Optional.empty() : BoardImages.find(name, List.of(dir));
            if (kept.isEmpty()) {
                missing.add(name);
                continue;
            }
            BoardImages.copyFile(kept.get(), target.get());
        }
        return missing;
    }

    /** A pasta com as imagens guardadas, ou {@code null} se a última cópia não tinha nenhuma. */
    public Path keptDir() {
        return dir != null && Files.isDirectory(dir) ? dir : null;
    }

    /** Apaga a pasta temporária (ao copiar de novo ou ao sair). Falhar aqui não afeta o board. */
    public void discard() {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException | UncheckedIOException e) {
            // Sobra na pasta temporária do sistema.
        }
        dir = null;
    }
}
