-- Fecha o enum de metodo de pagamento no banco, como ja e feito nos outros campos
-- de dominio. Sem isso, a coluna aceitaria qualquer texto e o banco deixaria de
-- contar a mesma historia que o codigo.
ALTER TABLE pagamentos
    ADD CONSTRAINT chk_pagamentos_metodo
        CHECK (metodo IN ('PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'DINHEIRO'));
