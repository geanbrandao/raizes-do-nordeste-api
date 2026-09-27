-- Codigos de verificação de e-mail.
--
-- O codigo tem 6 digitos e vale 24 horas. A linha não e apagada depois de usada:
-- fica com used = true para a trilha de auditoria conseguir mostrar quando aquela
-- conta foi confirmada.
--
-- tentativas_falhas existe para o codigo de 6 digitos não virar alvo de força
-- bruta: são so um milhão de combinações, e sem limite de tentativas alguem
-- confirmaria a conta de outra pessoa em pouco tempo.
CREATE TABLE tokens_verificacao_email
(
    id                UUID        DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    usuario_id        UUID        NOT NULL,
    codigo            VARCHAR(6)  NOT NULL,
    expira_em         TIMESTAMP   NOT NULL,
    usado             BOOLEAN     NOT NULL                  DEFAULT FALSE,
    tentativas_falhas INTEGER     NOT NULL                  DEFAULT 0,
    criado_em         TIMESTAMP   NOT NULL                  DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tokens_verificacao_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT chk_tokens_verificacao_tentativas CHECK (tentativas_falhas >= 0)
);

CREATE INDEX idx_tokens_verificacao_usuario ON tokens_verificacao_email (usuario_id);
