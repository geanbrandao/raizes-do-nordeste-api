-- Usuarios do sistema: clientes e operadores das unidades.
-- unidade_id so e preenchido para operador (GERENTE, ATENDENTE, COZINHA).
CREATE TABLE usuarios
(
    id            UUID         DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL,
    unidade_id    UUID         NULL,
    data_nascimento DATE       NULL,
    ativo         BOOLEAN      NOT NULL             DEFAULT TRUE,
    criado_em     TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP    NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT fk_usuarios_unidade FOREIGN KEY (unidade_id) REFERENCES unidades (id),
    CONSTRAINT chk_usuarios_perfil CHECK (perfil IN ('ADMIN', 'GERENTE', 'ATENDENTE', 'COZINHA', 'CLIENTE')),
    -- Operador precisa de unidade, cliente e admin não podem ter.
    CONSTRAINT chk_usuarios_unidade_por_perfil CHECK (
        (perfil IN ('GERENTE', 'ATENDENTE', 'COZINHA') AND unidade_id IS NOT NULL)
            OR (perfil IN ('ADMIN', 'CLIENTE') AND unidade_id IS NULL)
        )
);

CREATE INDEX idx_usuarios_unidade ON usuarios (unidade_id);
CREATE INDEX idx_usuarios_perfil ON usuarios (perfil);
