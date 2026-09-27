# Decisões de segurança e privacidade

Registro das escolhas de segurança da API, com o porquê de cada uma.
Alimenta a seção de LGPD e Segurança do documento final e a justificativa de
trade-offs técnicos.

---

## 1. O cadastro público não revela se um e-mail já tem conta

### O problema

A forma óbvia de escrever um cadastro é: se o e-mail já existe, devolver `409` com
"e-mail já cadastrado". Isso é ótimo para o usuário e péssimo para a privacidade dele.

Com esse comportamento, qualquer pessoa consegue transformar o endpoint de cadastro
numa consulta: basta enviar uma lista de endereços e anotar quais deram conflito.
O resultado é uma lista de "quem é cliente da Raízes do Nordeste" — que é justamente
o tipo de dado pessoal que a LGPD manda proteger, e que a diretoria da rede
resumiu como *"conhecer o cliente é importante, mas respeitar sua privacidade é
obrigatório"*.

### A escolha

`POST /usuarios` devolve **sempre** `202` com a mesma mensagem, exista ou não o e-mail:

```json
{ "mensagem": "Se o e-mail informado puder ser usado, enviamos um codigo de verificação para ele." }
```

Quando o e-mail já existe, nada é criado e nada é dito. Há teste garantindo que as duas
respostas são idênticas — mesmo status, mesmo corpo.

### Por que isso exige verificação de e-mail

A resposta genérica sozinha não fecha nada: sem confirmação, seria possível criar uma
conta usando o endereço de outra pessoa e usá-la normalmente. Por isso a conta nasce
com `email_verificado = false` e **não faz login** até confirmar um código de 6 dígitos
enviado ao dono do endereço. As duas peças só funcionam juntas.

### Onde o 409 continua existindo

No cadastro de **operador** (`POST /usuarios/operadores`). Ali quem chama já é admin ou
gerente autenticado, então não há enumeração a evitar, e esconder o motivo do erro só
atrapalharia quem está cadastrando a equipe. A regra é: resposta genérica em endpoint
público, erro explícito em endpoint autenticado.

---

## 2. Login não revela se um e-mail existe — nem pela mensagem, nem pelo relógio

### Mensagem

E-mail inexistente e senha errada devolvem exatamente o mesmo `401`
`CREDENCIAIS_INVALIDAS` com o mesmo texto. Diferenciar os dois casos seria a mesma
falha da seção anterior, por outra porta.

### Tempo

Mensagem igual não basta. A implementação original era:

```kotlin
if (usuario == null || !passwordEncoder.matches(senha, usuario.senhaHash))
```

O `||` faz curto-circuito: com e-mail inexistente o BCrypt nunca rodava. Medindo o
custo do BCrypt cost 10 na máquina de desenvolvimento: **cerca de 58 ms**. Ou seja, a
resposta voltava em ~1 ms para e-mail desconhecido e ~58 ms para e-mail conhecido com
senha errada. O texto era idêntico, mas o cronômetro entregava a informação — e isso é
automatizável em escala.

A correção roda a comparação também quando o usuário não existe, contra um hash
descartável, para os dois caminhos custarem o mesmo.

Como medir tempo em teste dá resultado instável, a garantia é indireta: um teste de
unidade verifica que `passwordEncoder.matches` é chamado mesmo com usuário nulo. Sem
ele, um curto-circuito futuro reabriria o canal sem nenhum teste reclamar.

---

## 3. Código de verificação fixo em desenvolvimento

`258369` em todo cadastro feito no perfil `dev`.

Isso existe porque quem for avaliar ou testar a API não tem caixa de entrada para
consultar. Um código aleatório obrigaria a caçar o valor no log a cada teste, o que
torna o ambiente difícil de reproduzir.

O mecanismo é o mesmo nos dois ambientes — código gerado, com validade de 24 horas,
limite de 5 tentativas e confirmação obrigatória. **Só a origem do número muda:**

| Ambiente | `app.verificacao-email.codigo-fixo` | Código |
|---|---|---|
| dev | `258369` | sempre o mesmo |
| produção | vazio | sorteado com `SecureRandom` |

Quando o modo fixo está ligado, a aplicação registra um `WARN` no startup, para não
passar despercebido se for parar onde não devia.

Os usuários do seed já nascem com `email_verificado = true`, para o fluxo principal
(login → pedido → pagamento) rodar sem passar por confirmação nenhuma.

---

## 4. Envio de e-mail: porta definida, integração adiada

O sistema depende da interface `EnviadorDeEmail`, não de um provedor. A única
implementação hoje é `EnviadorDeEmailLog`.

Isso é decisão consciente de escopo. Integrar com Resend, SES ou SMTP exigiria chave de
API, domínio verificado e custo, e não acrescentaria nada ao que este trabalho se propõe
a demonstrar — o fluxo de verificação em si já é real. Para produção, bastaria uma
classe nova implementando `EnviadorDeEmail` e o registro dela como bean no lugar da
atual, sem tocar em regra de negócio.

### O código não vai para o log fora de desenvolvimento

Log não é lugar de credencial: ele vai para agregador, fica retido por meses e é lido
por gente que não deveria ver código de autenticação. Então o comportamento do
`EnviadorDeEmailLog` depende do ambiente:

| Ambiente | Registro no log |
|---|---|
| dev (`codigo-fixo` preenchido) | destinatário **e** código — o valor é fixo, público e documentado |
| qualquer outro | só o destinatário; o valor é omitido por ser credencial |

Fora de desenvolvimento o código fica, na prática, inacessível — e isso é proposital.
Escancara que falta um provedor real, em vez de deixar a aplicação funcionando pela
metade em silêncio. Um `WARN` sobe no startup dizendo exatamente isso.

Há teste garantindo que o valor do código nunca aparece no log quando o modo fixo está
desligado.

### O que fica para essa etapa futura

- implementar um `EnviadorDeEmail` real e registrá-lo como bean;
- avisar o dono do endereço quando alguém tenta se cadastrar com um e-mail que já tem
  conta (hoje isso só vira uma linha de log, sem o valor);
- rotina de limpeza dos códigos expirados.

---

## 5. Outras decisões já em vigor

| Decisão | Motivo |
|---|---|
| Senha com hash BCrypt | Nunca armazenar nem devolver senha em texto |
| `senhaHash` fora de todo DTO de resposta | Testes garantem que não vaza em nenhuma rota |
| JWT de 15 min + refresh de 7 dias com rotação | Refresh reusado é recusado: roubo de token aparece em vez de durar para sempre |
| *Default deny* no `SecurityConfig` | Endpoint novo nasce protegido; liberar é ato explícito |
| 403 para perfil sem permissão, 404 para inexistente | 401 = não sei quem é; 403 = sei e não pode; 404 = não existe |
| Rota desconhecida sem token devolve 401 | Evita que alguém mapeie as rotas da API sem credencial |
| Consentimento LGPD versionado, revogação não apaga a linha | Preserva a prova de que o tratamento era legítimo na época |
| `data_nascimento` opcional no cadastro | Minimização: só é coletada se a pessoa quiser participar de campanha segmentada |
| Trilha de auditoria append-only | Nenhuma rotina faz update ou delete, senão deixa de servir como prova |

---

## 6. O que ficou de fora, e por quê

| Item | Motivo |
|---|---|
| Envio real de e-mail | Fora do escopo; porta definida, troca é de uma classe |
| Rate limiting nos endpoints públicos | Planejado (RNF08); `bucket4j` já é dependência |
| CAPTCHA no cadastro | Desproporcional para o contexto |
| Bloqueio de conta após N logins falhos | Traria risco de negação de serviço contra usuário legítimo |
| Refresh token com hash no banco | Token é opaco e aleatório; ganho pequeno frente ao custo |
| Perfil `prod` | Não existe hoje. Só há `dev`, e o Dockerfile o fixa. Criar um perfil de produção sem provedor de e-mail, sem segredo gerenciado e sem observabilidade seria fachada |
