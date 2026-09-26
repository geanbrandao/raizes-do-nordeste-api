-- Trilha de auditoria das ações sensiveis. Append-only: nenhuma rotina do sistema
-- faz UPDATE ou DELETE aqui, senão a trilha perde o valor como prova.
CREATE TABLE logs_auditoria
(
    id               UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    usuario_id       UUID         NULL,
    acao             VARCHAR(60)  NOT NULL,
    entidade         VARCHAR(60)  NOT NULL,
    entidade_id      UUID         NULL,
    dados_anteriores TEXT         NULL,
    dados_novos      TEXT         NULL,
    ip               VARCHAR(45)  NULL,
    criado_em        TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_logs_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

CREATE INDEX idx_logs_auditoria_usuario ON logs_auditoria (usuario_id);
CREATE INDEX idx_logs_auditoria_entidade ON logs_auditoria (entidade, entidade_id);
CREATE INDEX idx_logs_auditoria_criado_em ON logs_auditoria (criado_em);
