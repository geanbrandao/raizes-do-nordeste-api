# Arquitetura

Como o código está organizado, por quê, e como dá para conferir que a organização se
sustenta. Esta é a fonte da seção de arquitetura do README e do PDF da entrega.

**Tamanho:** 94 arquivos Kotlin, 8.023 linhas, 9 pacotes.

---

## 1. Mapeamento para o vocabulário do roteiro

O roteiro fala em API, Application, Domain e Infrastructure. O projeto usa a nomenclatura
convencional do Spring Boot. É a mesma separação com outros nomes:

| Camada do roteiro | Pacote no projeto | Responsabilidade |
|---|---|---|
| API (Interface/Controllers) | `controller/` + `dto/` | Rotas, contratos de request e response, status code |
| Application (Aplicação) | `service/` | Casos de uso, orquestração, transação |
| Domain (Domínio) | `entity/` + `domain/` | Entidades, enums, regras e invariantes do negócio |
| Infrastructure (Infraestrutura) | `repository/` + `config/` + `security/` + gateway mock | Persistência, integrações, detalhe técnico |

A escolha foi manter o layout convencional em vez de renomear os pacotes para o vocabulário
do roteiro. O roteiro admite a variação explicitamente — pede que "a separação de
responsabilidades fique clara no código e na documentação" e avalia "a coerência e clareza
da organização, e não a adesão a um framework específico".

Renomear traria o custo oposto ao objetivo: qualquer pessoa do ecossistema Spring abre
`controller/`, `service/`, `repository/` e sabe o que esperar, enquanto um pacote
`application/` num projeto Spring faz quem lê parar para descobrir a convenção local.

---

## 2. Direção das dependências

A regra é uma só: **a dependência aponta para dentro.** Camada de fora conhece a de dentro,
nunca o contrário.

```
controller/ ──> service/ ──> repository/ ──> entity/
     │              │                           ▲
     └──> dto/      └──> domain/ ───────────────┘
```

O que isso proíbe, na prática:

| Regra | Por que |
|---|---|
| `controller/` não importa `entity/` | Entidade é formato de banco. Expor direto vaza coluna que ninguém pediu (`senhaHash`) e amarra o contrato da API ao schema: renomear coluna viraria mudança de contrato |
| `controller/` não importa `repository/` | Pular o service tira a regra de negócio e a transação do lugar onde elas são testadas |
| `dto/` não importa `entity/` | Mesma razão, pelo outro lado: o contrato precisa poder mudar sem o banco mudar |
| `service/` não importa nada de `org.springframework.web` | Service que conhece `HttpServletRequest` não dá para reaproveitar fora de uma requisição HTTP |
| `entity/` e `domain/` não importam camada nenhuma | São o centro. Se o domínio depende de infraestrutura, não existe mais domínio |

### Como está medido

Contagem de arquivos que importam cada camada, por pacote de origem:

| Pacote | Importa `entity/` | Importa `repository/` |
|---|---|---|
| `repository/` | 15 | — |
| `service/` | 13 | 13 |
| `security/` | 1 *(exceção, abaixo)* | 0 |
| `controller/` | **0** | **0** |
| `dto/` | **0** | **0** |

E nos 11 controllers: zero acesso a repository, zero entidade em retorno, zero `if`, `when`,
`for` ou `while`. Controller só recebe, delega e devolve.

Dá para reconferir a qualquer momento:

```bash
cd src/main/kotlin/com/geanbrandao/raizes/api
grep -l 'Repository' controller/*.kt            # esperado: nenhum
grep -l 'import.*\.entity\.' controller/*.kt    # esperado: nenhum
grep -l 'import.*\.entity\.' dto/*.kt           # esperado: nenhum
grep -lE 'org.springframework.web' service/*.kt # esperado: nenhum
```

### A exceção, e por que ela fica

`security/JwtService` recebe `UsuarioEntity` para emitir o token. É o único ponto fora de
`service/` e `repository/` que toca entidade.

A alternativa seria passar os três valores que o token usa — id, perfil e unidade. Ela foi
recusada porque id e unidade são ambos `UUID` e ficariam lado a lado na assinatura: trocar
um pelo outro na chamada compila e passa pelos testes, e o efeito seria um token dizendo que
a pessoa é gerente de outra loja. A entidade é autodescritiva e o método tem um único
chamador (`AuthService`). Entre uma exceção documentada e um parâmetro fácil de inverter, a
exceção é o risco menor.

---

## 3. Onde mora cada regra de negócio

As 14 regras do documento de requisitos, e o arquivo que as implementa:

| Regra | Onde |
|---|---|
| RN01 produto tem que estar no cardápio da unidade | `PedidoService` + `CardapioService` |
| RN02 preço congelado no item | `PedidoService` |
| RN03 total calculado no servidor | `PedidoService` |
| RN04 estoque debitado na criação; faltou → 409 | `EstoqueService` |
| RN05 canal ausente ou inválido → 400 | `dto/` (Bean Validation) + `GlobalExceptionHandler` |
| RN06 pedido nasce em `AGUARDANDO_PAGAMENTO` | `PedidoService` |
| RN07 aprovado → `PAGO`, recusado → `PAGAMENTO_RECUSADO` | `PagamentoService` |
| RN08 transição só pela máquina de estados | `domain/StatusPedido` + `PedidoService` |
| RN09 cancelar antes de `PRONTO` e devolver estoque | `PedidoService` + `EstoqueService` |
| RN10 pontos só com pedido pago e consentimento ativo | `FidelidadeService` + `ConsentimentoService` |
| RN11 resgate exige saldo | `FidelidadeService` |
| RN12 cliente vê só os próprios pedidos | `PedidoService` |
| RN13 ação sensível gera auditoria na mesma transação | `AuditoriaService`, chamado pelos services |
| RN14 idempotência no pagamento | `PagamentoService` |

Repare que **RN08 mora no enum**, não no service: `StatusPedido.transicoesPermitidas()`
carrega a máquina de estados. O service pergunta ao domínio se a transição vale, em vez de
ter uma cadeia de `when` decidindo isso. Regra de domínio no domínio.

### Os 15 services

| Service | Assunto |
|---|---|
| `AuthService` | Login, renovação e encerramento de sessão |
| `UsuarioService` | Cadastro e consulta de usuários |
| `VerificacaoEmailService` | Emissão e conferência do código de e-mail |
| `UnidadeService` | Unidades da rede |
| `ProdutoService` | Catálogo de produtos |
| `CardapioService` | Cardápio de cada unidade |
| `EstoqueService` | Saldo e movimentação por unidade |
| `PedidoService` | **Fluxo crítico** |
| `CampanhaService` | Desconto por canal, unidade e faixa de idade |
| `PagamentoService` | Solicitação, callback e idempotência |
| `FidelidadeService` | Pontos |
| `ConsentimentoService` | Base legal do tratamento (LGPD) |
| `AuditoriaService` | Trilha das ações sensíveis |
| `EnviadorDeEmail` | **Porta** de saída para e-mail |
| `GatewayPagamento` | **Porta** de saída para pagamento |

As duas últimas são interfaces, não implementações. O domínio define o que precisa; quem
cumpre fica de fora. Hoje existem `EnviadorDeEmailLog` e `GatewayPagamentoMock`; trocar por
provedor real é escrever outra implementação, sem tocar em `PedidoService` nem em
`VerificacaoEmailService`.

---

## 4. O caminho de uma requisição

`POST /pedidos` com token de cliente, que é o fluxo que mais atravessa camadas:

| # | Onde | O que acontece |
|---|---|---|
| 1 | `config/RequestIdFilter` | Garante o `X-Request-Id`, põe no MDC do log |
| 2 | `security/JwtAuthFilter` | Lê o token, popula o contexto. Token ruim não derruba aqui |
| 3 | `security/SecurityConfig` | Decide se a rota exigia autenticação. É daqui que sai o 401 |
| 4 | `dto/CriarPedidoRequest` | Bean Validation no corpo. Campo faltando → 400 |
| 5 | `controller/PedidoController` | Extrai o solicitante e delega. Nada mais |
| 6 | `service/PedidoService` | Abre a transação e orquestra: cardápio, estoque, campanha, total |
| 7 | `service/EstoqueService` | Debita. Saldo insuficiente → 409, pedido nenhum é criado |
| 8 | `service/AuditoriaService` | Registra `PEDIDO_CRIADO` na **mesma** transação |
| 9 | `repository/` | Persiste pedido e itens |
| 10 | `service/PedidoService` | Monta o `PedidoResponse` — a entidade não sai daqui |
| 11 | `exception/GlobalExceptionHandler` | Se algo estourou, traduz para o envelope de erro padrão |

O passo 7 é o que define a semântica de **tudo ou nada**: a validação de estoque percorre
todos os itens e junta os faltantes num único 409 com a lista, em vez de falhar no primeiro.
Quem recebe o erro sabe de uma vez o que precisa tirar do carrinho.

---

## 5. Política de KDoc

Português simples, formato do FinancialPlanner: descrição, `@param`, `@return`, `@throws`
quando o método lança. O que o KDoc precisa dizer é **por que**, não o que o nome já diz.

Cobertura nas declarações públicas:

| Pacote | Públicas | Sem KDoc | Cobertura |
|---|---|---|---|
| `controller/` | 49 | 0 | 100% |
| `dto/` | 37 | 0 | 100% |
| `entity/` | 22 | 0 | 100% |
| `domain/` | 11 | 0 | 100% |
| `exception/` | 23 | 0 | 100% |
| `security/` | 19 | 0 | 100% |
| `config/` | 7 | 0 | 100% |
| `service/` | 79 | 2 | 97% |
| `repository/` | 51 | 22 | 56% |
| **Total** | **298** | **24** | **91%** |

Os 24 sem KDoc são deliberados, de dois tipos:

- **22 queries derivadas do Spring Data** — `findAllByUnidadeId`,
  `existsByUsuarioIdAndFinalidadeAndRevogadoEmIsNull` e parentes. O nome **é** a
  especificação: o Spring gera a consulta a partir dele. KDoc aqui só repetiria o nome em
  prosa, e repetição envelhece mal — muda o método, esquece o comentário, e o comentário
  passa a mentir. As consultas com `@Query` escrito à mão, que é onde existe decisão
  (`buscarComFiltros`, `buscarCandidatas`), estão documentadas. As interfaces têm KDoc de
  classe explicando o conjunto.
- **2 implementações de interface** — `enviarCodigoDeVerificacao` e `solicitar`, cujos
  contratos estão documentados na porta. A documentação é herdada; duplicar criaria duas
  versões para divergir.

---

## 6. Decisões de arquitetura registradas

| Decisão | Onde está justificada |
|---|---|
| Nomenclatura das camadas | Seção 1 deste documento, e **D4** no documento de requisitos |
| UUID em vez de id numérico | **D2**, com o trade-off de 16 contra 8 bytes assumido |
| Kotlin + Spring Boot | **2.1** do documento de requisitos |
| 403 para perfil sem permissão, 404 para inexistente | **D1** |
| 400 para corpo fora do contrato, 422 para valor inválido | **D8** |
| 401 em rota inexistente sem token | **D9** |
| Enumeração de e-mail e canal lateral de tempo | `DECISOES-SEGURANCA.md` |
| Portas para e-mail e pagamento | Seção 3 deste documento |
