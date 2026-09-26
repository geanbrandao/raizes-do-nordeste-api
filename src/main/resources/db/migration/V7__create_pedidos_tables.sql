-- Pedido. canal_pedido e NOT NULL de proposito: e requisito do projeto que todo
-- pedido saiba por onde entrou, para a matriz acompanhar venda por canal.
CREATE TABLE pedidos
(
    id            UUID           DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    unidade_id    UUID           NOT NULL,
    cliente_id    UUID           NULL,
    canal_pedido  VARCHAR(20)    NOT NULL,
    status        VARCHAR(30)    NOT NULL,
    subtotal      NUMERIC(10, 2) NOT NULL,
    desconto      NUMERIC(10, 2) NOT NULL            DEFAULT 0,
    total         NUMERIC(10, 2) NOT NULL,
    criado_em     TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP      NOT NULL            DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pedidos_unidade FOREIGN KEY (unidade_id) REFERENCES unidades (id),
    CONSTRAINT fk_pedidos_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios (id),
    CONSTRAINT chk_pedidos_canal CHECK (canal_pedido IN ('APP', 'TOTEM', 'BALCAO', 'PICKUP', 'WEB')),
    CONSTRAINT chk_pedidos_status CHECK (status IN
                                         ('AGUARDANDO_PAGAMENTO', 'PAGO', 'PAGAMENTO_RECUSADO',
                                          'EM_PREPARO', 'PRONTO', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT chk_pedidos_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_pedidos_desconto CHECK (desconto >= 0),
    CONSTRAINT chk_pedidos_total CHECK (total >= 0)
);

-- Indices pensados nas consultas que a API expõe: listagem por unidade + status
-- e o filtro por canal exigido no roteiro.
CREATE INDEX idx_pedidos_unidade_status ON pedidos (unidade_id, status);
CREATE INDEX idx_pedidos_canal ON pedidos (canal_pedido);
CREATE INDEX idx_pedidos_cliente ON pedidos (cliente_id);
CREATE INDEX idx_pedidos_criado_em ON pedidos (criado_em);

-- Item do pedido. preco_unitario e congelado na criação: se o cardapio mudar de
-- preco depois, o pedido antigo continua valendo o que valia na hora.
CREATE TABLE itens_pedido
(
    id              UUID           DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    pedido_id       UUID           NOT NULL,
    produto_id      UUID           NOT NULL,
    nome_produto    VARCHAR(120)   NOT NULL,
    quantidade      INTEGER        NOT NULL,
    preco_unitario  NUMERIC(10, 2) NOT NULL,
    subtotal        NUMERIC(10, 2) NOT NULL,

    CONSTRAINT fk_itens_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE,
    CONSTRAINT fk_itens_pedido_produto FOREIGN KEY (produto_id) REFERENCES produtos (id),
    CONSTRAINT chk_itens_pedido_quantidade CHECK (quantidade > 0),
    CONSTRAINT chk_itens_pedido_preco CHECK (preco_unitario >= 0),
    CONSTRAINT chk_itens_pedido_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX idx_itens_pedido_pedido ON itens_pedido (pedido_id);
