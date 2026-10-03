# Raízes do Nordeste — API

API REST da rede de lanchonetes fictícia **Raízes do Nordeste**, que vende comida típica
nordestina em várias unidades e por vários canais: aplicativo, web, totem e balcão.

A API cobre o ciclo completo do pedido — catálogo e cardápio por unidade, estoque,
criação de pedido com desconto de campanha, pagamento, programa de fidelidade — com
autenticação por perfil, trilha de auditoria e os controles de LGPD que o tratamento de
dado pessoal exige.

Projeto Multidisciplinar da Trilha Back-End, UNINTER.

| Stack | Tamanho |
|---|---|
| Kotlin 1.9.25 · Spring Boot 3.5.11 · PostgreSQL 16 | 30 rotas, 38 operações |
| Gradle · Flyway · Docker | 18 migrations, 196 testes, 0 falhas |

---

## Sumário

- [O que a API faz](#o-que-a-api-faz)
- [Requisitos](#requisitos)
- [Como subir](#como-subir)
- [Swagger](#swagger)
- [Como rodar os testes](#como-rodar-os-testes)
- [Coleção de testes](#coleção-de-testes)
- [Usuários do seed](#usuários-do-seed)
- [Decisões técnicas](#decisões-técnicas)
- [Padrão de erro](#padrão-de-erro)
- [Endpoints](#endpoints)
- [Segurança e LGPD](#segurança-e-lgpd)
- [Estrutura do projeto](#estrutura-do-projeto)
- [O que ficou fora do escopo](#o-que-ficou-fora-do-escopo)
- [Entrega](#entrega)
- [Documentos](#documentos)

---

## O que a API faz

**Rede com unidades diferentes entre si.** Cada unidade tem o próprio cardápio e o próprio
preço. A loja de Recife opera `COMPLETA` com 10 itens; a de Caruaru opera `REDUZIDA` com 6,
a preço menor. O mesmo produto custa valores diferentes em cada uma, e é a unidade que
decide o que está disponível agora.

**Pedido multicanal.** Todo pedido guarda por onde entrou (`APP`, `WEB`, `TOTEM`,
`BALCAO`), e isso não é só rótulo: as campanhas segmentam por canal, por unidade e por faixa
de idade. Um pedido pelo app pode ter 10% de desconto que o mesmo pedido no totem não tem.

**Preço que o cliente não controla.** O request não manda preço. O servidor lê o preço do
cardápio daquela unidade e **congela no item** — reajuste depois não altera pedido antigo.

**Estoque com tudo ou nada.** A criação do pedido debita o estoque. Se faltar qualquer item,
nada é criado e o erro traz a lista completa do que faltou, com pedido e disponível. Quem
recebe sabe de uma vez o que tirar do carrinho, em vez de descobrir um item por tentativa.

**Pagamento com os caminhos ruins inclusos.** Gateway simulado com três desfechos
determinísticos — aprovado, recusado e sem resposta —, idempotência na solicitação e
callback assinado para resolver o que ficou pendente.

**Fidelidade atrelada à base legal.** Pontuar exige saber quem é o cliente e o que ele
consome, e isso é tratamento de dado pessoal. Sem consentimento ativo, o pedido pago passa
sem gerar ponto.

**Trilha de auditoria.** Nove ações sensíveis registradas com antes, depois, autor e IP, na
mesma transação da ação.

---

## Requisitos

**Só o Docker é obrigatório.** O build do Java acontece dentro do container, então não é
preciso ter Java, Kotlin, Gradle nem PostgreSQL instalados na máquina.

| Ferramenta | Obrigatória? | Para quê |
|---|---|---|
| Docker Desktop (ou Docker Engine + Compose) | **Sim** | Subir API e banco |
| Java 17 | Não | Só para rodar os testes fora do container |
| `jq` | Não | Opcional, deixa as respostas no terminal legíveis |

Portas usadas: **8080** (API) e **5432** (Postgres). Se já houver algo nelas, pare o outro
serviço ou altere a porta no `docker-compose.yml`.

### Instalando o Docker

| Sistema | Como instalar |
|---|---|
| **Windows** | [Docker Desktop para Windows](https://docs.docker.com/desktop/install/windows-install/) — requer WSL 2, que o próprio instalador configura |
| **macOS** | [Docker Desktop para Mac](https://docs.docker.com/desktop/install/mac-install/) — escolha o instalador do seu chip (Apple Silicon ou Intel) |
| **Linux** | [Docker Engine](https://docs.docker.com/engine/install/) + [plugin do Compose](https://docs.docker.com/compose/install/linux/), ou [Docker Desktop para Linux](https://docs.docker.com/desktop/install/linux-install/) |

Depois de instalar, confira que está tudo no lugar:

```bash
docker --version
docker compose version
```

O segundo comando precisa responder **v2.x**. Este projeto usa o formato do Compose v2
(`docker compose`, com espaço). O `docker-compose` v1 — com hífen, descontinuado — não lê
este arquivo corretamente.

**No Windows**, o Docker Desktop precisa estar aberto e com o ícone da baleia estável antes
de qualquer comando. **No Linux**, se o `docker` pedir permissão, use `sudo` ou adicione seu
usuário ao grupo: `sudo usermod -aG docker $USER` e reabra a sessão.

### Dependências principais

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-web` | API REST |
| `spring-boot-starter-data-jpa` | Persistência (Hibernate) |
| `spring-boot-starter-validation` | Bean Validation nos contratos |
| `spring-boot-starter-security` | Autenticação e autorização |
| `jjwt` 0.12.6 | Geração e validação do JWT |
| `flyway-core` + `flyway-database-postgresql` | Migrations versionadas |
| `springdoc-openapi-starter-webmvc-ui` 2.8.6 | Swagger UI e OpenAPI |
| `spring-boot-starter-actuator` | Health check |
| `postgresql` | Driver |
| JUnit 5, mockito-kotlin, spring-security-test, H2 | Testes |

### Variáveis de ambiente

O [`.env.example`](.env.example) tem todas as chaves com valor de exemplo. Para desenvolvimento,
o `docker-compose.yml` já traz defaults e **nada precisa ser configurado**. Para criar seu
próprio arquivo:

```bash
cp .env.example .env
```

| Variável | Para quê |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Credenciais do banco |
| `SPRING_DATASOURCE_URL` | Conexão JDBC |
| `SPRING_PROFILES_ACTIVE` | `dev` ou `prod` |
| `JWT_SECRET` | Assinatura do token, mínimo 32 bytes |
| `APP_TIMEZONE` | Fuso usado nas regras de negócio |

No perfil `prod` **nenhum segredo tem valor padrão**: faltando qualquer variável, a aplicação
não sobe. É deliberado — subir em produção com segredo de exemplo é pior que não subir.

---

## Como subir

```bash
git clone https://github.com/geanbrandao/raizes-do-nordeste-api.git
cd raizes-do-nordeste-api
docker compose down -v && docker compose up --build
```

> **Windows:** rode estes comandos no **Git Bash**, no **WSL** ou no **PowerShell**. Se usar
> PowerShell, atenção ao `curl`: lá ele é apelido do `Invoke-WebRequest`, que tem outra
> sintaxe. Para os comandos de validação deste projeto, prefira o Git Bash — já vem com o
> instalador do Git para Windows e aceita tudo como está escrito aqui.

O `down -v` apaga o volume do banco antes de subir. Use sempre: as validações alteram preço e
estoque de propósito, e um banco reaproveitado faz os valores esperados na documentação não
fecharem. Em máquina onde o volume ainda não existe, o comando funciona igual.

O primeiro build demora alguns minutos — o Dockerfile roda `./gradlew bootJar` dentro do
container e baixa Gradle e dependências. As próximas vezes usam cache.

### O banco, as migrations e o seed

Nada disso é manual. Ao subir, o Flyway cria o schema e carrega o seed, nesta ordem:

1. **V1 a V11** criam as 16 tabelas
2. **V12** carrega o seed: 2 unidades, 6 usuários, 10 produtos, cardápios, estoque
3. **V13 a V18** campanhas e ajustes posteriores

O que procurar no log, nesta ordem:

| Sinal | Significa |
|---|---|
| `database system is ready to accept connections` | Postgres no ar |
| `Successfully applied 18 migrations` | Schema e seed prontos |
| `Codigo de verificação FIXO ligado (258369)` | Perfil dev, código previsível |
| `Started RaizesApiApplication` | Subiu inteira |

Conferindo que respondeu:

```bash
curl -s localhost:8080/actuator/health
```

Esperado: `{"status":"UP"}`.

### Sem ocupar um terminal com o log

```bash
docker compose up --build -d
docker compose logs -f app     # quando quiser ver
```

### Encerrar

```bash
docker compose down -v
```

---

## Swagger

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI:** http://localhost:8080/v3/api-docs

Para testar rota protegida pela interface:

1. `POST /auth/login` → **Try it out** → `cliente@exemplo.com` / `Senha@123` → **Execute**
2. Copie o `accessToken` da resposta
3. Botão **Authorize** no topo da página → cole o token → **Authorize**
4. Qualquer rota passa a responder autenticada

No perfil `prod` o Swagger fica **desabilitado**. Documentação interativa aberta em produção
entrega o mapa completo da API a qualquer visitante.

---

## Como rodar os testes

```bash
./gradlew test          # macOS, Linux, Git Bash e WSL
gradlew.bat test        # Windows, no CMD ou PowerShell
```

Esperado: `BUILD SUCCESSFUL`, **196 testes, 0 falhas**. Relatório navegável em
`build/reports/tests/test/index.html`.

Não precisa de Docker nem de Postgres: os testes rodam em H2 no modo de compatibilidade
PostgreSQL, com as **migrations reais aplicadas pelo Flyway**. Isso faz com que uma
divergência entre migration, entidade e seed quebre o teste, e não a subida em produção.

O roteiro de validação manual, com os fluxos por recurso e 28 cenários de erro, está em
[VALIDACAO.md](VALIDACAO.md).

---

## Coleção de testes

Siga os três passos abaixo na ordem. Não precisa configurar nada antes.

### Passo 1 — Suba a API com o banco limpo

```bash
docker compose down -v && docker compose up -d
```

O `-v` apaga o banco anterior. **Este passo não é opcional:** as pastas 2, 5 e 6 da coleção
alteram cadastro, preço e estoque de propósito, e sobre um banco já usado as contas não
fecham.

Espere a API responder antes de seguir:

```bash
curl -s localhost:8080/actuator/health
```

Esperado: `{"status":"UP"}`.

### Passo 2 — Importe **um** arquivo

```
postman/raizes-do-nordeste.postman_collection.json
```

Esse arquivo sozinho já traz tudo: as requisições, os testes e as variáveis preenchidas.
**Não importe mais nada agora** — os outros arquivos da pasta são opcionais e só atrapalham
neste momento (veja [Os outros arquivos](#os-outros-arquivos)).

No Postman ou no Insomnia, o caminho é o mesmo: **Import** → selecione o arquivo.

Depois de importar, confira que apareceu **Raizes do Nordeste — TESTES (rode esta)**, com
pastas numeradas de `0. Setup` a `11. Erros`.

### Passo 3 — Rode

**No Postman:** selecione a coleção → **Run** → **Run Raizes do Nordeste**.

**No Insomnia:** clique no **nome da coleção** → **Run Collection** → na aba
**Request Order** deixe a ordem como está → **Run**. Os resultados saem no painel da direita.

**Sem abrir programa nenhum**, direto no terminal:

```bash
npx newman run postman/raizes-do-nordeste.postman_collection.json
```

### O que esperar

| | |
|---|---|
| Requisições | **93** |
| Asserções | **202** |
| Falhas | **0** |
| Duração | poucos segundos |

As pastas já estão na ordem de execução e cada requisição guarda sozinha o que a próxima
precisa — os quatro tokens, o id do pedido, o do pagamento, o do consentimento. **Você não
copia nem cola nada.**

| Pasta | |
|---|---|
| `0. Setup` | Faz os quatro logins e guarda os tokens. **Sem ela, tudo depois dá 401** |
| `1. Auth` a `10. Auditoria` | Um recurso por pasta, no caminho feliz |
| `11. Erros` | 30 cenários negativos, cada um conferindo o status **e** o código do erro |

### Se algo não bater

| Sintoma | Causa | O que fazer |
|---|---|---|
| `Results 0/0` e 401 em quase tudo | Você está rodando o **documento de Design**, não a coleção | Rode em **Raizes do Nordeste — TESTES (rode esta)**. A errada se chama `API Raizes do Nordeste 0.1.0` e tem requisições soltas, sem pastas numeradas |
| 6 asserções falham, sempre as mesmas | Banco reaproveitado | Volte ao Passo 1 |
| Tudo dá 401 logo no começo | A pasta `0. Setup` não rodou, ou rodou depois das outras | Rode a coleção inteira, de cima para baixo, sem desmarcar requisições |
| Erro de conexão | A API não está no ar | `curl -s localhost:8080/actuator/health` |
| Os tokens não se preenchem sozinhos | Seu cliente não executa os scripts | Rode o login da pasta `0. Setup`, copie o `accessToken` e cole na variável `tokenCliente`. Os ids do seed já vêm prontos |

### Os outros arquivos

Não são necessários para rodar a coleção. Importe só se quiser:

| Arquivo | Para quê |
|---|---|
| [`raizes-do-nordeste.postman_environment.json`](postman/raizes-do-nordeste.postman_environment.json) | Ambiente separado, caso prefira editar `baseUrl` fora da coleção. A coleção já funciona sem ele |
| [`openapi.json`](postman/openapi.json) | O contrato em OpenAPI 3.1, para **ler** schemas e tipos. Vira um documento de Design, **não executa testes** — é esse que causa o `Results 0/0` da tabela acima |

### Clientes

O arquivo está no formato **Postman Collection v2.1**. O que varia entre clientes não são as
requisições, são os *scripts* — que é o que encadeia tokens e ids.

| Cliente | Situação |
|---|---|
| **Postman** | Verificado: 93 requisições, 202 asserções, 0 falhas |
| **Newman** (terminal) | Verificado, mesmo resultado |
| **Insomnia 13.3** | Verificado: 202/202 asserções no Collection Runner. O `openapi.json` passa no *Default OAS Ruleset* sem erro nem aviso |
| **Bruno, Hoppscotch, Thunder Client** | Importam requisições e pastas; os scripts podem não rodar — veja a última linha da tabela de problemas |

---

## Usuários do seed

Senha `Senha@123` para todos.

| E-mail | Perfil | Unidade |
|---|---|---|
| `admin@raizes.com.br` | ADMIN | — |
| `gerente.recife@raizes.com.br` | GERENTE | Recife |
| `gerente.caruaru@raizes.com.br` | GERENTE | Caruaru |
| `atendente.recife@raizes.com.br` | ATENDENTE | Recife |
| `cozinha.recife@raizes.com.br` | COZINHA | Recife |
| `cliente@exemplo.com` | CLIENTE | — |

Os ids do seed são fixos, o que permite escrever comandos de teste sem consultar o banco
antes. A lista completa está no [VALIDACAO.md](VALIDACAO.md).

**Em desenvolvimento, o código de verificação de e-mail é sempre `258369`.** É proposital:
dá para testar o fluxo de confirmação sem caixa de entrada. Em produção a chave
`app.verificacao-email.codigo-fixo` fica vazia e o código passa a ser sorteado.

---

## Decisões técnicas

### Por que Kotlin e Spring Boot

O curso trabalhou outras linguagens. A escolha é deliberada:

1. **Domínio da ferramenta.** Atuo como desenvolvedor Android nativo, onde Kotlin é a
   linguagem oficial. Usar a stack de maior domínio permite investir o tempo do projeto nas
   regras de negócio e na modelagem — que é o que está sendo avaliado — em vez de gastá-lo
   com sintaxe. Em contexto real, é a decisão correta de alocação de esforço.
2. **Experiência prévia em produção.** A fundação vem de um projeto próprio já em produção,
   trazendo padrões de segurança, tratamento de erro e migrations validados no uso real.
3. **Gestão de dependências do Spring Boot.** Os *starters* e o BOM curam versões
   compatíveis do ecossistema inteiro: declara-se `spring-boot-starter-web` e as versões
   transitivas vêm resolvidas e testadas em conjunto. Elimina a classe de problema de
   incompatibilidade entre bibliotecas, que em projeto montado peça a peça custa caro.
4. **Aderência do ecossistema ao que o projeto pede.** Spring Security, Spring Data JPA,
   Flyway, springdoc e Bean Validation cobrem os requisitos obrigatórios sem integração
   artesanal.
5. **Kotlin sobre Java.** Null-safety no compilador, `data class` para DTO sem boilerplate,
   enum expressivo para a máquina de estados do pedido — com interoperabilidade total com o
   ecossistema Spring.

A stack não foi escolhida por ser a do curso, mas por ser aquela em que consigo entregar a
solução mais madura no prazo.

### Por que UUID como chave primária

- **Não enumerável.** Id sequencial expõe volume de negócio — `/pedidos/9001` revela quantos
  pedidos a rede já teve — e permite varredura de recursos. Em sistema multi-unidade com
  dado de cliente sob LGPD, é superfície de ataque desnecessária.
- **Geração distribuída.** A chave não depende de *sequence* do banco, o que permite gerar
  na aplicação e escalar horizontalmente sem coordenação.
- **Sem colisão entre ambientes.** Facilita seed, replicação e consolidação de dados entre
  unidades.
- **Trade-off assumido:** ocupa 16 bytes contra 8 do `bigint`, e o índice fica menos
  compacto. Na escala deste sistema, o custo é aceitável e a contrapartida de segurança
  compensa. É uma troca, não uma escolha sem custo.

### Organização em camadas

A nomenclatura é a convencional do Spring Boot. Equivale assim ao vocabulário de camadas:

| Camada | Pacote no projeto | Responsabilidade |
|---|---|---|
| API (Interface/Controllers) | `controller/` + `dto/` | Rotas, contratos de request e response, status code |
| Application (Aplicação) | `service/` | Casos de uso, orquestração, transação |
| Domain (Domínio) | `entity/` + `domain/` | Entidades, enums, regras e invariantes do negócio |
| Infrastructure (Infraestrutura) | `repository/` + `config/` + `security/` | Persistência, integrações, detalhe técnico |

Preferi manter o layout convencional a renomear os pacotes. Qualquer pessoa do ecossistema
Spring abre `controller/`, `service/`, `repository/` e sabe o que esperar; um pacote
`application/` num projeto Spring faz quem lê parar para descobrir a convenção local — o
oposto do objetivo, que é clareza.

A separação não é só declarada, é verificável. Nos 11 controllers: zero acesso a repository,
zero entidade em retorno, zero `if`, `when`, `for` ou `while`. Os comandos que medem isso e a
discussão completa estão em [ARQUITETURA.md](ARQUITETURA.md).

---

## Padrão de erro

Todo erro responde no mesmo envelope:

```json
{
  "error": "ESTOQUE_INSUFICIENTE",
  "message": "Não ha quantidade suficiente para um ou mais itens.",
  "details": [
    { "field": "itens[Bolo de rolo].quantidade", "issue": "pedido: 999, disponivel: 22" }
  ],
  "timestamp": "2026-10-03T16:26:24.153105Z",
  "path": "/pedidos",
  "requestId": "a594dea3-da90-4531-b1af-fbc2a64f43f5"
}
```

O `requestId` vem do header `X-Request-Id` que o cliente mandou, ou é gerado. O mesmo valor
vai para o header da resposta, para o corpo do erro e para o log — então dá para pegar o id
que o usuário reclamou e achar a linha exata.

### Como os status são usados

| Status | Quando |
|---|---|
| **400** | O corpo não casa com o contrato: campo obrigatório ausente, enum inexistente, UUID mal formado |
| **401** | Não autenticado. Também em rota que não existe, quando não há token |
| **403** | Autenticado, mas o perfil não pode |
| **404** | O recurso não existe, ou não é visível para quem pediu |
| **409** | Conflito com o estado atual: estoque insuficiente, pedido já pago, transição inválida |
| **422** | O corpo casa com o contrato, mas o valor é inválido: senha fraca, preço negativo |

A distinção **400 contra 422** é intencional: 400 significa "não entendi o que você mandou",
422 significa "entendi e não aceito". Em Kotlin, campo não-nulo ausente falha na
desserialização do Jackson antes da Bean Validation, o que naturalmente produz 400.

**401 em rota inexistente sem token** também é escolha, não descuido: responder 404 ali diria
a quem não se autenticou quais caminhos existem e quais não, entregando o mapa da API.

---

## Endpoints

30 rotas, 38 operações. A referência interativa é o [Swagger](#swagger).

| Recurso | Operações |
|---|---|
| **Auth** | `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` |
| **Usuários** | `POST /usuarios` · `POST /usuarios/verificacao` · `POST /usuarios/verificacao/reenvio` · `POST /usuarios/operadores` · `GET /usuarios/me` |
| **Unidades** | `GET /unidades` · `GET /unidades/{id}` · `POST /unidades` · `PUT /unidades/{id}` |
| **Produtos** | `GET /produtos` · `GET /produtos/{id}` · `POST /produtos` · `PUT /produtos/{id}` · `DELETE /produtos/{id}` |
| **Cardápio** | `GET /unidades/{id}/cardapio` · `PUT /unidades/{id}/cardapio/{produtoId}` · `DELETE /unidades/{id}/cardapio/{produtoId}` |
| **Estoque** | `GET /unidades/{id}/estoque` · `POST /unidades/{id}/estoque/movimentacoes` · `GET /unidades/{id}/estoque/{produtoId}/movimentacoes` |
| **Pedidos** | `POST /pedidos` · `GET /pedidos` · `GET /pedidos/{id}` · `PATCH /pedidos/{id}/status` · `POST /pedidos/{id}/cancelamento` |
| **Pagamentos** | `POST /pedidos/{id}/pagamentos` · `GET /pagamentos/{id}` · `POST /pagamentos/callback` |
| **Fidelidade** | `GET /fidelidade/saldo` · `GET /fidelidade/extrato` · `POST /fidelidade/resgates` |
| **Consentimentos** | `POST /consentimentos` · `GET /consentimentos` · `DELETE /consentimentos/{id}` |
| **Auditoria** | `GET /auditoria` |

A auditoria **não tem rota de escrita nem de exclusão**, de propósito: sem endpoint que a
altere, não há como adulterar a prova pela API.

### Paginação

Listagens aceitam `page` (começa em 1) e `limit` (máximo 100), e respondem no mesmo envelope:

```json
{ "conteudo": [], "pagina": 1, "limite": 10, "totalItens": 2,
  "totalPaginas": 1, "primeira": true, "ultima": true }
```

---

## Segurança e LGPD

| Controle | Como |
|---|---|
| Autenticação | JWT com expiração de 15 minutos |
| Sessão | Refresh token com **rotação**: usado uma vez, não vale mais |
| Senha | BCrypt, com sal próprio por senha |
| Autorização | 5 perfis — ADMIN, GERENTE, ATENDENTE, COZINHA, CLIENTE |
| *Default deny* | Rota nova nasce protegida; só é pública se liberada explicitamente |
| Escopo por unidade | Gerente só administra a própria loja |
| Escopo por titular | Cliente só enxerga os próprios pedidos |
| Consentimento | Versionado por finalidade; revogação preserva a linha e grava `revogadoEm` |
| Minimização | Response nunca traz hash de senha nem dado que a tela não usa |
| Auditoria | 9 ações sensíveis, com antes e depois, na mesma transação da ação |
| Rastreabilidade | `X-Request-Id` ligando resposta, erro e log |

Duas decisões que merecem leitura, porque não são o caminho mais curto:

**O cadastro não revela se um e-mail já tem conta.** `POST /usuarios` responde **202 com a
mesma mensagem** exista ou não o endereço. Devolver 409 em e-mail duplicado seria mais
informativo e também seria um oráculo: qualquer pessoa descobriria quem tem conta na rede
testando endereços. Para isso funcionar sem bloquear quem é dono do e-mail, o cadastro só
completa depois de confirmar o código recebido.

**O login não revela existência nem pela mensagem, nem pelo relógio.** A primeira versão
saía mais cedo quando o usuário não existia, sem calcular o hash — e a diferença de tempo,
medida em ~58 ms, respondia "esse e-mail existe". Hoje os dois caminhos custam o mesmo.

O raciocínio completo, incluindo um terceiro furo da mesma família encontrado na confirmação
de e-mail, está em [DECISOES-SEGURANCA.md](DECISOES-SEGURANCA.md).

---

## Estrutura do projeto

```
src/main/kotlin/com/geanbrandao/raizes/api/
├── controller/     11 arquivos — rotas e contratos HTTP
├── dto/            13 — request e response
├── service/        15 — regras de negócio e casos de uso
├── entity/         16 — mapeamento das tabelas
├── domain/          9 — enums e regras do domínio
├── repository/     15 — acesso a dados
├── security/        8 — JWT, filtros, autorização, assinatura do callback
├── exception/       4 — erros da API e handler global
└── config/          3 — OpenAPI, request id, contexto

src/main/resources/
├── db/migration/   18 migrations Flyway
├── application.yaml          comum
├── application-dev.yaml      desenvolvimento
└── application-prod.yaml     produção, sem default para segredo
```

95 arquivos Kotlin, 8.167 linhas.

---

## O que ficou fora do escopo

Dívida consciente, registrada para não parecer esquecimento:

| Item | Por que |
|---|---|
| **Rate limiting** nos endpoints públicos | As dependências (Bucket4j, Caffeine) estão declaradas, mas o filtro não está ligado. O limite **não** está valendo |
| **Envio real de e-mail** | A porta `EnviadorDeEmail` está definida e a implementação de desenvolvimento escreve no log. Integrar provedor real não agrega ao objetivo do trabalho |
| **Job de limpeza** de token expirado | A tabela cresce indefinidamente. Token expirado já é recusado na validação, então não há falha de segurança — só acúmulo |
| **Gateway de pagamento real** | O mock com desfechos determinísticos demonstra melhor que um sandbox de terceiro, porque permite provocar recusa e timeout na hora |

---

## Entrega

| Evidência | Onde |
|---|---|
| Repositório | https://github.com/geanbrandao/raizes-do-nordeste-api |
| Swagger UI | `http://localhost:8080/swagger-ui.html` com a API no ar — ver [Como subir](#como-subir) |
| OpenAPI | `http://localhost:8080/v3/api-docs` |
| Roteiro de validação | [VALIDACAO.md](VALIDACAO.md) |
| Coleção de testes | [`postman/`](postman/) — 93 requisições, 202 asserções |
| Testes automatizados | `./gradlew test` — 196 testes |

---

## Documentos

| Documento | O que tem |
|---|---|
| [VALIDACAO.md](VALIDACAO.md) | Como subir e validar: fluxos por recurso e 28 cenários de erro, com o resultado esperado de cada um |
| [ARQUITETURA.md](ARQUITETURA.md) | Camadas, direção das dependências, onde mora cada regra de negócio, política de KDoc |
| [DECISOES-SEGURANCA.md](DECISOES-SEGURANCA.md) | Enumeração de e-mail, canal lateral de tempo, código fixo em dev, e o que ficou de fora |

---

## Licença

Projeto acadêmico. A rede Raízes do Nordeste é fictícia.
