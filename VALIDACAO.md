# Como subir e validar a API

Passo a passo para levantar o ambiente e conferir que tudo funciona.
Vira a base da seção de execução do README na entrega final.

> **Organização deste documento.** As seções 4 e 5 espelham as pastas que a coleção
> Postman/Insomnia vai ter (Auth, Usuários, Unidades, Produtos, Cardápio, … e Erros).
> Cada etapa nova acrescenta uma subseção em **4. Fluxos por recurso** e alguns casos em
> **5. Erros** — assim, montar a coleção na Etapa 10 vira transcrição.

Pré-requisitos já conferidos nesta máquina: Docker Desktop instalado, Java 17,
`jq` disponível, portas 8080 e 5432 livres.

**Estado atual:** 5 controllers, 20 operações HTTP, 98 testes automatizados.

---

## 0. Validação sem banco (roda agora, sem Docker)

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && ./gradlew test
```

Esperado: `BUILD SUCCESSFUL`, 98 testes, 0 falhas.

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
| `Successfully applied 16 migrations` | Flyway criou o schema e aplicou o seed |
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

Esperado: 16 linhas, todas com `success = t`.

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

Esperado (14 rotas):

```
/auth/login          /auth/logout         /auth/refresh
/usuarios            /usuarios/me         /usuarios/operadores
/usuarios/verificacao                     /usuarios/verificacao/reenvio
/unidades            /unidades/{unidadeId}
/produtos            /produtos/{produtoId}
/unidades/{unidadeId}/cardapio            /unidades/{unidadeId}/cardapio/{produtoId}
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
