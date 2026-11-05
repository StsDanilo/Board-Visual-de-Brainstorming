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
- Estilos de seta: simples, tracejada, ponta dupla ou linha sem ponta. O ícone da ferramenta Conexão
  desenha o estilo das setas novas; com o mouse sobre ele, abre à direita da barra de ferramentas uma mini
  barra com os outros três estilos (ela continua aberta enquanto o mouse está nela). Clicar num deles troca
  de lugar com o principal e vale para as próximas setas, pela ferramenta ou pela alça. Com uma seta
  selecionada, esses botões mudam o estilo dela (o ícone principal aplica o estilo dele na seta)
- Setas com texto: clique na seta para selecioná-la (fica azul; `Delete` exclui) e duplo clique na seta
  (ou no texto dela) para escrever, por exemplo, "leva a" ou "depende de". `Enter` ou clicar fora confirma,
  `Esc` cancela. O texto fica no meio da seta, numa linha (até 60 caracteres; longo demais termina em "…"),
  acompanha os cards ao mover e aparece no PNG e no PDF (buscável). Com A→B e B→A, os dois textos ficam
  lado a lado; acima da linha se clica numa seta, abaixo na outra
- Canvas com pan e zoom
- Painel flutuante: card com um botão que abre um painel ao lado, em modo texto (detalhamento)
  ou lista (itens editáveis); o modo é trocado pela barra do card selecionado
- Boards aninhados: card que contém outro board (sem limite de níveis), com caminho clicável
  no topo (`Board principal › board 2 › board 3`) e botão de voltar
- Imagens no board: colar (`Ctrl+V`), arrastar o arquivo para o canvas ou escolher pela ferramenta Imagem (`I`).
  A imagem vira um card (mover, redimensionar sempre na proporção, conectar, duplicar, desfazer, salvar),
  com legenda opcional embaixo (duplo clique na imagem). Aceita PNG, JPG, GIF (primeiro quadro) e BMP;
  imagens maiores que 2000 px são reduzidas ao inserir. O arquivo fica na pasta `imagens/` do save
- Link externo em qualquer card (comum, painel, board aninhado ou imagem): clique direito no card
  › "Adicionar link..." (ou o botão Link da barra flutuante) e digite o endereço. O card mostra um
  clipe na borda de cima; clicar nele abre o endereço no navegador (a dica mostra o endereço). Só
  aceita `http://` e `https://` (sem esquema, assume `https://`). No PDF o card vira link para o site
- Link para outro board: qualquer card pode ser um atalho para **qualquer board da árvore**, mesmo em
  outro caminho (ex.: do card "Orçamento" direto para `Principal › Projeto B › Custos`). Clique direito no
  card › "Link para outro board..." (ou o botão de seta curva da barra flutuante) e escolha o destino na
  lista da árvore, que mostra o caminho de cada board. O card ganha uma seta curva ao lado do clipe;
  clicar nela abre o destino com o caminho completo no topo, e o botão voltar (`Alt+←`) leva de volta
  ao board de onde você saiu. Renomear o destino não quebra o link; se o destino for excluído, a seta
  aparece riscada em vermelho (desfazer a exclusão faz o link voltar a funcionar). No PDF o card vira
  link para a página do destino
- Ordem de empilhamento: escolher quais cards ficam por cima de quais, e essa ordem é **salva** (vale ao
  reabrir, no desfazer, no PDF e no PNG). "Trazer para frente" / "Enviar para trás" (um nível) e "Trazer
  para o topo" / "Enviar para o fundo", pelo clique direito, pelo botão de camadas da barra flutuante ou
  pelos atalhos. Com vários cards selecionados, a ordem entre eles se mantém. Clicar num card **não** o traz
  mais para frente; um card novo, colado ou duplicado entra no topo
- Travar card (cadeado): protege o card contra mudanças sem querer. Um card travado não se move (nem
  arrastado em grupo: os outros se movem e ele fica), não mostra as alças de redimensionar, não troca de
  formato e não é excluído pelo `Delete` (aparece um aviso: destrave antes). Continua livre para selecionar,
  editar o texto, mudar a cor e o tamanho do texto, ligar setas, abrir links, painéis e boards, e mudar a
  ordem. Clique direito › "Travar"/"Destravar", botão de cadeado da barra flutuante (tracejado se a seleção
  tem travados e destravados) ou `Ctrl+L`. Um cadeado pequeno aparece no canto do card (só na tela, não no
  PDF/PNG). Duplicar um card travado cria uma cópia destravada
- Seções (frames): uma área colorida com título que fica **atrás dos cards**, para organizá-los por tema
  (ferramenta Seção, tecla `S`). Arrastar a seção pela **borda** ou pelo **título** leva junto os cards que
  estão dentro dela (os que têm o centro dentro da área), inclusive os travados; soltar um card em cima de
  uma seção já o põe nela (é só geometria, não há vínculo salvo). O **meio vazio** da seção funciona como
  o fundo do canvas: caixa de seleção, criar card com a ferramenta ativa, clicar nos cards de dentro.
  Redimensionar a seção não mexe nos cards; excluir ou duplicar a seção age só nela. O título é o texto
  do card (duplo clique para editar, `Enter` termina), numa linha, com fonte fixa, e nunca faz a seção
  crescer. A cor usa a mesma paleta dos cards, em tom translúcido; a ordem entre seções muda pelas ações
  de ordem, mas elas ficam sempre atrás dos cards comuns e das setas. Seções não têm setas e podem ter
  link e cadeado (seção travada não se move e não leva nada). Aparecem atrás no PDF e no PNG, com o
  título buscável no PDF
- Menu de clique direito nos cards (clique sem arrastar; arrastar com o direito continua movendo a visão)
- Seleção múltipla: caixa de seleção, Shift+clique e mover/alterar/excluir em grupo
- Desfazer/refazer de todas as ações no board
- Barra de ferramentas: Selecionar, Retângulo, Elipse, Losango, Painel, Board aninhado, Seção, Imagem, Conexão
  (com a mini barra dos estilos de seta) e cor dos novos cards
- Barra flutuante da seleção: cor, formato, tamanho do texto, link e link para outro board (com um card selecionado),
  ordem (camadas), travar (cadeado), duplicar e excluir (só seções selecionadas: sem formato nem tamanho do texto)
- Salvar e abrir: cada board é salvo numa **pasta** (`board.json` + `imagens/`);
  saves antigos (um `.json` solto) continuam abrindo e viram pasta no primeiro "Salvar"
- Exportar para PDF navegável: uma página por board, cards de board aninhado e caminho do topo
  viram links, painéis flutuantes ganham páginas de "Detalhes", marcadores com a árvore de boards
- Exportar PNG do board que está na tela (o principal ou o aninhado aberto): imagem nítida
  (2 pixels por unidade, reduzida acima de 8.000 px), com fundo branco ou transparente
  (`Arquivo › Exportar PNG com fundo transparente...`)

## Atalhos de teclado

| Tecla | Ação |
|---|---|
| `V` | Selecionar |
| `R` / `E` / `L` | Criar retângulo / elipse / losango |
| `P` | Criar painel flutuante |
| `B` | Criar board aninhado |
| `S` | Criar seção (área com título, atrás dos cards) |
| Duplo clique no título da seção | Editar o título (`Enter` termina) |
| `I` | Inserir imagem (abre o seletor de arquivo) |
| `Ctrl+V` | Colar imagem (fora da edição de texto) |
| Duplo clique na imagem | Escrever a legenda |
| `Alt+←` | Voltar ao board anterior (depois de um link para outro board: ao board de onde você saiu) |
| `C` | Conexão |
| Clique na seta | Selecionar a seta |
| Duplo clique na seta ou no texto dela | Escrever o texto da seta (`Enter` confirma, `Esc` cancela) |
| `Delete` / `Backspace` | Excluir a seta selecionada; senão, a seleção de cards (cards travados ficam) |
| `Ctrl+D` | Duplicar seleção |
| `Ctrl+]` ou `Ctrl+PgUp` | Trazer para frente (um nível) |
| `Ctrl+[` ou `Ctrl+PgDn` | Enviar para trás (um nível) |
| `Ctrl+Shift+]` ou `Ctrl+Shift+PgUp` | Trazer para o topo |
| `Ctrl+Shift+[` ou `Ctrl+Shift+PgDn` | Enviar para o fundo |
| `Ctrl+L` | Travar / destravar a seleção |
| `Ctrl+A` | Selecionar tudo |
| `Ctrl+Z` | Desfazer |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Refazer |
| `Esc` | Sair da edição de texto → fechar painel flutuante → voltar para Selecionar → limpar seleção |
| `Espaço` + arrastar | Mover a visão (também: botão do meio ou direito) |
| `Shift` + clique | Somar/retirar card da seleção |
| Clique direito no card (sem arrastar) | Menu do card: adicionar, editar, abrir ou remover o link; escolher, trocar, ir para ou remover o link para outro board; ordem de empilhamento; travar/destravar |
| Clique no clipe do card | Abrir o link no navegador |
| Clique na seta curva do card | Ir para o board de destino |
| Na lista de boards | Setas para escolher, `Enter` confirma, `Esc` cancela |
| `Alt` + arrastar card ou alça | Mover/redimensionar sem alinhamento automático |
| `Shift` + arrastar canto | Redimensionar mantendo a proporção (retângulo) |
| `Ctrl+N` / `Ctrl+O` | Novo board / abrir |
| `Ctrl+S` / `Ctrl+Shift+S` | Salvar / salvar como |
| `Ctrl+E` | Exportar PDF (todos os boards) |
| `Ctrl+Shift+E` | Exportar PNG do board atual (fundo branco) |
| `Ctrl+0` | Redefinir visualização |

Enquanto o texto de um card está sendo editado, as teclas vão para o texto (inclusive `Ctrl+Z`,
que desfaz dentro do próprio texto, e `Ctrl+V`, que cola no texto); só o `Esc` age como atalho. Ao sair da edição, tudo o que foi
digitado vira um único passo no desfazer do board.

Os atalhos com colchetes funcionam pelo **caractere** da tecla: no teclado ABNT2 são as teclas `[` e `]`
de sempre (sem `AltGr`). `Ctrl+PgUp`/`Ctrl+PgDn` fazem o mesmo em qualquer teclado (nos notebooks, às
vezes com `Fn`).

## Salvar e abrir

Cada board é salvo numa **pasta**:

```
MeuBoard/
  board.json     o board, com todos os boards aninhados
  imagens/       arquivos de imagem dos cards (a pasta só aparece quando houver a primeira)
```

- As imagens inseridas entram na pasta `imagens/` ao **salvar**. Ao salvar, imagens que nenhum card usa
  (nem o desfazer pode trazer de volta) são apagadas dessa pasta.
- Se uma imagem sumir da pasta, o card mostra "Imagem não encontrada" e guarda a referência: devolver o
  arquivo faz a imagem voltar.

- **Salvar como** (`Ctrl+Shift+S`): o nome digitado vira o nome da pasta.
- Links dos cards ficam no `board.json`; um endereço que não seja `http(s)` (arquivo editado à mão)
  é descartado ao abrir. O link para outro board guarda o id do board de destino (mantido ao abrir
  mesmo que o destino não exista mais).
- **Abrir** (`Ctrl+O`): escolha o `board.json` dentro da pasta do board.
- **Saves antigos** (um `.json` solto) abrem normalmente. O primeiro "Salvar" pede a pasta nova e não
  mexe no arquivo antigo.
- Para copiar ou mover um board, leve a **pasta inteira**.

## Estrutura

```
src/main/java/com/danilo/boardvisual
├─ model/        Card, Connection, Board — dados puros, sem nada visual
├─ alignment/    Geometria de caixas: alinhamento e redimensionamento — Java puro
├─ view/         Nós JavaFX: canvas, cards, setas, barras
├─ controller/   Interações de mouse e ações sobre o board
├─ persistence/  Leitura e escrita do save (pasta com board.json e imagens/)
└─ export/       Exportação para PDF (Apache PDFBox) e PNG
src/main/resources/.../styles/app.css   Estilo visual (paleta e espaçamentos)
```

O modelo usa propriedades observáveis do JavaFX: a view faz binding nelas,
então mover um card no modelo atualiza o card e as setas na tela sem código
de redesenho.

## Stack

Java 17 · JavaFX 21 · Gson · JUnit 5
