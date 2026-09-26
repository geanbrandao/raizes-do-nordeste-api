-- Campanhas de exemplo, uma de cada recorte de segmentação.
INSERT INTO campanhas (id, nome, descricao, tipo, valor, unidade_id, canal_pedido,
                       idade_minima, idade_maxima, vigencia_inicio, vigencia_fim, ativa)
VALUES
    -- Rede inteira, canal unico: incentiva o cliente a migrar para o app.
    ('50000000-0000-0000-0000-000000000001', 'Primeira compra no app',
     '10% de desconto em pedidos feitos pelo aplicativo', 'DESCONTO_PERCENTUAL', 10.00,
     NULL, 'APP', NULL, NULL, DATE '2026-01-01', DATE '2026-12-31', TRUE),

    -- Uma unidade so: ação local da loja de Caruaru.
    ('50000000-0000-0000-0000-000000000002', 'Cafe da manhã Caruaru',
     'R$ 3,00 de desconto na unidade de Caruaru', 'DESCONTO_FIXO', 3.00,
     '10000000-0000-0000-0000-000000000002', NULL, NULL, NULL,
     DATE '2026-01-01', DATE '2026-12-31', TRUE),

    -- Segmentada por idade: so roda para quem consentiu com perfilamento.
    ('50000000-0000-0000-0000-000000000003', 'Junino jovem',
     'Pontos em dobro no periodo junino para clientes de 18 a 30 anos', 'PONTOS_EXTRAS', 2.00,
     NULL, NULL, 18, 30, DATE '2026-06-01', DATE '2026-06-30', TRUE);
