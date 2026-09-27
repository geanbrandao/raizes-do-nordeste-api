-- Contagem de inventario que da zero e um resultado legitimo: a loja contou a
-- prateleira e não tinha nada. A restrição original exigia quantidade > 0 para
-- qualquer movimentação, o que tornava esse caso impossivel de registrar.
--
-- Agora a regra e por tipo: ENTRADA e SAIDA continuam exigindo quantidade maior que
-- zero, porque movimentar nada não e movimentação e so geraria linha de lixo no
-- historico. AJUSTE aceita zero, ja que ali a quantidade e o saldo que passa a valer.
ALTER TABLE movimentacoes_estoque
    DROP CONSTRAINT chk_mov_estoque_quantidade;

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT chk_mov_estoque_quantidade
        CHECK (quantidade >= 0 AND (quantidade > 0 OR tipo = 'AJUSTE'));
