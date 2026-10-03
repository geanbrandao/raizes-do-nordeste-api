# Como subir e validar a API

Passo a passo para levantar o ambiente e conferir que tudo funciona.
Vira a base da seção de execução do README na entrega final.

> **Organização deste documento.** As seções 4 e 5 espelham as pastas que a coleção
> Postman/Insomnia vai ter (Auth, Usuários, Unidades, Produtos, Cardápio, … e Erros).
> Cada etapa nova acrescenta uma subseção em **4. Fluxos por recurso** e alguns casos em
> **5. Erros** — assim, montar a coleção na Etapa 10 vira transcrição.

Pré-requisitos já conferidos nesta máquina: Docker Desktop instalado, Java 17,
`jq` disponível, portas 8080 e 5432 livres.

**Estado atual:** 8 controllers, 31 operações HTTP, 165 testes automatizados.

---

## 0. Validação sem banco (roda agora, sem Docker)

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && ./gradlew test
```

Esperado: `BUILD SUCCESSFUL`, 165 testes, 0 falhas.

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

Com o daemon no ar:

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose up --build
```

O primeiro build demora alguns minutos: o Dockerfile roda `./gradlew bootJar` dentro do
container e baixa o Gradle e as dependências do zero. As próximas vezes usam cache.

Deixe esse terminal aberto mostrando o log e use outro para os comandos seguintes.

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

```bash
curl -s localhost:8080/actuator/health | jq
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
curl -s localhost:8080/v3/api-docs | jq -r '.paths | keys[]'
```

Esperado (24 rotas):

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
```

---

## 4. Fluxos por recurso

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
login() { curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
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
Confira com `curl -s localhost:8080/actuator/health`.

---

### 4.1 Auth

**Login (200)**

```bash
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq
```

Esperado: `accessToken`, `refreshToken`, `tokenType: "Bearer"`, `expiresIn: 900`,
`usuario.perfil: "CLIENTE"`. Nenhum campo de senha aparece na resposta.

**Perfil autenticado (200)**

```bash
curl -s localhost:8080/usuarios/me -H "Authorization: Bearer $TOKEN" | jq
```

**Rotação do refresh token**

```bash
REFRESH=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq -r .refreshToken)
echo "--- primeira renovacao (espera um token):"
curl -s -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
echo "--- reusando o mesmo refresh (espera TOKEN_INVALIDO):"
curl -s -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
```

O segundo tem que falhar. É a rotação funcionando: refresh usado não vale mais.

---

### 4.2 Usuários — cadastro e verificação de e-mail (202 → 204 → 200)

O cadastro devolve **sempre a mesma resposta**, exista ou não o e-mail. A conta nasce
pendente e só loga depois de confirmar o código.

> **Em desenvolvimento o código é sempre `258369`.** Ele também aparece no log
> (`docker compose logs app | grep VERIFICACAO`). Em produção a chave
> `app.verificacao-email.codigo-fixo` fica vazia e o código passa a ser sorteado.

```bash
# 1. cadastrar -> 202 generico
curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"joana@exemplo.com","senha":"Senha@123"}' | jq

# 2. tentar logar antes de confirmar -> 403 EMAIL_NAO_VERIFICADO
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq -r '.accessToken // .error'

# 3. confirmar com o codigo de dev -> 204
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/usuarios/verificacao \
  -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"258369"}'

# 4. agora loga -> 200
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq -r '.accessToken // .error'
```

**Prova da proteção contra enumeração** — as duas respostas são idênticas:

```bash
echo "--- email novo:"; curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Fulano","email":"novo.endereco@exemplo.com","senha":"Senha@123"}'
echo; echo "--- email existente:"; curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Outra Maria","email":"cliente@exemplo.com","senha":"Senha@123"}'
echo
```

**Cadastro de operador (201, só ADMIN e GERENTE)**

```bash
curl -s -X POST localhost:8080/usuarios/operadores -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Novo Atendente\",\"email\":\"novo.atendente@raizes.com.br\",\"senha\":\"Senha@123\",\"perfil\":\"ATENDENTE\",\"unidadeId\":\"$RECIFE\"}" | jq
```

Operador nasce já verificado — foi criado por alguém de confiança, então loga direto.

---

### 4.3 Unidades

> Precisa das variáveis da seção **4.0**.

**Listagem pública e paginada (200)**

```bash
curl -s "localhost:8080/unidades?page=1&limit=10" | jq
```

Esperado: envelope com `conteudo`, `pagina: 1`, `limite: 10`, `totalItens: 2`,
`totalPaginas: 1`, `primeira: true`, `ultima: true`.

**Paginação de verdade**

```bash
curl -s "localhost:8080/unidades?page=1&limit=1" | jq '{pagina,totalPaginas,ultima,itens:(.conteudo|length)}'
curl -s "localhost:8080/unidades?page=2&limit=1" | jq '{pagina,ultima}'
```

**Detalhe (200)**

```bash
curl -s localhost:8080/unidades/$RECIFE | jq
```

**Cadastrar unidade (201, só ADMIN)**

```bash
curl -s -X POST localhost:8080/unidades -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Raizes Olinda","cidade":"Olinda","uf":"pe","tipoOperacao":"REDUZIDA"}' | jq
```

Repare que a UF volta em maiúscula mesmo enviada minúscula — normalização no service.

---

### 4.4 Produtos

> Precisa das variáveis da seção **4.0**.

**Catálogo da rede (200, exige token)**

```bash
curl -s "localhost:8080/produtos?page=1&limit=5" -H "Authorization: Bearer $TOKEN" | jq '{totalItens,totalPaginas,nomes:[.conteudo[].nome]}'
```

Esperado: `totalItens: 10`.

**Filtro por categoria (aceita minúscula)**

```bash
curl -s "localhost:8080/produtos?categoria=bebida" -H "Authorization: Bearer $TOKEN" | jq '{totalItens,nomes:[.conteudo[].nome]}'
```

Esperado: 3 bebidas.

**Cadastrar produto (201, ADMIN ou GERENTE)**

```bash
curl -s -X POST localhost:8080/produtos -H "Authorization: Bearer $GERENTE" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Pamonha","categoria":"milho","precoBase":9.50,"sazonal":true}' | jq
```

**Inativar produto (204, só ADMIN) — soft delete**

```bash
BOLO=30000000-0000-0000-0000-000000000006
curl -s -o /dev/null -w "inativar: %{http_code}\n" -X DELETE localhost:8080/produtos/$BOLO \
  -H "Authorization: Bearer $ADMIN"
echo "--- sumiu da listagem:"
curl -s localhost:8080/produtos -H "Authorization: Bearer $ADMIN" | jq .totalItens
echo "--- mas continua existindo:"
curl -s localhost:8080/produtos/$BOLO -H "Authorization: Bearer $ADMIN" | jq '{nome,ativo}'
```

O produto sai da listagem mas continua acessível por id, com `ativo: false`. Apagar de
verdade quebraria todo pedido antigo que aponta para ele.

---

### 4.5 Cardápio por unidade

> Precisa das variáveis da seção **4.0**.

Esta é a parte que mostra na prática que **nem toda loja da rede é igual**.

**Consulta pública (200)**

```bash
curl -s localhost:8080/unidades/$RECIFE/cardapio | jq '[.[] | {nome,preco,categoria}]'
```

**Unidade COMPLETA vende mais que a REDUZIDA**

```bash
echo "Recife (COMPLETA):  $(curl -s localhost:8080/unidades/$RECIFE/cardapio | jq 'length') itens"
echo "Caruaru (REDUZIDA): $(curl -s localhost:8080/unidades/$CARUARU/cardapio | jq 'length') itens"
```

Esperado: 10 e 6.

**Mesmo produto, preço diferente em cada loja**

```bash
for u in $RECIFE $CARUARU; do
  curl -s localhost:8080/unidades/$u/cardapio \
    | jq -r --arg u "$u" '.[] | select(.nome=="Tapioca de queijo coalho") | "\($u): R$ \(.preco)"'
done
```

**Gerente ajusta o preço da própria loja (200)**

```bash
curl -s -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":15.50,"disponivel":true}' | jq '{nome,preco,disponivel}'
```

**Tirar item do ar sem apagar o cadastro**

```bash
curl -s -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":15.50,"disponivel":false}' > /dev/null
echo "--- visao do cliente:    $(curl -s localhost:8080/unidades/$RECIFE/cardapio | jq 'length') itens"
echo "--- visao da operacao:   $(curl -s "localhost:8080/unidades/$RECIFE/cardapio?incluirIndisponiveis=true" | jq 'length') itens"
```

O item some para o cliente e continua visível para quem administra a loja.

---

### 4.6 Estoque

> Precisa das variáveis da seção **4.0**.

Estoque é informação da operação, não da vitrine: cliente não tem acesso nenhum.

**Saldo da unidade (200)**

```bash
curl -s "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq '[.conteudo[] | {nome,saldoAtual,abaixoDoMinimo}]'
```

O Bolo de rolo vem com `saldoAtual: 2` e `abaixoDoMinimo: true` — o seed deixa esse item
propositalmente baixo, para dar para testar o 409 de estoque insuficiente sem preparar nada.

**Entrada soma ao saldo (201)**

```bash
BOLO=30000000-0000-0000-0000-000000000006
curl -s -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$BOLO\",\"tipo\":\"ENTRADA\",\"quantidade\":20,\"motivo\":\"Recebimento do fornecedor\"}" \
  | jq '{tipo,quantidade,saldoApos,motivo}'
```

Esperado: `saldoApos: 22` (2 + 20).

**Saída subtrai (201)**

```bash
curl -s -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"SAIDA\",\"quantidade\":15}" | jq '{tipo,saldoApos}'
```

Esperado: `saldoApos: 35` (50 − 15).

**Ajuste define o saldo absoluto (201)**

```bash
curl -s -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"AJUSTE\",\"quantidade\":7,\"motivo\":\"Contagem de inventario\"}" \
  | jq '{tipo,quantidade,saldoApos}'
```

Em `AJUSTE` a quantidade **é** o saldo que passa a valer — é o caso da contagem de
inventário, em que a loja conta a prateleira e informa o que realmente tem. Zero é
aceito aqui (contagem pode dar zero), mas recusado em `ENTRADA` e `SAIDA`.

**Histórico do produto (200, só GERENTE e ADMIN)**

```bash
curl -s "localhost:8080/unidades/$RECIFE/estoque/$TAPIOCA/movimentacoes" \
  -H "Authorization: Bearer $GERENTE" | jq '[.conteudo[] | {tipo,quantidade,saldoApos,motivo,criadoEm}]'
```

Cada linha guarda o saldo que ficou depois dela, então dá para conferir o histórico
sem recalcular nada.

---

### 4.7 Pedidos — o fluxo crítico

> Precisa das variáveis da seção **4.0**.

**Criar pedido (201)**

```bash
PEDIDO=$(curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"TOTEM\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":2}]}")
echo "$PEDIDO" | jq '{id,status,canalPedido,subtotal,desconto,total,proximosStatus,itens}'
PEDIDO_ID=$(echo "$PEDIDO" | jq -r .id)
```

Esperado: `status: "AGUARDANDO_PAGAMENTO"`, `precoUnitario: 12.90`, `total: 25.80`.

Repare que **o request não manda preço**. O servidor lê o preço do cardápio daquela
unidade e congela no item — reajuste posterior não muda pedido antigo.

**O preço vem do cardápio, não do cliente**

```bash
curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":1,\"precoUnitario\":0.01}]}" \
  | jq '.itens[0].precoUnitario'
```

Esperado: `12.90`. O `precoUnitario` enviado é simplesmente ignorado.

**A criação baixa o estoque**

```bash
antes=$(curl -s "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq --arg p "$TAPIOCA" '.conteudo[] | select(.produtoId==$p) | .saldoAtual')
curl -s -o /dev/null -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":3}]}"
depois=$(curl -s "localhost:8080/unidades/$RECIFE/estoque?limit=100" -H "Authorization: Bearer $GERENTE" \
  | jq --arg p "$TAPIOCA" '.conteudo[] | select(.produtoId==$p) | .saldoAtual')
echo "saldo antes: $antes / depois: $depois (esperado: 3 a menos)"
```

**Multicanalidade: filtrar por canal**

```bash
curl -s "localhost:8080/pedidos?canalPedido=TOTEM" -H "Authorization: Bearer $TOKEN" \
  | jq '{totalItens, canais:[.conteudo[].canalPedido]}'
```

Esperado: só `TOTEM` na lista. É o que permite a matriz acompanhar a venda por canal.

**Avançar o status**

```bash
avancar() { curl -s -X PATCH localhost:8080/pedidos/$PEDIDO_ID/status \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"status\":\"$1\"}" | jq -r '.status // .error'; }
avancar PAGO; avancar EM_PREPARO; avancar PRONTO; avancar ENTREGUE
```

Esperado: os quatro status em sequência.

**Cancelar devolve o estoque**

```bash
NOVO=$(curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":4}]}" | jq -r .id)
curl -s -X POST localhost:8080/pedidos/$NOVO/cancelamento -H "Authorization: Bearer $TOKEN" | jq '{status}'
curl -s "localhost:8080/unidades/$RECIFE/estoque/$TAPIOCA/movimentacoes" -H "Authorization: Bearer $GERENTE" \
  | jq '[.conteudo[] | {tipo,quantidade,saldoApos,motivo}]'
```

O histórico mostra a saída **e** a devolução — a devolução não apaga a baixa.

**Campanha aplicada automaticamente**

O seed tem uma campanha de 10% exclusiva do canal `APP`, válida na rede toda:

```bash
curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
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
novoPedido() { curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"TOTEM\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":2}]}" | jq -r .id; }
pagar() { curl -s -X POST localhost:8080/pedidos/$1/pagamentos -H "Authorization: Bearer $TOKEN" \
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
curl -s -X POST localhost:8080/pagamentos/callback \
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
id1=$(curl -s -X POST localhost:8080/pedidos/$P4/pagamentos -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$corpo" | jq -r .id)
id2=$(curl -s -X POST localhost:8080/pedidos/$P4/pagamentos -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$corpo" | jq -r .id)
[ "$id1" = "$id2" ] && echo "ok: mesmo pagamento devolvido ($id1)" || echo "FALHOU: cobrou duas vezes"
```

---

## 5. Erros

> Precisa das variáveis da seção **4.0**.

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
| 13 | Código de verificação errado | 400 `CODIGO_VERIFICACAO_INVALIDO` |
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

```bash
p() { printf "\n--- %s\n" "$1"; }

p "1. sem token";            curl -s localhost:8080/usuarios/me | jq -c '{error,message}'
p "2. cliente em rota admin"; curl -s -X POST localhost:8080/unidades -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"nome":"X","cidade":"Y","uf":"PE","tipoOperacao":"COMPLETA"}' | jq -c '{error}'
p "3. gerente em outra unidade"; curl -s -X PUT localhost:8080/unidades/$CARUARU/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"preco":1.00,"disponivel":true}' | jq -c '{error,message}'
p "4. unidade inexistente";  curl -s localhost:8080/unidades/10000000-0000-0000-0000-0000000000ff/cardapio | jq -c '{error}'
p "5. produto inexistente";  curl -s -X PUT localhost:8080/unidades/$RECIFE/cardapio/30000000-0000-0000-0000-0000000000ff \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"preco":10.00,"disponivel":true}' | jq -c '{error}'
p "6. email duplicado";      curl -s -X POST localhost:8080/usuarios/operadores -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Repetido\",\"email\":\"cliente@exemplo.com\",\"senha\":\"Senha@123\",\"perfil\":\"ATENDENTE\",\"unidadeId\":\"$RECIFE\"}" | jq -c '{error,details}'
p "7. senha fraca";          curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"fraca@exemplo.com","senha":"12345678"}' | jq -c '{error,details}'
p "8. preco negativo";       curl -s -X PUT localhost:8080/unidades/$RECIFE/cardapio/$TAPIOCA \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"preco":-5.00,"disponivel":true}' | jq -c '{error,details}'
p "9. campo ausente";        curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"senha":"Senha@123"}' | jq -c '{error,details}'
p "10. enum invalido";       curl -s -X POST localhost:8080/unidades -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"nome":"X","cidade":"Y","uf":"PE","tipoOperacao":"DRIVE_THRU"}' | jq -c '{error,details}'
p "11. uuid mal formado";    curl -s localhost:8080/unidades/isso-nao-e-uuid | jq -c '{error,details}'
p "12. paginacao invalida";  curl -s "localhost:8080/unidades?page=0&limit=999" | jq -c '{error,details}'
p "13. codigo errado";       curl -s -X POST localhost:8080/usuarios/verificacao -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"000000"}' | jq -c '{error}'
p "14. estoque insuficiente"; curl -s -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"produtoId":"30000000-0000-0000-0000-000000000006","tipo":"SAIDA","quantidade":999}' | jq -c '{error,details}'
p "15. cliente no estoque";  curl -s "localhost:8080/unidades/$RECIFE/estoque" -H "Authorization: Bearer $TOKEN" | jq -c '{error,message}'
p "16. entrada zero";        curl -s -X POST localhost:8080/unidades/$RECIFE/estoque/movimentacoes \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d "{\"produtoId\":\"$TAPIOCA\",\"tipo\":\"ENTRADA\",\"quantidade\":0}" | jq -c '{error,details}'
p "17. pedido sem canal";    curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"itens\":[{\"produtoId\":\"$TAPIOCA\",\"quantidade\":1}]}" | jq -c '{error,details}'
p "18. pedido sem estoque";  curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$RECIFE\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"30000000-0000-0000-0000-000000000006\",\"quantidade\":999}]}" | jq -c '{error,details}'
p "19. fora do cardapio";    curl -s -X POST localhost:8080/pedidos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"unidadeId\":\"$CARUARU\",\"canalPedido\":\"APP\",\"itens\":[{\"produtoId\":\"30000000-0000-0000-0000-000000000006\",\"quantidade\":1}]}" | jq -c '{error,details}'
p "20. transicao invalida";  curl -s -X PATCH localhost:8080/pedidos/$PEDIDO_ID/status \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"status":"AGUARDANDO_PAGAMENTO"}' | jq -c '{error,details}'
p "21. pedido de outro";     curl -s localhost:8080/pedidos/$PEDIDO_ID -H "Authorization: Bearer $(login gerente.caruaru@raizes.com.br)" | jq -c '{error}'
p "22. pedido ja pago";      PJ=$(novoPedido); pagar $PJ tok_ok > /dev/null; \
  curl -s -X POST localhost:8080/pedidos/$PJ/pagamentos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"metodo":"PIX"}' | jq -c '{error,message}'
p "23. callback sem assinatura"; curl -s -X POST localhost:8080/pagamentos/callback \
  -H 'Content-Type: application/json' \
  -d '{"pagamentoId":"00000000-0000-0000-0000-000000000001","resultado":"APROVADO"}' | jq -c '{error}'
p "24. metodo invalido";     PM=$(novoPedido); curl -s -X POST localhost:8080/pedidos/$PM/pagamentos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"metodo":"BITCOIN"}' | jq -c '{error,details}'
echo
```

### Rastreabilidade do erro

```bash
curl -s -i localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | grep -i x-request-id
curl -s localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | jq -r .requestId
```

Esperado: o mesmo `meu-teste-123` nos dois. Quando o cliente não manda, a API gera um UUID.
Esse id também aparece no log do container, o que permite achar a requisição exata.

---

## 6. Encerrar

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down
```

Para os containers e **mantém** o volume do banco — ao subir de novo, os dados continuam
lá e o Flyway não reaplica as migrations.

Para começar do zero (necessário se alguma migration for alterada):

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down -v
```

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
| Build do container muito lento | Primeira vez baixa Gradle e dependências | Normal; as próximas usam cache |
