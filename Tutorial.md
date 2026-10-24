# Tutorial do jardiff (para quem está começando)

## 1. O que é isso, em uma frase

Você tem **duas versões do mesmo programa Java**. O jardiff diz **o que mudou no jeito como ele funciona**, ignorando as mudanças que não importam.

## 2. Por que isso é útil? (sem jargão)

Imagine que você usa uma biblioteca de terceiros no seu projeto, por exemplo o Gson, que converte objetos em JSON. Um dia sai uma versão nova e você pensa em atualizar. Perguntas normais:

- "A nova versão mexeu em alguma coisa importante?"
- "Ela passou a acessar a internet sem me avisar?"
- "Removeram algo que meu código usa?"

Você quase nunca tem o código-fonte, só o arquivo pronto (`.jar`). E o texto de "novidades" do autor pode não contar tudo. Isso já foi usado em ataques reais: alguém compromete uma biblioteca e coloca código que envia dados para fora.

É como comparar duas receitas de bolo prontas: um diff comum diria "mudou a ordem das palavras, a letra, a fonte". O jardiff diz "agora leva 2 ovos em vez de 1 e vai ao forno 10 minutos a mais". E avisa: "essa versão nova **liga para um número de telefone**".

## 3. Três palavras que você precisa conhecer

- **`.jar`:** um pacote (tipo um `.zip`) com programas Java já prontos para rodar. É assim que bibliotecas Java são distribuídas.
- **Bytecode:** o que o Java gera quando "compila" seu código. É o que existe dentro do `.jar`.
- **Risco:** uma coisa nova que o programa passou a fazer e que merece atenção: acessar a rede, rodar comandos, mexer em arquivos, usar reflexão ou serialização.

## 4. Experimente em 5 minutos

### O que instalar
- **Java 17 ou mais novo.** Teste no terminal: `java -version`.
- **Maven.** Teste: `mvn -version`.

### Passo a passo
1. Extraia o `jardiff.zip` e abra a pasta `jardiff` num terminal (no IntelliJ: abra a pasta e use a aba **Terminal**).
2. Gere o programa:
   ```bash
   mvn package
   ```
   Ao final, existe `target/jardiff.jar`.
3. Compare os jars de exemplo que já vêm no projeto:
   ```bash
   java -jar target/jardiff.jar samples/v1.jar samples/v2.jar -o relatorio.html
   ```
4. No terminal aparece o resumo. Abra o arquivo `relatorio.html` com duplo clique.

### O que você vai ver no terminal
```
~ loja.Frete
    calcular(double): double -> lógica alterada, chamada nova  [risco: rede]
~ loja.Pedido
    ≈ subtotal(): double -> cosmética (ignorada)
    resumo(): java.lang.String -> método removido
```
Leitura em português:
- `+` classe nova · `−` classe removida · `~` classe alterada · `≈` mudança sem efeito.
- **`Frete.calcular` passou a usar a rede.** É o alerta que importa.
- **`Pedido.subtotal` só teve uma variável renomeada**, sem efeito, e foi ignorada.
- `resumo()` foi apagado: quem usava vai quebrar.

## 5. Como ler o relatório (`relatorio.html`)

1. **Cartões no topo:** quantidade de classes novas, removidas e alteradas, e quantas têm risco. **Clique num cartão** para filtrar.
2. **Mapa de calor:** quadradinhos coloridos; quanto mais vermelho, mais coisa mudou naquela classe. Clique para abrir.
3. **Caixa de busca e filtros:** digite o nome de uma classe ou método.
4. **Lista da esquerda:** as classes, em pastas (pacotes). Clique numa classe.
5. **Painel da direita:** para cada método alterado, duas colunas: **esquerda = versão antiga, direita = versão nova**. Vermelho = saiu, verde = entrou. São instruções de bytecode (`INVOKEVIRTUAL ...` é "chamar um método"): você não precisa entender cada linha, os **rótulos coloridos no topo de cada método** já dizem o essencial.
6. **Botão ☾** (canto superior direito): modo escuro.

## 6. Usar com bibliotecas de verdade

Baixe dois `.jar` da biblioteca (por exemplo em https://search.maven.org, botão *Downloads → jar*) e rode:
```bash
java -jar target/jardiff.jar gson-2.10.jar gson-2.11.0.jar -o gson.html
```
Se aparecerem muitas mudanças, comece pelo filtro **Risco: Alto**.

## 7. Automático no GitHub (opcional)

Se seu projeto está no GitHub, dá para rodar o jardiff a cada pull request e receber um comentário com o resumo. O passo a passo está na seção "GitHub Action" do [README.md](README.md) e nos exemplos da pasta `examples/`.

## 8. Dúvidas comuns

**"Apareceu `lógica alterada` em tudo, e agora?"**
Normal em bibliotecas grandes. Olhe primeiro o risco (alto e médio) e os métodos removidos.

**"Risco alto significa que a biblioteca é maliciosa?"**
Não. Significa que **algo novo** passou a acessar rede, `exec` ou serialização. Muitas bibliotecas legítimas fazem isso por bons motivos. O alerta serve para você olhar com atenção.

**"E se o risco já existia na versão antiga?"**
Não é reportado. O jardiff mostra só o que é **novo**.

**"Preciso saber bytecode?"**
Não. Os rótulos resolvem a maior parte. O diff de bytecode é para quando você quiser ver o detalhe.

**"Deu erro `mvn: command not found`."**
O Maven não está instalado. No IntelliJ, você também pode abrir o `pom.xml` e usar o painel **Maven → Lifecycle → package**.
