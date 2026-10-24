# jardiff

Diff de **comportamento** entre duas versões de um `.jar`: compara o bytecode e mostra o que mudou na lógica, não só no texto.

> Novo no assunto? Comece pelo **[TUTORIAL.md](TUTORIAL.md)**.

## Por que existe

Quando você atualiza uma biblioteca (`gson 2.10 → 2.11`), normalmente só tem o `.jar`, sem o código-fonte e com um changelog que pode não contar tudo. Um diff de texto do bytecode desassemblado é ilegível: variáveis renomeadas, linhas deslocadas e reordenação interna viram centenas de linhas de ruído. O jardiff descarta esse ruído e responde:

- Qual método mudou de **lógica**?
- Alguma condição (`if`) foi trocada?
- A nova versão passou a **chamar rede, reflexão, `exec`, arquivos ou serialização**?
- Algum método público **sumiu**?

## Instalação

Requisitos: Java 17+ e Maven.

```bash
mvn package                      # gera target/jardiff.jar (com os testes)
java -jar target/jardiff.jar v1.jar v2.jar -o relatorio.html
```

Jars de exemplo prontos: `samples/v1.jar` e `samples/v2.jar` (regenere com `sh samples/build.sh`).
Exemplo de relatório: [docs/exemplo-relatorio.html](docs/exemplo-relatorio.html).

## Referência da CLI

```
java -jar jardiff.jar <v1.jar> <v2.jar> [opções]
```

| Opção | Efeito |
|---|---|
| `-o`, `--output <arq>` | HTML de saída (padrão `relatorio.html`) |
| `--json <arq>` | JSON de saída (padrão: mesmo nome do HTML, `.json`) |
| `--markdown <arq>` | resumo em Markdown (usado nos comentários de PR) |
| `--fail-on-risk alto\|medio\|nenhum` | sai com código 3 se o risco **novo** atingir o nível (`nenhum` = nunca falha) |
| `-h`, `--help` | ajuda |

Códigos de saída: `0` ok · `1` uso incorreto · `2` erro de arquivo · `3` risco acima do limite.

## Como funciona

1. **Camada 1, entradas do jar.** SHA-256 de cada arquivo: adicionado, removido ou alterado.
2. **Camada 2, bytecode (ASM).** Cada classe alterada vira uma lista de "membro → instruções normalizadas". Fica de fora:
    - números de linha, frames e ordem do constant pool;
    - nomes de variáveis locais (só detectados para listar como cosmética);
    - saltos viram **deslocamentos relativos** (`IFLE +5`), então inserir código não "move" saltos que não cruzam a inserção;
    - ruído do compilador: `$values()`, `access$NNN`, métodos *bridge*, `$deserializeLambda$` e o `<clinit>` de enums.
3. **Diff** por maior subsequência comum (LCS) entre as instruções de cada membro.
4. **Classificação** de cada membro alterado:

| Tipo | Quando |
|---|---|
| chamada nova | uma instrução `INVOKE*` aparece na v2 sem equivalente exato na v1 |
| condição trocada | a sequência de saltos condicionais (`IF*`) mudou |
| lógica alterada | qualquer outra instrução mudou (constantes, operações, estrutura) |
| método adicionado / removido | o membro só existe de um lado |
| cosmética (ignorada) | o bytecode é idêntico; só mudou nome de variável local, linha ou constant pool |

5. **Risco novo**, olhando só as instruções **adicionadas** na v2 (risco que já existia não é reportado):

| Risco | Nível | Exemplos |
|---|---|---|
| rede | alto | `java.net.*`, `javax.net.*` |
| exec | alto | `Runtime.exec`, `ProcessBuilder` |
| serialização | alto | `ObjectInputStream`, `ObjectOutputStream`, `XMLDecoder` |
| reflexão | médio | `java.lang.reflect.*`, `Class.forName`, `getMethod`, `ClassLoader` |
| acesso a arquivos | médio | `File`, `FileInputStream`, `java.nio.file.*` |

O nível de uma classe é o pior risco entre seus métodos.

## O relatório HTML

Arquivo único, sem dependências externas, funciona offline.

- **Cards de resumo** no topo: classes por status, mudanças por tipo e risco (clique num card para filtrar).
- **Mapa de calor:** classes mais alteradas (mais quente = mais linhas mudadas).
- **Filtros** por risco e tipo, **busca** por classe ou método, **modo escuro**.
- **Árvore** de pacotes e classes (`+` adicionada, `−` removida, `~` alterada, `≈` só cosmética).
- **Diff lado a lado** do bytecode por método, com as instruções alteradas destacadas.

## JSON

`relatorio.json` contém `summary` (contadores), `meta` (rótulos), `entries` (camada 1) e `classes[]` com `status`, `risk`, `score`, `cosmetic` e `members[]` (`kinds`, `risks`, `diff[] = {op, text}`). `op` é `' '` (igual), `'-'` (só na v1) ou `'+'` (só na v2).

## GitHub Action

Roda o jardiff no pull request, comenta o resumo, anexa o relatório HTML e **falha o build** se aparecer risco novo.

```yaml
- uses: SEU_USUARIO/jardiff@v1
  with:
    old-jar: base/target/app.jar        # ou: artifact + old-version + new-version (Maven Central)
    new-jar: head/target/app.jar
    fail-on-risk: alto
```

| Input | Padrão | Descrição |
|---|---|---|
| `old-jar`, `new-jar` | | caminhos dos jars (modo arquivo) |
| `artifact`, `old-version`, `new-version` | | `groupId:artifactId` do Maven Central (modo coordenada) |
| `fail-on-risk` | `alto` | `alto`, `medio` ou `nenhum` |
| `comment` | `true` | comenta no PR (precisa de `pull-requests: write`) |

Exemplos prontos em [`examples/`](examples/): diff base × PR e auditoria de dependência.

## Estrutura do projeto

```
src/main/java/dev/jardiff/
  Main.java        CLI
  JarIndex.java    camada 1 (hashes)
  Normalizer.java  camada 2 (ASM -> instruções normalizadas)
  JarDiff.java     diff, classificação
  RiskScanner.java categorias de risco
  Reports.java     HTML, JSON, Markdown e terminal
  Json.java        escritor de JSON mínimo
  Model.java       tipos de dados
src/main/resources/report.html   template do relatório
src/test/java/...                testes (compilam os jars de samples/ na hora)
samples/                         v1/v2 da "calculadora de pedidos"
action.yml                       GitHub Action
```

## Diff de texto vs jardiff (samples v1 → v2)

| | `javap -c -p -l` + `diff` | jardiff |
|---|---|---|
| Linhas de diferença | 178 | 4 métodos alterados + 1 classe nova |
| Renomear variável local | aparece como mudança | cosmética, ignorada |
| Linhas deslocadas | tudo muda de offset | ignorado |
| "Chamou rede?" | você procura na mão | risco **rede** em `Frete.calcular` |
| "Usa reflexão?" | idem | risco **reflexão** em `Pedido.total` |

## Limites conhecidos

- Anotações, `InnerClasses` e jars multi-release não são comparados.
- Métodos com mais de ~2000 instruções viram diff total, sem LCS.
- Só o `<clinit>` de enums é ignorado; se os argumentos das constantes de um enum mudarem, o jardiff não vê.
- Detectar variável local renomeada exige jar compilado com `-g`; sem isso o nome nem existe no bytecode (e, portanto, já não gera ruído).
- Código ofuscado, ou compilado por javac muito diferente entre as versões, gera mais "lógica alterada" do que o autor realmente mudou.
- O risco é por **nome de classe/método chamado**; não analisa fluxo de dados nem chamadas indiretas.

## Licença

MIT