-- Catalogo da rede. O preco aqui e a referencia da matriz; cada unidade pode
-- ter o proprio preco no cardapio.
CREATE TABLE produtos
(
    id            UUID          DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    nome          VARCHAR(120)  NOT NULL,
    descricao     VARCHAR(500)  NULL,
    categoria     VARCHAR(60)   NOT NULL,
    preco_base    NUMERIC(10, 2) NOT NULL,
    sazonal       BOOLEAN       NOT NULL            DEFAULT FALSE,
    ativo         BOOLEAN       NOT NULL            DEFAULT TRUE,
    criado_em     TIMESTAMP     NOT NULL            DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP     NOT NULL            DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_produtos_preco_base CHECK (preco_base >= 0)
);

CREATE INDEX idx_produtos_categoria ON produtos (categoria);
CREATE INDEX idx_produtos_ativo ON produtos (ativo);
