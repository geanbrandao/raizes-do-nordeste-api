-- Pagamento desacoplado do pedido: a rede so pede e registra o que voltou.
-- E 1:N porque o cliente pode tentar de novo depois de uma recusa.
CREATE TABLE pagamentos
(
    id                   UUID           DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    pedido_id            UUID           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    valor                NUMERIC(10, 2) NOT NULL,
    metodo               VARCHAR(30)    NOT NULL,
    id_transacao_externa VARCHAR(100)   NULL,
    chave_idempotencia   VARCHAR(100)   NOT NULL,
    payload_retorno      TEXT           NULL,
    mensagem             VARCHAR(255)   NULL,
    tentativas           INTEGER        NOT NULL            DEFAULT 1,
    criado_em            TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,
    atualizado_em        TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pagamentos_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE,
    -- Garante a idempotencia da solicitação: reenvio com a mesma chave não gera
    -- cobrança nova, so devolve o resultado que ja existe.
    CONSTRAINT uq_pagamentos_idempotencia UNIQUE (chave_idempotencia),
    CONSTRAINT chk_pagamentos_status CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'ESTORNADO')),
    CONSTRAINT chk_pagamentos_valor CHECK (valor >= 0)
);

CREATE INDEX idx_pagamentos_pedido ON pagamentos (pedido_id);
