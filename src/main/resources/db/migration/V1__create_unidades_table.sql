-- Unidades da rede. A matriz cadastra, cada unidade tem estoque e cardapio proprios.
CREATE TABLE unidades
(
    id              UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL,
    cidade          VARCHAR(120) NOT NULL,
    uf              VARCHAR(2)   NOT NULL,
    tipo_operacao   VARCHAR(20)  NOT NULL,
    ativa           BOOLEAN      NOT NULL             DEFAULT TRUE,
    criado_em       TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    atualizado_em   TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_unidades_tipo_operacao CHECK (tipo_operacao IN ('COMPLETA', 'REDUZIDA'))
);

CREATE INDEX idx_unidades_ativa ON unidades (ativa);
