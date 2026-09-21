# Board Visual de Brainstorming

Aplicativo desktop para organizar ideias espacialmente, no estilo Miro/Milanote:
um canvas infinito onde você cria cards, posiciona livremente e conecta com setas.

## Requisitos

- JDK 17 ou superior
- Maven 3.9+

## Como rodar

```bash
mvn javafx:run
```

Testes:

```bash
mvn test
```

## Funcionalidades

- Cards com texto editável direto no card, em três formatos: retângulo, elipse e losango
- Arrastar cards livremente; setas acompanham automaticamente
- Redimensionar cards pelas alças do card selecionado (elipse e losango mantêm a proporção)
- Texto sempre inteiro à vista, sem rolagem: a fonte se ajusta sozinha ao card (ou tem tamanho
  fixo, escolhido na barra flutuante) e o card cresce para baixo quando o texto não cabe
- Alinhamento simplificado: ao arrastar ou redimensionar, bordas e centros grudam nos de outros cards, com linhas guia
  (segure `Alt` para posicionar livremente)
- Conexões entre cards (ferramenta Conexão ou alça na borda do card)
- Canvas com pan e zoom
- Painel flutuante: card com um botão que abre um painel ao lado, em modo texto (detalhamento)
  ou lista (itens editáveis); o modo é trocado pela barra do card selecionado
- Boards aninhados: card que contém outro board (sem limite de níveis), com caminho clicável
  no topo (`Board principal › board 2 › board 3`) e botão de voltar
- Seleção múltipla: caixa de seleção, Shift+clique e mover/alterar/excluir em grupo
- Desfazer/refazer de todas as ações no board
- Barra de ferramentas: Selecionar, Retângulo, Elipse, Losango, Conexão e cor dos novos cards
- Barra flutuante da seleção: cor, formato, tamanho do texto, duplicar e excluir
- Salvar e abrir boards em arquivos JSON locais

## Atalhos de teclado

| Tecla | Ação |
|---|---|
| `V` | Selecionar |
| `R` / `E` / `L` | Criar retângulo / elipse / losango |
| `P` | Criar painel flutuante |
| `B` | Criar board aninhado |
| `Alt+←` | Voltar ao board anterior |
| `C` | Conexão |
| `Delete` / `Backspace` | Excluir seleção |
| `Ctrl+D` | Duplicar seleção |
| `Ctrl+A` | Selecionar tudo |
| `Ctrl+Z` | Desfazer |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Refazer |
| `Esc` | Sair da edição de texto → fechar painel flutuante → voltar para Selecionar → limpar seleção |
| `Espaço` + arrastar | Mover a visão (também: botão do meio ou direito) |
| `Shift` + clique | Somar/retirar card da seleção |
| `Alt` + arrastar card ou alça | Mover/redimensionar sem alinhamento automático |
| `Shift` + arrastar canto | Redimensionar mantendo a proporção (retângulo) |
| `Ctrl+N` / `Ctrl+O` | Novo board / abrir |
| `Ctrl+S` / `Ctrl+Shift+S` | Salvar / salvar como |
| `Ctrl+0` | Redefinir visualização |

Enquanto o texto de um card está sendo editado, as teclas vão para o texto (inclusive `Ctrl+Z`,
que desfaz dentro do próprio texto); só o `Esc` age como atalho. Ao sair da edição, tudo o que foi
digitado vira um único passo no desfazer do board.

## Estrutura

```
src/main/java/com/danilo/boardvisual
├─ model/        Card, Connection, Board — dados puros, sem nada visual
├─ alignment/    Geometria de caixas: alinhamento e redimensionamento — Java puro
├─ view/         Nós JavaFX: canvas, cards, setas, barras
├─ controller/   Interações de mouse e ações sobre o board
└─ persistence/  Leitura e escrita do JSON
src/main/resources/.../styles/app.css   Estilo visual (paleta e espaçamentos)
```

O modelo usa propriedades observáveis do JavaFX: a view faz binding nelas,
então mover um card no modelo atualiza o card e as setas na tela sem código
de redesenho.

## Stack

Java 17 · JavaFX 21 · Gson · JUnit 5
