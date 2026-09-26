-- Campanhas e promoções da rede.
--
-- A segmentação e feita por tres recortes opcionais, e cada um nulo significa
-- "vale para todos":
--   unidade_id   -> campanha de uma loja so, ou da rede inteira
--   canal_pedido -> promoção exclusiva de um canal (ex.: so no app)
--   idade_*      -> faixa etaria, que so pode ser usada com consentimento do
--                   cliente, porque perfilar por idade e tratamento de dado
--                   pessoal sem base contratual
CREATE TABLE campanhas
(
    id               UUID           DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    nome             VARCHAR(120)   NOT NULL,
    descricao        VARCHAR(500)   NULL,
    tipo             VARCHAR(30)    NOT NULL,
    valor            NUMERIC(10, 2) NOT NULL,
    unidade_id       UUID           NULL,
    canal_pedido     VARCHAR(20)    NULL,
    idade_minima     INTEGER        NULL,
    idade_maxima     INTEGER        NULL,
    vigencia_inicio  DATE           NOT NULL,
    vigencia_fim     DATE           NOT NULL,
    ativa            BOOLEAN        NOT NULL                  DEFAULT TRUE,
    criado_em        TIMESTAMP      NOT NULL                  DEFAULT CURRENT_TIMESTAMP,
    atualizado_em    TIMESTAMP      NOT NULL                  DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_campanhas_unidade FOREIGN KEY (unidade_id) REFERENCES unidades (id) ON DELETE CASCADE,
    CONSTRAINT chk_campanhas_tipo CHECK (tipo IN ('DESCONTO_PERCENTUAL', 'DESCONTO_FIXO', 'PONTOS_EXTRAS')),
    CONSTRAINT chk_campanhas_canal CHECK (canal_pedido IS NULL
        OR canal_pedido IN ('APP', 'TOTEM', 'BALCAO', 'PICKUP', 'WEB')),
    CONSTRAINT chk_campanhas_valor CHECK (valor >= 0),
    -- Percentual não pode passar de 100, senão o desconto viraria credito.
    CONSTRAINT chk_campanhas_percentual CHECK (tipo <> 'DESCONTO_PERCENTUAL' OR valor <= 100),
    CONSTRAINT chk_campanhas_vigencia CHECK (vigencia_fim >= vigencia_inicio),
    CONSTRAINT chk_campanhas_idade CHECK (idade_minima IS NULL OR idade_maxima IS NULL
        OR idade_maxima >= idade_minima)
);

CREATE INDEX idx_campanhas_unidade ON campanhas (unidade_id);
CREATE INDEX idx_campanhas_vigencia ON campanhas (ativa, vigencia_inicio, vigencia_fim);
