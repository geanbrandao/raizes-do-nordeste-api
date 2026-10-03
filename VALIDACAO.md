# Como subir e validar a API

Passo a passo para levantar o ambiente e conferir que tudo funciona.
Vira a base da seção de execução do README na entrega final.

> **Organização deste documento.** As seções 4 e 5 espelham as pastas que a coleção
> Postman/Insomnia vai ter (Auth, Usuários, Unidades, Produtos, Cardápio, … e Erros).
> Cada etapa nova acrescenta uma subseção em **4. Fluxos por recurso** e alguns casos em
> **5. Erros** — assim, montar a coleção na Etapa 10 vira transcrição.

Pré-requisitos: Docker instalado e rodando, `jq` disponível, portas 8080 e 5432 livres.
Java 17 só é necessário para a seção 0. Como instalar o Docker em cada sistema: ver o
[README](README.md#instalando-o-docker).

> **Em que terminal rodar.** Os comandos deste documento são de shell POSIX. No macOS e no
> Linux, qualquer terminal serve. No **Windows**, use o **Git Bash** (vem com o instalador do
> Git) ou o **WSL** — no PowerShell, `curl` e apelido do `Invoke-WebRequest` e tem outra
> sintaxe, entao os comandos nao funcionam como estao escritos.

> **Regra: toda validacao comeca com banco limpo.** As secoes 4.2, 4.5 e 4.6 alteram
> preco, estoque e cadastro de proposito — e o que elas demonstram. Os valores esperados
> no resto do documento partem do seed, entao uma base reaproveitada faz a conta nao
> fechar sem que exista nada errado na API. Se voce parou no meio e vai retomar, nao
> continue de onde parou: recrie o banco e comece da secao 1.

**Estado atual:** 11 controllers, 30 rotas, 38 operações HTTP, 196 testes automatizados.

Este roteiro foi executado de ponta a ponta contra Postgres real, em banco criado do zero.

---

## 0. Validação sem banco (roda agora, sem Docker)

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && ./gradlew test
```

Esperado: `BUILD SUCCESSFUL`, 196 testes, 0 falhas.

Os testes usam H2 em modo PostgreSQL com as migrations reais aplicadas pelo Flyway.
Provam a coerência entre migrations, entidades e seed — mas **não** substituem uma
execução contra Postgres de verdade, que é o que os passos abaixo fazem.

---

## 1. Subir o ambiente

O daemon do Docker precisa estar rodando. Abra o Docker Desktop (ou `open -a Docker`)
e espere o ícone da baleia ficar estável.

```bash
docker info >/dev/null 2>&1 && echo "docker pronto" || echo "docker ainda subindo"
```

Com o daemon no ar, suba **sempre apagando o banco anterior**:

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down -v && docker compose up --build
```

O `-v` e a parte que importa: ele apaga o volume do Postgres. Sem ele, `down` e `up`
preservam o banco, e voce recomeca sobre os dados da validacao anterior — precos
alterados, estoque consumido, usuarios de teste. Com ele, o Flyway recria o schema e
reaplica o seed, e todo numero esperado neste documento passa a valer.

Rodar isso num banco que ainda nao existe tambem funciona: o `down -v` nao reclama de
volume ausente. Ou seja, e sempre o comando certo para comecar, inclusive na primeira vez.

O primeiro build demora alguns minutos: o Dockerfile roda `./gradlew bootJar` dentro do
container e baixa o Gradle e as dependências do zero. As próximas vezes usam cache.

Deixe esse terminal aberto mostrando o log e use outro para os comandos seguintes.

Se preferir nao ocupar um terminal com o log, troque o `up --build` por `up --build -d` e
acompanhe com `docker compose logs -f app` quando precisar.

**O que procurar no log**, nessa ordem:

| Sinal | Significa |
|---|---|
| `database system is ready to accept connections` | Postgres no ar |
| `Successfully applied 18 migrations` | Flyway criou o schema e aplicou o seed |
| `Codigo de verificação FIXO ligado (258369)` | Perfil dev ativo, código previsível |
| `Tomcat started on port 8080` | API no ar |
| `Started RaizesApiApplication` | Subiu inteira |

Se aparecer erro de `Schema-validation`, é divergência entre migration e entidade —
exatamente o que este passo existe para pegar.

---

## 2. Conferir que subiu

Antes de qualquer chamada, defina este atalho na aba do terminal que voce vai usar:

```bash
api() { curl -s -w '%{stderr}HTTP %{http_code}\n' "$@"; }
```

Ele e o `curl` de sempre, com uma diferenca: **imprime o status HTTP na tela** e manda
so o corpo da resposta para o `jq` ou para a variavel. E o que deixa conferir os status
que cada secao promete (201, 422, 409...) sem atrapalhar os comandos que guardam o
resultado em variavel, como `PEDIDO=$(api ...)`.

O `api` vive so na aba onde foi definido, igual aos tokens da secao 4.0 — abriu outra
aba, defina de novo.

```bash
api localhost:8080/actuator/health | jq
```

Esperado: `{"status":"UP"}`

```bash
docker compose exec postgres psql -U raizes_user -d raizes -c "\dt"
```

Esperado: 16 tabelas (`unidades`, `usuarios`, `produtos`, `cardapio_unidade`, `estoque`,
`movimentacoes_estoque`, `pedidos`, `itens_pedido`, `pagamentos`, `contas_fidelidade`,
`movimentacoes_pontos`, `consentimentos`, `logs_auditoria`, `campanhas`, `refresh_tokens`,
`tokens_verificacao_email`) mais a `flyway_schema_history`.

```bash
docker compose exec postgres psql -U raizes_user -d raizes \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Esperado: 18 linhas, todas com `success = t`.

```bash
docker compose exec postgres psql -U raizes_user -d raizes \
  -c "SELECT perfil, count(*) FROM usuarios GROUP BY perfil ORDER BY perfil;"
```

Esperado: ADMIN 1, ATENDENTE 1, CLIENTE 1, COZINHA 1, GERENTE 2.

---

## 3. Swagger

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI cru:** http://localhost:8080/v3/api-docs

Como testar por lá:

1. `POST /auth/login` → **Try it out** → `cliente@exemplo.com` / `Senha@123` → **Execute**
2. Copie o `accessToken` da resposta
3. Botão **Authorize** no topo → cole o token → **Authorize**
4. `GET /usuarios/me` → **Execute** → deve devolver 200

Conferência de que a documentação reflete as rotas reais:

```bash
api localhost:8080/v3/api-docs | jq -r '.paths | keys[]'
```

Esperado (30 rotas):

```
/auth/login          /auth/logout         /auth/refresh
/usuarios            /usuarios/me         /usuarios/operadores
/usuarios/verificacao                     /usuarios/verificacao/reenvio
/unidades            /unidades/{unidadeId}
/produtos            /produtos/{produtoId}
/unidades/{unidadeId}/cardapio            /unidades/{unidadeId}/cardapio/{produtoId}
/unidades/{unidadeId}/estoque             /unidades/{unidadeId}/estoque/movimentacoes
/unidades/{unidadeId}/estoque/{produtoId}/movimentacoes
/pedidos             /pedidos/{pedidoId}
/pedidos/{pedidoId}/status                /pedidos/{pedidoId}/cancelamento
/pedidos/{pedidoId}/pagamentos            /pagamentos/{pagamentoId}
/pagamentos/callback
/fidelidade/saldo    /fidelidade/extrato       /fidelidade/resgates
/consentimentos      /consentimentos/{consentimentoId}
/auditoria
```

---

## 4. Fluxos por recurso

> **Os numeros esperados daqui para baixo valem para um banco recem-semeado.** Preco,
> saldo de estoque e contagem de usuarios sao estado, e as secoes 4.2, 4.5 e 4.6 alteram
> esse estado de proposito — e justamente o que elas demonstram. Rodar as secoes fora de
> ordem, ou repetir a validacao sobre uma base ja usada, muda os valores sem que nada
> esteja errado na API.
>
> Se os numeros nao baterem, comece recriando o banco:
>
> ```bash
> docker compose down -v && docker compose up -d
> ```
>
> O `-v` apaga o volume do Postgres; as migrations recriam o schema e o seed. Tudo o que
> foi criado na validacao anterior se perde, o que e o objetivo.

### Usuários do seed (senha `Senha@123` para todos)

| E-mail | Perfil | Unidade |
|---|---|---|
| `admin@raizes.com.br` | ADMIN | — |
| `gerente.recife@raizes.com.br` | GERENTE | Recife |
| `atendente.recife@raizes.com.br` | ATENDENTE | Recife |
| `cozinha.recife@raizes.com.br` | COZINHA | Recife |
| `gerente.caruaru@raizes.com.br` | GERENTE | Caruaru |
| `cliente@exemplo.com` | CLIENTE | — |

### Ids fixos do seed

| O quê | Id |
|---|---|
| Unidade Recife (COMPLETA) | `10000000-0000-0000-0000-000000000001` |
| Unidade Caruaru (REDUZIDA) | `10000000-0000-0000-0000-000000000002` |
| Tapioca de queijo coalho | `30000000-0000-0000-0000-000000000001` |
| Bolo de rolo (saldo baixo: 2) | `30000000-0000-0000-0000-000000000006` |

### 4.0 Preparar os tokens — rode isto primeiro

> **Obrigatório antes das seções 4.2 em diante e da seção 5.** As variáveis vivem só na
> sessão do terminal em que foram definidas: se você abrir outra aba, ou o access token
> passar dos 15 minutos de validade, rode este bloco de novo.

```bash
# Mesmo atalho da secao 2: status na tela, corpo para o jq.
api() { curl -s -w '%{stderr}HTTP %{http_code}\n' "$@"; }

login() { api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"senha\":\"Senha@123\"}" | jq -r '.accessToken // empty'; }

TOKEN=$(login cliente@exemplo.com)
ADMIN=$(login admin@raizes.com.br)
GERENTE=$(login gerente.recife@raizes.com.br)
RECIFE=10000000-0000-0000-0000-000000000001
CARUARU=10000000-0000-0000-0000-000000000002
TAPIOCA=30000000-0000-0000-0000-000000000001

# Confere na hora, em vez de deixar o erro aparecer tres comandos depois.
for nome in TOKEN ADMIN GERENTE; do
  case $nome in TOKEN) v=$TOKEN;; ADMIN) v=$ADMIN;; GERENTE) v=$GERENTE;; esac
  if [ -n "$v" ]; then echo "  $nome ok"; else echo "  $nome FALHOU - a API esta no ar?"; fi
done
```

Se algum sair como `FALHOU`, pare aqui: ou a API não subiu, ou o banco está sem o seed.
Confira com `api localhost:8080/actuator/health`.

---

### 4.1 Auth

**Login (200)**

```bash
api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq
```

Esperado: `accessToken`, `refreshToken`, `tokenType: "Bearer"`, `expiresIn: 900`,
`usuario.perfil: "CLIENTE"`. Nenhum campo de senha aparece na resposta.

**Perfil autenticado (200)**

```bash
api localhost:8080/usuarios/me -H "Authorization: Bearer $TOKEN" | jq
```

**Rotação do refresh token**

```bash
REFRESH=$(api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq -r .refreshToken)
echo "--- primeira renovacao (espera um token):"
api -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
echo "--- reusando o mesmo refresh (espera TOKEN_INVALIDO):"
api -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
```

O segundo tem que falhar. É a rotação funcionando: refresh usado não vale mais.

Se o token renovado sair identico ao do login, não e defeito: o JWT carrega o instante
de emissão em segundos, e as duas chamadas cairam no mesmo segundo. O que importa aqui e
a segunda tentativa falhar.

---

### 4.2 Usuários — cadastro e verificação de e-mail (202 → 204 → 200)

O cadastro devolve **sempre a mesma resposta**, exista ou não o e-mail. A conta nasce
pendente e só loga depois de confirmar o código.

> **Em desenvolvimento o código é sempre `258369`.** Ele também aparece no log
> (`docker compose logs app | grep VERIFICACAO`). Em produção a chave
> `app.verificacao-email.codigo-fixo` fica vazia e o código passa a ser sorteado.

```bash
# 1. cadastrar -> 202 generico
api -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"joana@exemplo.com","senha":"Senha@123"}' | jq

# 2. tentar logar antes de confirmar -> 403 EMAIL_NAO_VERIFICADO
api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq -r '.accessToken // .error'

# 3. confirmar com o codigo de dev -> 204
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/usuarios/verificacao \
  -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"258369"}'

# 4. agora loga -> 200
api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq -r '.accessToken // .error'
```

**Prova da proteção contra enumeração** — as duas respostas são idênticas:

```bash
echo "--- email novo:"; api -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Fulano","email":"novo.endereco@exemplo.com","senha":"Senha@123"}' | jq -c
echo "--- email existente:"; api -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Outra Maria","email":"cliente@exemplo.com","senha":"Senha@123"}' | jq -c
```

**Cadastro de operador (201, só ADMIN e GERENTE)**

```bash
api -X POST localhost:8080/usuarios/operadores -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Novo Atendente\",\"email\":\"novo.atendente@raizes.com.br\",\"senha\":\"Senha@123\",\"perfil\":\"ATENDENTE\",\"unidadeId\":\"$RECIFE\"}" | jq
```

Operador nasce já verificado — foi criado por alguém de confiança, então loga direto.

---

### 4.3 Unidades

> Precisa das variáveis da seção **4.0**.

**Listagem pública e paginada (200)**

```bash
api "localhost:8080/unidades?page=1&limit=10" | jq
```

Esperado: envelope com `conteudo`, `pagina: 1`, `limite: 10`, `totalItens: 2`,
`totalPaginas: 1`, `primeira: true`, `ultima: true`.

**Paginação de verdade**

```bash
api "localhost:8080/unidades?page=1&limit=1" | jq '{pagina,totalPaginas,ultima,itens:(.conteudo|length)}'
api "localhost:8080/unidades?page=2&limit=1" | jq '{pagina,ultima}'
```

**Detalhe (200)**

```bash
api localhost:8080/unidades/$RECIFE | jq
```

**Cadastrar unidade (201, só ADMIN)**

```bash
api -X POST localhost:8080/unidades -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Raizes Olinda","cidade":"Olinda","uf":"pe","tipoOperacao":"REDUZIDA"}' | jq
```

Repare que a UF volta em maiúscula mesmo enviada minúscula — normalização no service.

---

### 4.4 Produtos

> Precisa das variáveis da seção **4.0**.

**Catálogo da rede (200, exige token)**

```bash
api "localhost:8080/produtos?page=1&limit=5" -H "Authorization: Bearer $TOKEN" | jq '{totalItens,totalPaginas,nomes:[.conteudo[].nome]}'
```

Esperado: `totalItens: 10`.

**Filtro por categoria (aceita minúscula)**

```bash
api "localhost:8080/produtos?categoria=bebida" -H "Authorization: Bearer $TOKEN" | jq '{totalItens,nomes:[.conteudo[].nome]}'
```

Esperado: 3 bebidas.

**Cadastrar produto (201, ADMIN ou GERENTE)**

```bash
api -X POST localhost:8080/produtos -H "Authorization: Bearer $GERENTE" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Pamonha","categoria":"milho","precoBase":9.50,"sazonal":true}' | jq
```

**Inativar produto (204, só ADMIN) — soft delete**

```bash
BOLO=30000000-0000-0000-0000-000000000006
curl -s -o /dev/null -w "inativar: %{http_code}\n" -X DELETE localhost:8080/produtos/$BOLO \
  -H "Authorization: Bearer $ADMIN"
echo "--- sumiu da listagem:"
api "localhost:8080/produtos?limit=100" -H "Authorization: Bearer $ADMIN" \
  | jq '{totalItens, boloNaLista: ([.conteudo[].nome] | any(. == "Bolo de rolo"))}'
echo "--- mas continua existindo:"
api localhost:8080/produtos/$BOLO -H "Authorization: Bearer $ADMIN" | jq '{nome,ativo}'
```

Esperado: `boloNaLista: false` e `totalItens: 10`.

O `10` aqui e coincidencia e nao prova nada: eram 10 produtos do seed, a Pamonha do passo
anterior virou 11, e inativar o bolo devolveu a contagem para 10. Por isso o comando
pergunta direto se o bolo esta na lista, em vez de confiar no total.

O produto sai da listagem mas continua acessível por id, com `ativo: false`. Apagar de
verdade quebraria todo pedido antigo que aponta para ele.

---

### 4.5 Cardápio por unidade

> Precisa das variáveis da seção **4.0**.

Esta é a parte que mostra na prática que **nem toda loja da rede é igual**.

**Consulta pública (200)**

```bash
api localhost:8080/unidades/$RECIFE/cardapio | jq '[.[] | {nome,preco,categoria}]'
```

**Unidade COMPLETA vende mais que a REDUZIDA**

```bash
echo "Recife (COMPLETA):  $(api localhost:8080/unidades/$RECIFE/cardapio | jq 'length') itens"
echo "Caruaru (REDUZIDA): $(api localhost:8080/unidades/$CARUARU/cardapio | jq 'length') itens"
```

Esperado: 10 e 6.

**Mesmo produto, preço diferente em cada loja**

```bash
for u in $RECIFE $CARUARU; do
  api localhost:8080/unidades/$u/cardapio \
    | jq -r --arg u "$u" '.[] | select(.nome=="Tapioca de queijo coalho") | "\($u): R$ \(.preco)"'
done
```

**Gerente ajusta o preço da própria loja (200)**

```bash
api -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":15.50,"disponivel":true}' | jq '{nome,preco,disponivel}'
```

**Tirar item do ar sem apagar o cadastro**

```bash
api -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":15.50,"disponivel":false}' > /dev/null
echo "--- visao do cliente:    $(api localhost:8080/unidades/$RECIFE/cardapio | jq 'length') itens"
echo "--- visao da operacao:   $(api "localhost:8080/unidades/$RECIFE/cardapio?incluirIndisponiveis=true" | jq 'length') itens"
```

O item some para o cliente e continua visível para quem administra a loja.

**Desfaca o que esta secao mudou, antes de seguir**

Os dois comandos acima deixaram a tapioca de Recife a R$ 15,50 e indisponivel. Volte ao
valor do seed:

```bash
api -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":12.90,"disponivel":true}' | jq '{nome,preco,disponivel}'
```

Esperado: 200, `preco: 12.90`, `disponivel: true`.

> **Nao pule este passo.** A secao 4.7 monta o pedido com essa mesma tapioca e confere os
> valores contra o preco do seed. Com o item indisponivel, o `POST /pedidos` devolve 422
> `PRODUTO_FORA_DO_CARDAPIO`; com o preco em 15,50, o total sai 31,00 em vez de 25,80. Nos
> dois casos parece defeito da API, quando e so o estado que esta secao deixou para tras.

---

### 4.6 Estoque

> Precisa das variáveis da seção **4.0**.

Estoque é informação da operação, não da vitrine: cliente não tem acesso nenhum.

**Saldo da unidade (200)**

```bash
api "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq '[.conteudo[] | {nome,saldoAtual,abaixoDoMinimo}]'
```

O Bolo de rolo vem com `saldoAtual: 2` e `abaixoDoMinimo: true` — o seed deixa esse item
propositalmente baixo, para dar para testar o 409 de estoque insuficiente sem preparar nada.

**Entrada soma ao saldo (201)**

```bash
BOLO=30000000-0000-0000-0000-000000000006
api -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$BOLO\",\"tipo\":\"ENTRADA\",\"quantidade\":20,\"motivo\":\"Recebimento do fornecedor\"}" \
  | jq '{tipo,quantidade,saldoApos,motivo}'
```

Esperado: `saldoApos: 22` (2 + 20).

**Saída subtrai (201)**

```bash
api -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"SAIDA\",\"quantidade\":15}" | jq '{tipo,saldoApos}'
```

Esperado: `saldoApos: 35` (50 − 15).

**Ajuste define o saldo absoluto (201)**

```bash
api -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"AJUSTE\",\"quantidade\":40,\"motivo\":\"Contagem de inventario\"}" \
  | jq '{tipo,quantidade,saldoApos}'
```

Esperado: `saldoApos: 40`, e nao 75 — o ajuste **nao soma**.

Em `AJUSTE` a quantidade **é** o saldo que passa a valer — é o caso da contagem de
inventário, em que a loja conta a prateleira e informa o que realmente tem. Zero é
aceito aqui (contagem pode dar zero), mas recusado em `ENTRADA` e `SAIDA`.

**Histórico do produto (200, só GERENTE e ADMIN)**

```bash
api "localhost:8080/unidades/$RECIFE/estoque/$TAPIOCA/movimentacoes" \
  -H "Authorization: Bearer $GERENTE" | jq '[.conteudo[] | {tipo,quantidade,saldoApos,motivo,criadoEm}]'
```

Cada linha guarda o saldo que ficou depois dela, então dá para conferir o histórico
sem recalcular nada.

---

### 4.7 Pedidos — o fluxo crítico

> Precisa das variáveis da seção **4.0**.

**Criar pedido (201)**

```bash
PEDIDO=$(api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"TOTEM\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":2}]}")
echo "$PEDIDO" | jq '{id,status,canalPedido,subtotal,desconto,total,proximosStatus,itens}'
PEDIDO_ID=$(echo "$PEDIDO" | jq -r .id)
```

Esperado: `status: "AGUARDANDO_PAGAMENTO"`, `precoUnitario: 12.90`, `total: 25.80`.

O `12.90` e o preco que o seed da a tapioca no cardapio de Recife. Se o seu total vier
31,00, o preco ficou em 15,50: a secao 4.5 o alterou e o passo de restauracao dela nao
foi rodado. Confira com
`api localhost:8080/unidades/$RECIFE/cardapio | jq '.[] | select(.nome|startswith("Tapioca de queijo"))'`.

Repare que **o request não manda preço**. O servidor lê o preço do cardápio daquela
unidade e congela no item — reajuste posterior não muda pedido antigo.

**O preço vem do cardápio, não do cliente**

```bash
api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":1,\"precoUnitario\":0.01}]}" \
  | jq '.itens[0].precoUnitario'
```

Esperado: `12.90` — o preco do cardapio. O `precoUnitario` enviado é simplesmente ignorado.

**A criação baixa o estoque**

```bash
antes=$(api "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq --arg p "$TAPIOCA" '.conteudo[] | select(.produtoId==$p) | .saldoAtual')
api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":3}]}" \
  | jq -c '{id,total,erro:.error}'
depois=$(api "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq --arg p "$TAPIOCA" '.conteudo[] | select(.produtoId==$p) | .saldoAtual')
echo "saldo antes: $antes / depois: $depois / caiu: $((antes - depois)) (esperado: 3)"
```

Se o `POST` devolver `409 ESTOQUE_INSUFICIENTE` e o saldo nao mexer, o estoque acabou:
rode o `AJUSTE` da secao 4.6 de novo, ou recrie o banco.

**Multicanalidade: filtrar por canal**

```bash
api "localhost:8080/pedidos?canalPedido=TOTEM" -H "Authorization: Bearer $TOKEN" \
  | jq '{totalItens, canais:[.conteudo[].canalPedido]}'
```

Esperado: só `TOTEM` na lista. É o que permite a matriz acompanhar a venda por canal.

**Avançar o status**

```bash
avancar() { api -X PATCH localhost:8080/pedidos/$PEDIDO_ID/status \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"status\":\"$1\"}" | jq -r '.status // .error'; }
avancar PAGO; avancar EM_PREPARO; avancar PRONTO; avancar ENTREGUE
```

Esperado: os quatro status em sequência.

**Cancelar devolve o estoque**

```bash
NOVO=$(api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":4}]}" | jq -r .id)
api -X POST localhost:8080/pedidos/$NOVO/cancelamento -H "Authorization: Bearer $TOKEN" | jq '{status}'
api "localhost:8080/unidades/$RECIFE/estoque/$TAPIOCA/movimentacoes" -H "Authorization: Bearer $GERENTE" \
  | jq '[.conteudo[] | {tipo,quantidade,saldoApos,motivo}]'
```

O histórico mostra a saída **e** a devolução — a devolução não apaga a baixa.

**Campanha aplicada automaticamente**

O seed tem uma campanha de 10% exclusiva do canal `APP`, válida na rede toda:

```bash
api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":2}]}" \
  | jq '{subtotal,desconto,total,campanhaAplicada}'
```

Compare com o mesmo pedido pelo `TOTEM`, que não tem desconto.

---

### 4.8 Pagamento mock

> Precisa das variáveis da seção **4.0**.

O gateway é simulado, mas o fluxo é real. **O desfecho não é sorteado** — quem decide é
o `tokenPagamento`, para você conseguir reproduzir os três casos quando quiser:

| Token começando com | Desfecho |
|---|---|
| `tok_recusa` | RECUSADO → pedido vai para `PAGAMENTO_RECUSADO` |
| `tok_timeout` | gateway não responde → pagamento `PENDENTE`, pedido não se mexe |
| qualquer outro, ou nenhum | APROVADO → pedido vai para `PAGO` |

A API **nunca recebe dado de cartão**: o `tokenPagamento` é opaco e, num cenário real,
viria do SDK do próprio gateway.

```bash
novoPedido() { api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"TOTEM\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":2}]}" | jq -r .id; }
pagar() { api -X POST localhost:8080/pedidos/$1/pagamentos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"metodo\":\"PIX\",\"tokenPagamento\":\"$2\"}"; }
```

**Aprovado (T09)**

```bash
P1=$(novoPedido); pagar $P1 tok_ok_123 | jq '{status,statusPedido,valor,idTransacaoExterna,mensagem}'
```

Esperado: `status: "APROVADO"`, `statusPedido: "PAGO"`.

**Recusado (T10)**

```bash
P2=$(novoPedido); pagar $P2 tok_recusa_01 | jq '{status,statusPedido,mensagem}'
```

Esperado: `status: "RECUSADO"`, `statusPedido: "PAGAMENTO_RECUSADO"`. E dá para tentar
de novo:

```bash
pagar $P2 tok_ok_999 | jq '{status,statusPedido,tentativas}'
```

Esperado: aprovado, `tentativas: 2`.

**Gateway sem resposta, resolvido pelo callback**

```bash
P3=$(novoPedido)
PAG=$(pagar $P3 tok_timeout_01)
echo "$PAG" | jq '{status,statusPedido,mensagem}'
PAG_ID=$(echo "$PAG" | jq -r .id)
```

Esperado: `status: "PENDENTE"`, pedido ainda em `AGUARDANDO_PAGAMENTO`. Repare que
**não** é recusa: o gateway pode ter cobrado mesmo sem responder, e dar como recusado
arriscaria cobrar o cliente duas vezes.

Agora o gateway avisa o resultado:

```bash
api -X POST localhost:8080/pagamentos/callback \
  -H 'X-Gateway-Assinatura: segredo-do-gateway-em-dev' -H 'Content-Type: application/json' \
  -d "{\"pagamentoId\":\"$PAG_ID\",\"resultado\":\"APROVADO\",\"idTransacaoExterna\":\"ext_777\"}" \
  | jq '{status,statusPedido}'
```

Esperado: `APROVADO` e pedido em `PAGO`. Reenviar o mesmo callback é ignorado — gateway
reenvia webhook quando não recebe confirmação.

**Idempotência: a mesma chave não cobra duas vezes**

```bash
P4=$(novoPedido)
corpo='{"metodo":"PIX","tokenPagamento":"tok_ok","chaveIdempotencia":"minha-chave-1"}'
id1=$(api -X POST localhost:8080/pedidos/$P4/pagamentos -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$corpo" | jq -r .id)
id2=$(api -X POST localhost:8080/pedidos/$P4/pagamentos -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$corpo" | jq -r .id)
[ "$id1" = "$id2" ] && echo "ok: mesmo pagamento devolvido ($id1)" || echo "FALHOU: cobrou duas vezes"
```

---

### 4.9 Fidelidade e LGPD

> Precisa das variáveis da seção **4.0** e das funções `novoPedido` e `pagar`, definidas
> no primeiro bloco da seção **4.8**. Se aparecer `novoPedido: command not found`, rode
> aquele bloco antes.

O ponto desta seção não é o saldo, é a **base legal**: pontuar depende de saber quem é o
cliente e do que ele consome, e isso é tratamento de dado pessoal. Sem consentimento
ativo, o pedido pago passa sem gerar ponto.

**Consentimentos do titular**

```bash
api localhost:8080/consentimentos -H "Authorization: Bearer $TOKEN" | jq
```

O cliente do seed já vem com `FIDELIDADE` ativo.

**Pedido pago credita pontos**

```bash
echo "saldo antes: $(api localhost:8080/fidelidade/saldo -H "Authorization: Bearer $TOKEN" | jq .saldoPontos)"
PF=$(novoPedido); pagar $PF tok_ok > /dev/null
echo "saldo depois: $(api localhost:8080/fidelidade/saldo -H "Authorization: Bearer $TOKEN" | jq .saldoPontos)"
api localhost:8080/fidelidade/extrato -H "Authorization: Bearer $TOKEN" \
  | jq '[.conteudo[] | {tipo,pontos,saldoApos,descricao}]'
```

Esperado: +25 pontos (total 25.80, 1 ponto por real, truncado para baixo).

**Sem consentimento não pontua** — a prova da regra

```bash
CID=$(api localhost:8080/consentimentos -H "Authorization: Bearer $TOKEN" \
  | jq -r '.[] | select(.finalidade=="FIDELIDADE") | .id')
api -X DELETE localhost:8080/consentimentos/$CID -H "Authorization: Bearer $TOKEN" \
  | jq '{finalidade,ativo,revogadoEm}'

antes=$(api localhost:8080/fidelidade/saldo -H "Authorization: Bearer $TOKEN" | jq .saldoPontos)
PS=$(novoPedido); pagar $PS tok_ok > /dev/null
depois=$(api localhost:8080/fidelidade/saldo -H "Authorization: Bearer $TOKEN" | jq .saldoPontos)
echo "pedido pago com sucesso, saldo $antes -> $depois (esperado: igual)"
```

O pagamento funciona normalmente; só a pontuação não acontece. E repare que a revogação
**não apaga a linha** — ela ganha `revogadoEm`, porque o histórico é a prova de que o
tratamento foi legítimo enquanto durou.

Para voltar a pontuar:

```bash
api -X POST localhost:8080/consentimentos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"finalidade":"FIDELIDADE","versaoDocumento":"1.0"}' | jq '{finalidade,ativo}'
```

**Resgate**

```bash
api -X POST localhost:8080/fidelidade/resgates -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"pontos":10,"descricao":"Troca por cuscuz"}' | jq
```

---

### 4.10 Auditoria

> Precisa das variáveis da seção **4.0**. Só `ADMIN` lê a trilha.

```bash
api "localhost:8080/auditoria?limit=20" -H "Authorization: Bearer $ADMIN" \
  | jq '[.conteudo[] | {acao,entidade,usuarioId,ip,criadoEm}]'
```

**Filtrar por tipo de registro**

```bash
for e in PEDIDO ESTOQUE PAGAMENTO FIDELIDADE CONSENTIMENTO; do
  n=$(api "localhost:8080/auditoria?entidade=$e&limit=100" -H "Authorization: Bearer $ADMIN" | jq .totalItens)
  echo "$e: $n registro(s)"
done
```

**O antes e o depois de uma mudança de status**

```bash
api "localhost:8080/auditoria?entidade=PEDIDO&limit=100" -H "Authorization: Bearer $ADMIN" \
  | jq '[.conteudo[] | select(.acao=="STATUS_ALTERADO") | {acao,dadosAnteriores,dadosNovos}]'
```

Esperado: as quatro transições da secao 4.7 (`PAGO`, `EM_PREPARO`, `PRONTO`, `ENTREGUE`),
cada uma com o status de antes e o de depois.

O `limit=100` e necessario: a rota filtra por entidade, não por acao, e o `select` do `jq`
trabalha so sobre a pagina que voltou. Com uma pagina curta, os registros de criação de
pedido ocupam tudo e o resultado sai `[]` — parecendo que a trilha não guarda o antes e o
depois, quando ela guarda.

**A trilha não tem rota de escrita nem de exclusão**

```bash
curl -s -o /dev/null -w "POST /auditoria -> %{http_code} (esperado 405)
" \
  -X POST localhost:8080/auditoria -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{}'
```

Sem endpoint que altere a trilha, não há como adulterar a prova pela API. O registro
também roda na **mesma transação** da ação: se gravar a trilha falhar, a ação volta
atrás — trilha que falha em silêncio não serve como prova.

---

## 5. Erros

> **Depende das seções anteriores.** Alem das variáveis da **4.0**, estes casos usam o
> `$PEDIDO_ID` criado na **4.7** (casos 20 e 21) e as funções `novoPedido` e `pagar` da
> **4.8** (casos 22 e 24). Rode a secao 4 inteira, na ordem, antes desta.

Todos devolvem o mesmo formato: `error`, `message`, `details[]`, `timestamp`, `path`,
`requestId`.

| # | Cenário | Esperado |
|---|---|---|
| 1 | Sem token | 401 `NAO_AUTENTICADO` |
| 2 | Cliente em rota de admin | 403 `SEM_PERMISSAO` |
| 3 | Gerente em outra unidade | 403 `SEM_PERMISSAO` |
| 4 | Unidade inexistente | 404 `UNIDADE_NAO_ENCONTRADA` |
| 5 | Produto inexistente | 404 `PRODUTO_NAO_ENCONTRADO` |
| 6 | E-mail duplicado (operador) | 409 `EMAIL_JA_CADASTRADO` |
| 7 | Senha fraca | 422 `VALIDACAO` |
| 8 | Preço negativo | 422 `VALIDACAO` |
| 9 | Campo obrigatório ausente | 400 `REQUISICAO_INVALIDA` |
| 10 | Enum inválido | 400 `REQUISICAO_INVALIDA` |
| 11 | UUID mal formado | 400 `REQUISICAO_INVALIDA` |
| 12 | Paginação inválida | 400 `REQUISICAO_INVALIDA` |
| 13 | Código de verificação errado, em conta **ja verificada** | 400 `CODIGO_VERIFICACAO_INVALIDO` — a mesma resposta de um e-mail que não existe, para o 204 não revelar quais contas existem |
| 14 | Saída maior que o saldo | 409 `ESTOQUE_INSUFICIENTE` |
| 15 | Cliente acessando estoque | 403 `SEM_PERMISSAO` |
| 16 | Entrada de quantidade zero | 422 `VALIDACAO` |
| 17 | Pedido sem `canalPedido` | 400 `REQUISICAO_INVALIDA` |
| 18 | Pedido sem estoque | 409 `ESTOQUE_INSUFICIENTE` |
| 19 | Item fora do cardápio da unidade | 422 `PRODUTO_FORA_DO_CARDAPIO` |
| 20 | Transição de status inválida | 409 `TRANSICAO_DE_STATUS_INVALIDA` |
| 21 | Pedido de outra pessoa | 404 `PEDIDO_NAO_ENCONTRADO` |
| 22 | Pagar pedido já pago | 409 `PEDIDO_JA_PAGO` |
| 23 | Callback sem assinatura | 401 `NAO_AUTENTICADO` |
| 24 | Método de pagamento inválido | 400 `REQUISICAO_INVALIDA` |
| 25 | Resgate maior que o saldo | 409 `PONTOS_INSUFICIENTES` |
| 26 | Gerente lendo a auditoria | 403 `SEM_PERMISSAO` |
| 27 | Finalidade de consentimento inválida | 422 `VALIDACAO` |
| 28 | Método HTTP não aceito na rota | 405 `METODO_NAO_PERMITIDO` |

```bash
p() { printf "\n--- %s\n" "$1"; }

p "1. sem token";            api localhost:8080/usuarios/me | jq -c '{error,message}'
p "2. cliente em rota admin"; api -X POST localhost:8080/unidades -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"nome":"X","cidade":"Y","uf":"PE","tipoOperacao":"COMPLETA"}' | jq -c '{error}'
p "3. gerente em outra unidade"; api -X PUT localhost:8080/unidades/$CARUARU/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":1.00,"disponivel":true}' | jq -c '{error,message}'
p "4. unidade inexistente";  api localhost:8080/unidades/10000000-0000-0000-0000-0000000000ff/cardapio | jq -c '{error}'
p "5. produto inexistente";  api -X PUT localhost:8080/unidades/$RECIFE/cardapio/30000000-0000-0000-0000-0000000000ff \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"preco":10.00,"disponivel":true}' | jq -c '{error}'
p "6. email duplicado";      api -X POST localhost:8080/usuarios/operadores -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Repetido\",\"email\":\"cliente@exemplo.com\",\"senha\":\"Senha@123\",\"perfil\":\"ATENDENTE\",\"unidadeId\":\"$RECIFE\"}" | jq -c '{error,details}'
p "7. senha fraca";          api -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"fraca@exemplo.com","senha":"12345678"}' | jq -c '{error,details}'
p "8. preco negativo";       api -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"preco":-5.00,"disponivel":true}' | jq -c '{error,details}'
p "9. campo ausente";        api -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"senha":"Senha@123"}' | jq -c '{error,details}'
p "10. enum invalido";       api -X POST localhost:8080/unidades -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"nome":"X","cidade":"Y","uf":"PE","tipoOperacao":"DRIVE_THRU"}' | jq -c '{error,details}'
p "11. uuid mal formado";    api localhost:8080/unidades/isso-nao-e-uuid | jq -c '{error,details}'
p "12. paginacao invalida";  api "localhost:8080/unidades?page=0&limit=999" | jq -c '{error,details}'
p "13. codigo errado";       api -X POST localhost:8080/usuarios/verificacao -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"000000"}' | jq -c '{error}'
p "14. estoque insuficiente"; api -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"produtoId":"30000000-0000-0000-0000-000000000006","tipo":"SAIDA","quantidade":999}' | jq -c '{error,details}'
p "15. cliente no estoque";  api "localhost:8080/unidades/$RECIFE/estoque" -H "Authorization: Bearer $TOKEN" | jq -c '{error,message}'
p "16. entrada zero";        api -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"ENTRADA\",\"quantidade\":0}" | jq -c '{error,details}'
p "17. pedido sem canal";    api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":1}]}" | jq -c '{error,details}'
p "18. pedido sem estoque";  api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"30000000-0000-0000-0000-000000000006\",\"quantidade\":999}]}" | jq -c '{error,details}'
p "19. fora do cardapio";    api -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$CARUARU\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"30000000-0000-0000-0000-000000000006\",\"quantidade\":1}]}" | jq -c '{error,details}'
p "20. transicao invalida";  api -X PATCH localhost:8080/pedidos/$PEDIDO_ID/status \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"status":"AGUARDANDO_PAGAMENTO"}' | jq -c '{error,details}'
p "21. pedido de outro";     api localhost:8080/pedidos/$PEDIDO_ID -H "Authorization: Bearer $(login gerente.caruaru@raizes.com.br)" | jq -c '{error}'
p "22. pedido ja pago";      PJ=$(novoPedido); pagar $PJ tok_ok > /dev/null; \
  api -X POST localhost:8080/pedidos/$PJ/pagamentos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"metodo":"PIX"}' | jq -c '{error,message}'
p "23. callback sem assinatura"; api -X POST localhost:8080/pagamentos/callback \
  -H 'Content-Type: application/json' \
  -d '{"pagamentoId":"00000000-0000-0000-0000-000000000001","resultado":"APROVADO"}' | jq -c '{error}'
p "24. metodo invalido";     PM=$(novoPedido); api -X POST localhost:8080/pedidos/$PM/pagamentos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"metodo":"BITCOIN"}' | jq -c '{error,details}'
p "25. resgate sem saldo";   api -X POST localhost:8080/fidelidade/resgates -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"pontos":99999}' | jq -c '{error,details}'
p "26. gerente na auditoria"; api localhost:8080/auditoria -H "Authorization: Bearer $GERENTE" | jq -c '{error}'
p "27. finalidade invalida"; api -X POST localhost:8080/consentimentos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"finalidade":"VENDER_PARA_TERCEIROS","versaoDocumento":"1.0"}' | jq -c '{error,details}'
p "28. metodo nao aceito";   api -X POST localhost:8080/auditoria -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{}' | jq -c '{error,details}'
echo
```

### Rastreabilidade do erro

```bash
curl -s -i localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | grep -i x-request-id
api localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | jq -r .requestId
```

Esperado: o mesmo `meu-teste-123` nos dois. Quando o cliente não manda, a API gera um UUID.
Esse id também aparece no log do container, o que permite achar a requisição exata.

---

## 6. Encerrar

Encerre apagando o banco, para a proxima validacao comecar limpa:

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down -v
```

Sem o `-v`, o `down` para os containers e **mantem** o volume: ao subir de novo os dados
da validacao continuam la, o Flyway nao reaplica nada, e os valores esperados nas secoes
4 e 5 nao batem mais. Guarde antes o que for virar evidencia na entrega — print, resposta
salva, o que for — porque o `-v` apaga tudo.

Se precisar parar no meio e retomar depois, pare com `-v` do mesmo jeito e recomece da
secao 1. Sai mais barato que descobrir na secao 4.7 que o estoque acabou.

---

## Ambientes

| | `dev` | `prod` |
|---|---|---|
| Arquivo | `application-dev.yaml` | `application-prod.yaml` |
| Código de verificação | fixo, `258369` | sorteado (`SecureRandom`) |
| Código no log | sim (é público e documentado) | nunca — é credencial |
| Swagger / OpenAPI | no ar | desligado |
| SQL no log | sim | não (vaza dado pessoal nos parâmetros) |
| Segredo do webhook de pagamento | conhecido: `segredo-do-gateway-em-dev` | **sem default** — variável obrigatória |
| Segredos | têm default para facilitar | **sem default** — falta de variável derruba no startup |
| Actuator | `health`, `info`, com detalhes | só `health`, sem detalhes |

O perfil `prod` não é usado hoje — o Dockerfile fixa `dev`. Ele existe para a separação
de ambientes ser real em vez de teórica. Enquanto não houver um `EnviadorDeEmail` de
verdade, subir em `prod` registra um aviso no startup e os códigos não chegam a ninguém.

---

## Problemas comuns

| Sintoma | Causa provável | Saída |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop fechado | Abrir o Docker Desktop e esperar |
| `port is already allocated` em 5432 | Outro Postgres rodando | Parar o outro, ou trocar a porta no `docker-compose.yml` |
| `Schema-validation: wrong column type` | Migration e entidade divergiram | Corrigir a entidade ou criar migration nova |
| `Migration checksum mismatch` | Migration já aplicada foi editada | `docker compose down -v` e subir de novo |
| API sobe mas toda rota dá 401 | Esperado nas rotas protegidas | Fazer login e mandar o `Authorization: Bearer` |
| Rota nova dá 401 sem motivo | *Default deny*: rota não liberada no `SecurityConfig` | Liberar explicitamente, se for para ser pública |
| `jq: Cannot iterate over null` | A resposta é o envelope de **erro**, não o de dados — quase sempre `$TOKEN` vazio ou expirado | Rodar a seção **4.0**. Para ver o que voltou de verdade: repita o curl com `-i` e sem o filtro do `jq` |
| `jq: Cannot index number with string` | Mesma causa da linha acima | Idem |
| O `jq` devolve um objeto com **todos os campos `null`** | A resposta e um envelope de erro: os campos que voce pediu nao existem nele. O status na tela mostra qual erro | Rodar `echo "$VARIAVEL" | jq` sem filtro para ler a mensagem |
| `api: command not found` | O atalho vive so na aba onde foi definido | Redefinir o `api()` da secao 2 nesta aba |
| 422 `PRODUTO_FORA_DO_CARDAPIO` no `POST /pedidos` com um item que deveria existir | A secao 4.5 deixou o item indisponivel | Rodar o passo **Religue o item** no fim da 4.5 |
| Contagens da secao 2 nao batem (usuarios, itens de cardapio) | O volume do Postgres guarda dados de validacoes anteriores | `docker compose down -v && docker compose up --build -d` recria do seed |
| Build do container muito lento | Primeira vez baixa Gradle e dependências | Normal; as próximas usam cache |
