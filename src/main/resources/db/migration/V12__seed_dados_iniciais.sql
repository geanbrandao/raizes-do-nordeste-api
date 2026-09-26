-- Dados iniciais para conseguir rodar e testar a API logo depois de subir.
-- Os UUIDs são fixos de proposito: a coleção do Insomnia/Postman referencia esses
-- ids, então o seed precisa dar sempre o mesmo resultado.
--
-- Senha de todos os usuarios do seed: Senha@123

-- ---------------------------------------------------------------- unidades
INSERT INTO unidades (id, nome, cidade, uf, tipo_operacao, ativa)
VALUES ('10000000-0000-0000-0000-000000000001', 'Raizes Boa Viagem', 'Recife', 'PE', 'COMPLETA', TRUE),
       ('10000000-0000-0000-0000-000000000002', 'Raizes Caruaru Centro', 'Caruaru', 'PE', 'REDUZIDA', TRUE);

-- ---------------------------------------------------------------- usuarios
INSERT INTO usuarios (id, nome, email, senha_hash, perfil, unidade_id, data_nascimento)
VALUES ('20000000-0000-0000-0000-000000000001', 'Francisca Matriz', 'admin@raizes.com.br',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'ADMIN', NULL, NULL),
       ('20000000-0000-0000-0000-000000000002', 'Gerente Boa Viagem', 'gerente.recife@raizes.com.br',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'GERENTE',
        '10000000-0000-0000-0000-000000000001', NULL),
       ('20000000-0000-0000-0000-000000000003', 'Atendente Boa Viagem', 'atendente.recife@raizes.com.br',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'ATENDENTE',
        '10000000-0000-0000-0000-000000000001', NULL),
       ('20000000-0000-0000-0000-000000000004', 'Cozinha Boa Viagem', 'cozinha.recife@raizes.com.br',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'COZINHA',
        '10000000-0000-0000-0000-000000000001', NULL),
       ('20000000-0000-0000-0000-000000000005', 'Maria Cliente', 'cliente@exemplo.com',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'CLIENTE', NULL, '1995-06-24'),
       ('20000000-0000-0000-0000-000000000006', 'Gerente Caruaru', 'gerente.caruaru@raizes.com.br',
        '$2y$10$gYtGMjc1o1K3rWczYvU7PuOL2RfGtaVBY7WvsvsRARHURpe8X0KIS', 'GERENTE',
        '10000000-0000-0000-0000-000000000002', NULL);

-- ---------------------------------------------------------------- produtos
INSERT INTO produtos (id, nome, descricao, categoria, preco_base, sazonal)
VALUES ('30000000-0000-0000-0000-000000000001', 'Tapioca de queijo coalho',
        'Tapioca recheada com queijo coalho artesanal', 'TAPIOCA', 12.90, FALSE),
       ('30000000-0000-0000-0000-000000000002', 'Tapioca de carne de sol',
        'Tapioca com carne de sol desfiada e manteiga de garrafa', 'TAPIOCA', 18.50, FALSE),
       ('30000000-0000-0000-0000-000000000003', 'Cuscuz recheado com frango',
        'Cuscuz nordestino recheado com frango cremoso', 'CUSCUZ', 16.00, FALSE),
       ('30000000-0000-0000-0000-000000000004', 'Cuscuz com manteiga de garrafa',
        'O classico da casa', 'CUSCUZ', 10.00, FALSE),
       ('30000000-0000-0000-0000-000000000005', 'Bolo de macaxeira',
        'Fatia de bolo de macaxeira com coco', 'BOLO', 8.50, FALSE),
       ('30000000-0000-0000-0000-000000000006', 'Bolo de rolo',
        'Bolo de rolo pernambucano', 'BOLO', 9.90, FALSE),
       ('30000000-0000-0000-0000-000000000007', 'Suco de caja', 'Suco natural de caja 400ml',
        'BEBIDA', 7.50, FALSE),
       ('30000000-0000-0000-0000-000000000008', 'Suco de umbu', 'Suco natural de umbu 400ml',
        'BEBIDA', 7.50, FALSE),
       ('30000000-0000-0000-0000-000000000009', 'Cafe coado', 'Cafe passado na hora',
        'BEBIDA', 5.00, FALSE),
       ('30000000-0000-0000-0000-000000000010', 'Canjica junina', 'Canjica cremosa, so no periodo junino',
        'SOBREMESA', 11.00, TRUE);

-- ------------------------------------------------- cardapio da unidade Recife
-- Unidade de operação COMPLETA: vende o cardapio inteiro.
INSERT INTO cardapio_unidade (unidade_id, produto_id, preco, disponivel)
SELECT '10000000-0000-0000-0000-000000000001', id, preco_base, TRUE
FROM produtos;

-- ----------------------------------------------- cardapio da unidade Caruaru
-- Unidade REDUZIDA: so parte do cardapio, e com preco um pouco menor.
-- E o que mostra na pratica que cardapio e por unidade, não por rede.
INSERT INTO cardapio_unidade (unidade_id, produto_id, preco, disponivel)
SELECT '10000000-0000-0000-0000-000000000002', id, ROUND(preco_base * 0.90, 2), TRUE
FROM produtos
WHERE id IN ('30000000-0000-0000-0000-000000000001',
             '30000000-0000-0000-0000-000000000003',
             '30000000-0000-0000-0000-000000000004',
             '30000000-0000-0000-0000-000000000005',
             '30000000-0000-0000-0000-000000000007',
             '30000000-0000-0000-0000-000000000009');

-- ---------------------------------------------------------------- estoque
-- Todo produto que esta no cardapio de uma unidade ganha linha de estoque.
INSERT INTO estoque (unidade_id, produto_id, saldo_atual, saldo_minimo)
SELECT unidade_id, produto_id, 50, 10
FROM cardapio_unidade;

-- Um item proposital com saldo baixo, para dar para testar o erro de estoque
-- insuficiente (409) sem precisar zerar nada na mão antes.
UPDATE estoque
SET saldo_atual = 2
WHERE unidade_id = '10000000-0000-0000-0000-000000000001'
  AND produto_id = '30000000-0000-0000-0000-000000000006';

-- ------------------------------------------------------ fidelidade e LGPD
INSERT INTO contas_fidelidade (id, cliente_id, saldo_pontos, ativa)
VALUES ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000005', 0, TRUE);

INSERT INTO consentimentos (usuario_id, finalidade, versao_documento, ip)
VALUES ('20000000-0000-0000-0000-000000000005', 'FIDELIDADE', '1.0', '127.0.0.1');
