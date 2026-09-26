-- Consentimento LGPD. Versionado porque se a finalidade mudar o cliente precisa
-- aceitar de novo. Revogação não apaga a linha: guarda a data em revogado_em,
-- senão se perde a prova de que houve consentimento na epoca.
CREATE TABLE consentimentos
(
    id               UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    usuario_id       UUID         NOT NULL,
    finalidade       VARCHAR(60)  NOT NULL,
    versao_documento VARCHAR(20)  NOT NULL,
    aceito_em        TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    revogado_em      TIMESTAMP    NULL,
    ip               VARCHAR(45)  NULL,

    CONSTRAINT fk_consentimentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT chk_consentimentos_finalidade CHECK (finalidade IN ('FIDELIDADE', 'MARKETING', 'PERFILAMENTO'))
);

CREATE INDEX idx_consentimentos_usuario ON consentimentos (usuario_id);
