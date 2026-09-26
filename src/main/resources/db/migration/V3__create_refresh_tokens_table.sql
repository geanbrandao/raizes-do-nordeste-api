-- Refresh token opaco. O access token e JWT e não fica no banco.
CREATE TABLE refresh_tokens
(
    id         UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    usuario_id UUID         NOT NULL,
    token      VARCHAR(255) NOT NULL,
    expira_em  TIMESTAMP    NOT NULL,
    revogado   BOOLEAN      NOT NULL             DEFAULT FALSE,
    criado_em  TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id);
