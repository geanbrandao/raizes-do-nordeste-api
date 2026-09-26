-- Cardapio por unidade: quais produtos a unidade vende, por quanto e se esta
-- disponivel agora. E o que resolve "nem todas as unidades são identicas".
CREATE TABLE cardapio_unidade
(
    id            UUID           DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    unidade_id    UUID           NOT NULL,
    produto_id    UUID           NOT NULL,
    preco         NUMERIC(10, 2) NOT NULL,
    disponivel    BOOLEAN        NOT NULL            DEFAULT TRUE,
    criado_em     TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cardapio_unidade_produto UNIQUE (unidade_id, produto_id),
    CONSTRAINT fk_cardapio_unidade FOREIGN KEY (unidade_id) REFERENCES unidades (id) ON DELETE CASCADE,
    CONSTRAINT fk_cardapio_produto FOREIGN KEY (produto_id) REFERENCES produtos (id),
    CONSTRAINT chk_cardapio_preco CHECK (preco >= 0)
);

CREATE INDEX idx_cardapio_unidade ON cardapio_unidade (unidade_id);
