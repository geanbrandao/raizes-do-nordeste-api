# Como subir e validar a API

Passo a passo para levantar o ambiente e conferir que tudo funciona.
Vira base da seção de execução do README na entrega final.

Pré-requisitos já conferidos nesta máquina: Docker Desktop instalado, Java 17,
`jq` disponível, portas 8080 e 5432 livres.

---

## 0. Validação sem banco (roda agora, sem Docker)

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && ./gradlew test
```

Esperado: `BUILD SUCCESSFUL`, 62 testes, 0 falhas.

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
`tokens_verificacao_email`)
mais a `flyway_schema_history`.

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

Abra no navegador:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI cru:** http://localhost:8080/v3/api-docs

Como testar por lá:

1. `POST /auth/login` → **Try it out** → use `cliente@exemplo.com` / `Senha@123` → **Execute**
2. Copie o `accessToken` da resposta
3. Botão **Authorize** no topo → cole o token → **Authorize**
4. `GET /usuarios/me` → **Execute** → deve devolver 200 com os dados do cliente

Conferência rápida pelo terminal de que a documentação reflete as rotas reais:

```bash
curl -s localhost:8080/v3/api-docs | jq -r '.paths | keys[]'
```

Esperado: `/auth/login`, `/auth/logout`, `/auth/refresh`, `/usuarios`, `/usuarios/me`,
`/usuarios/operadores`, `/usuarios/verificacao`, `/usuarios/verificacao/reenvio`.

---

## 4. Fluxo pelo terminal

### Usuários do seed (senha `Senha@123` para todos)

| E-mail | Perfil | Unidade |
|---|---|---|
| `admin@raizes.com.br` | ADMIN | — |
| `gerente.recife@raizes.com.br` | GERENTE | Recife |
| `atendente.recife@raizes.com.br` | ATENDENTE | Recife |
| `cozinha.recife@raizes.com.br` | COZINHA | Recife |
| `gerente.caruaru@raizes.com.br` | GERENTE | Caruaru |
| `cliente@exemplo.com` | CLIENTE | — |

### 4.1 Login (200)

```bash
curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq
```

Esperado: `accessToken`, `refreshToken`, `tokenType: "Bearer"`, `expiresIn: 900`,
e `usuario.perfil: "CLIENTE"`. Repare que **não** existe nenhum campo de senha na resposta.

Guarde o token numa variável:

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq -r .accessToken) && echo "token capturado"
```

### 4.2 Perfil autenticado (200)

```bash
curl -s localhost:8080/usuarios/me -H "Authorization: Bearer $TOKEN" | jq
```

### 4.3 Cadastro de cliente + verificação de e-mail (202 → 204 → 200)

O cadastro devolve **sempre a mesma resposta**, exista ou não o e-mail. A conta nasce
pendente e só loga depois de confirmar o código.

> **Em ambiente de desenvolvimento o código é sempre `258369`.** Ele também aparece no
> log da aplicação (`docker compose logs app | grep VERIFICACAO`). Em produção a chave
> `app.verificacao-email.codigo-fixo` fica vazia e o código passa a ser sorteado.

```bash
# 1. cadastrar -> 202 generico
curl -s -X POST localhost:8080/usuarios \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"joana@exemplo.com","senha":"Senha@123"}' | jq

# 2. tentar logar antes de confirmar -> 403 EMAIL_NAO_VERIFICADO
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq

# 3. confirmar com o codigo de dev -> 204
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/usuarios/verificacao \
  -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"258369"}'

# 4. agora loga -> 200
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","senha":"Senha@123"}' | jq -r '.accessToken // .error'
```

### 4.4 Prova da proteção contra enumeração

As duas chamadas abaixo — e-mail novo e e-mail que já existe — devolvem resposta
**idêntica**, mesmo status e mesmo corpo:

```bash
echo "--- email novo:"; curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Fulano","email":"novo.endereco@exemplo.com","senha":"Senha@123"}'
echo; echo "--- email existente:"; curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Outra Maria","email":"cliente@exemplo.com","senha":"Senha@123"}'
echo
```

---

## 5. Cenários de erro

Cada um devolve o mesmo formato padrão: `error`, `message`, `details[]`, `timestamp`,
`path`, `requestId`.

### 401 — sem token

```bash
curl -s -i localhost:8080/usuarios/me | head -1 && curl -s localhost:8080/usuarios/me | jq
```

Esperado: `401`, `error: "NAO_AUTENTICADO"`.

### 403 — perfil sem permissão

```bash
curl -s -X POST localhost:8080/usuarios/operadores \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Novo Atendente","email":"novo@raizes.com.br","senha":"Senha@123","perfil":"ATENDENTE","unidadeId":"10000000-0000-0000-0000-000000000001"}' | jq
```

Esperado: `403`, `error: "SEM_PERMISSAO"` — cliente não cadastra operador.

### 403 — gerente tentando mexer em outra unidade

```bash
GERENTE=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"gerente.recife@raizes.com.br","senha":"Senha@123"}' | jq -r .accessToken)
curl -s -X POST localhost:8080/usuarios/operadores \
  -H "Authorization: Bearer $GERENTE" -H 'Content-Type: application/json' \
  -d '{"nome":"Intruso","email":"intruso@raizes.com.br","senha":"Senha@123","perfil":"ATENDENTE","unidadeId":"10000000-0000-0000-0000-000000000002"}' | jq
```

Esperado: `403` — o gerente é de Recife e tentou cadastrar em Caruaru.

### 404 — recurso inexistente (e não 403)

```bash
ADMIN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@raizes.com.br","senha":"Senha@123"}' | jq -r .accessToken)
curl -s -X POST localhost:8080/usuarios/operadores \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"nome":"Fantasma","email":"fantasma@raizes.com.br","senha":"Senha@123","perfil":"ATENDENTE","unidadeId":"10000000-0000-0000-0000-0000000000ff"}' | jq
```

Esperado: `404`, `error: "UNIDADE_NAO_ENCONTRADA"`. Admin pode acessar qualquer unidade,
então o que falta é o recurso, não a permissão.

### 409 — e-mail já cadastrado (só no cadastro de operador)

O cadastro **público** nunca devolve 409, de propósito. Já o cadastro de operador é feito
por admin ou gerente autenticado, então não há enumeração a evitar e o erro é explícito.

```bash
curl -s -X POST localhost:8080/usuarios/operadores \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"nome":"Repetido","email":"cliente@exemplo.com","senha":"Senha@123","perfil":"ATENDENTE","unidadeId":"10000000-0000-0000-0000-000000000001"}' | jq
```

Esperado: `409`, `error: "EMAIL_JA_CADASTRADO"`, com `details[0].field: "email"`.

### 400 — código de verificação errado

```bash
curl -s -X POST localhost:8080/usuarios/verificacao -H 'Content-Type: application/json' \
  -d '{"email":"joana@exemplo.com","codigo":"000000"}' | jq
```

Esperado: `400`, `error: "CODIGO_VERIFICACAO_INVALIDO"`. E-mail inexistente devolve
exatamente este mesmo erro — pelo mesmo motivo do login.

### 422 — senha fraca

```bash
curl -s -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joana Silva","email":"joana2@exemplo.com","senha":"12345678"}' | jq
```

Esperado: `422`, `error: "VALIDACAO"`, `details[0].field: "senha"`.

### 400 — campo obrigatório ausente

```bash
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"senha":"Senha@123"}' | jq
```

Esperado: `400`, `error: "REQUISICAO_INVALIDA"`, `details[0].field: "email"`,
`issue: "campo obrigatorio"`.

### 400 — enum inválido

```bash
curl -s -X POST localhost:8080/usuarios/operadores \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"nome":"X","email":"x@raizes.com.br","senha":"Senha@123","perfil":"CHEFE_SUPREMO","unidadeId":"10000000-0000-0000-0000-000000000001"}' | jq
```

Esperado: `400`, com `details[0].issue` listando os perfis aceitos.

### Rastreabilidade do erro

```bash
curl -s -i localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | grep -i x-request-id
curl -s localhost:8080/usuarios/me -H 'X-Request-Id: meu-teste-123' | jq -r .requestId
```

Esperado: o mesmo `meu-teste-123` nos dois. Quando o cliente não manda, a API gera um UUID.
Esse id também aparece no log do container, o que permite achar a requisição exata.

---

## 6. Rotação do refresh token

```bash
REFRESH=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"cliente@exemplo.com","senha":"Senha@123"}' | jq -r .refreshToken)
echo "--- primeira renovacao (espera 200):"
curl -s -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
echo "--- reusando o mesmo refresh (espera TOKEN_INVALIDO):"
curl -s -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken // .error'
```

O segundo tem que falhar. É a rotação funcionando: refresh usado não vale mais.

---

## 7. Encerrar

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down
```

Isso para os containers e **mantém** o volume do banco — ao subir de novo, os dados
continuam lá e o Flyway não reaplica as migrations.

Para começar do zero (necessário se alguma migration for alterada):

```bash
cd ~/Documents/faculdade/TCC/raizes-do-nordeste-api && docker compose down -v
```

---

## Problemas comuns

| Sintoma | Causa provável | Saída |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop fechado | Abrir o Docker Desktop e esperar |
| `port is already allocated` em 5432 | Outro Postgres rodando | Parar o outro, ou trocar a porta no `docker-compose.yml` |
| `Schema-validation: wrong column type` | Migration e entidade divergiram | Corrigir a entidade ou criar migration nova |
| `Migration checksum mismatch` | Migration já aplicada foi editada | `docker compose down -v` e subir de novo |
| API sobe mas toda rota dá 401 | Esperado nas rotas protegidas | Fazer login e mandar o `Authorization: Bearer` |
| Build do container muito lento | Primeira vez baixa Gradle e dependências | Normal; as próximas usam cache |
