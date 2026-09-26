-- Conta de fidelidade do cliente. Uma por cliente.
CREATE TABLE contas_fidelidade
(
    id            UUID      DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    cliente_id    UUID      NOT NULL,
    saldo_pontos  INTEGER   NOT NULL             DEFAULT 0,
    ativa         BOOLEAN   NOT NULL             DEFAULT TRUE,
    criado_em     TIMESTAMP NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    versao        BIGINT    NOT NULL             DEFAULT 0,

    CONSTRAINT uq_contas_fidelidade_cliente UNIQUE (cliente_id),
    CONSTRAINT fk_contas_fidelidade_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT chk_contas_fidelidade_saldo CHECK (saldo_pontos >= 0)
);

-- Extrato de pontos. Append-only, igual ao de estoque.
CREATE TABLE movimentacoes_pontos
(
    id         UUID        DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    conta_id   UUID        NOT NULL,
    tipo       VARCHAR(20) NOT NULL,
    pontos     INTEGER     NOT NULL,
    saldo_apos INTEGER     NOT NULL,
    pedido_id  UUID        NULL,
    descricao  VARCHAR(255) NULL,
    criado_em  TIMESTAMP   NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mov_pontos_conta FOREIGN KEY (conta_id) REFERENCES contas_fidelidade (id) ON DELETE CASCADE,
    CONSTRAINT fk_mov_pontos_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id),
    CONSTRAINT chk_mov_pontos_tipo CHECK (tipo IN ('ACUMULO', 'RESGATE')),
    CONSTRAINT chk_mov_pontos_pontos CHECK (pontos > 0)
);

CREATE INDEX idx_mov_pontos_conta ON movimentacoes_pontos (conta_id);
