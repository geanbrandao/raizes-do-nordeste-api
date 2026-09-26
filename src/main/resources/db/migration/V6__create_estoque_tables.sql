-- Saldo de estoque por unidade e produto. Uma linha por combinação.
CREATE TABLE estoque
(
    id            UUID      DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    unidade_id    UUID      NOT NULL,
    produto_id    UUID      NOT NULL,
    saldo_atual   INTEGER   NOT NULL             DEFAULT 0,
    saldo_minimo  INTEGER   NOT NULL             DEFAULT 0,
    atualizado_em TIMESTAMP NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    -- Lock otimista. Em horario de pico dois pedidos podem baixar o mesmo item ao
    -- mesmo tempo; sem isso os dois leriam o mesmo saldo e o estoque ficaria errado.
    versao        BIGINT    NOT NULL             DEFAULT 0,

    CONSTRAINT uq_estoque_unidade_produto UNIQUE (unidade_id, produto_id),
    CONSTRAINT fk_estoque_unidade FOREIGN KEY (unidade_id) REFERENCES unidades (id) ON DELETE CASCADE,
    CONSTRAINT fk_estoque_produto FOREIGN KEY (produto_id) REFERENCES produtos (id),
    CONSTRAINT chk_estoque_saldo_atual CHECK (saldo_atual >= 0),
    CONSTRAINT chk_estoque_saldo_minimo CHECK (saldo_minimo >= 0)
);

CREATE INDEX idx_estoque_unidade ON estoque (unidade_id);

-- Historico de toda mudança de saldo. Append-only: nunca se edita linha antiga.
CREATE TABLE movimentacoes_estoque
(
    id          UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    estoque_id  UUID         NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,
    quantidade  INTEGER      NOT NULL,
    saldo_apos  INTEGER      NOT NULL,
    motivo      VARCHAR(255) NULL,
    pedido_id   UUID         NULL,
    usuario_id  UUID         NULL,
    criado_em   TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mov_estoque_estoque FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE CASCADE,
    CONSTRAINT fk_mov_estoque_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT chk_mov_estoque_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE')),
    CONSTRAINT chk_mov_estoque_quantidade CHECK (quantidade > 0)
);

CREATE INDEX idx_mov_estoque_estoque ON movimentacoes_estoque (estoque_id);
CREATE INDEX idx_mov_estoque_pedido ON movimentacoes_estoque (pedido_id);
