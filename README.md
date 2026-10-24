# jardiff

Diff de **comportamento** entre duas versões de um `.jar`: compara o bytecode e mostra o que mudou na lógica, não só no texto.

## Uso

```bash
mvn package
java -jar target/jardiff.jar v1.jar v2.jar -o relatorio.html   # gera relatorio.html + relatorio.json
```

Opções: `-o/--output` (HTML), `--json` (caminho do JSON), `-h`. Códigos de saída: 0 ok, 1 uso incorreto, 2 erro de arquivo.
Exemplo pronto: `samples/v1.jar` vs `samples/v2.jar` (regenere com `sh samples/build.sh`) e [docs/exemplo-relatorio.html](docs/exemplo-relatorio.html).

## GitHub Action

Roda o jardiff no pull request, comenta o resumo, anexa o relatório HTML e **falha o build** se aparecer risco novo.

```yaml
- uses: SEU_USUARIO/jardiff@v1
  with:
    old-jar: base/target/app.jar        # ou: artifact + old-version + new-version (Maven Central)
    new-jar: head/target/app.jar
    fail-on-risk: alto                  # alto | medio | nenhum
```

Exemplos completos em [`examples/`](examples/): diff base vs PR e auditoria de dependência. A CLI ganhou `--markdown` e `--fail-on-risk` (código de saída 3).

## Como funciona

1. **Camada 1:** SHA-256 de cada entrada do jar → adicionada / removida / alterada.
2. **Camada 2:** classes alteradas são lidas com ASM; compara método a método ignorando números de linha, nomes de variáveis locais, frames e ordem do constant pool (saltos viram deslocamentos relativos).
3. **Classificação:** lógica alterada, chamada nova, condição trocada, método adicionado/removido, cosmética (ignorada, só listada).
4. **Risco novo na v2** (só instruções adicionadas): rede, reflexão, exec, acesso a arquivos, serialização.

## Diff de texto vs jardiff (samples v1 → v2)

| | `javap -c -p -l` + `diff` | jardiff |
|---|---|---|
| Linhas de diferença | 178 | 4 métodos alterados + 1 classe nova |
| Renomear variável local | aparece como mudança | listada como cosmética, ignorada |
| Linhas deslocadas | tudo muda de offset | ignorado |
| "Chamou rede?" | você procura na mão | risco **rede** em `Frete.calcular` |
| "Usa reflexão?" | idem | risco **reflexão** em `Pedido.total` |

Screenshot: abra `docs/exemplo-relatorio.html` no navegador e salve como `docs/report.png` (não gerei a imagem aqui).

## Limites conhecidos

Anotações, InnerClasses e multi-release jars não são comparados; métodos com mais de ~2000 instruções viram diff total sem LCS.

## Licença

MIT
