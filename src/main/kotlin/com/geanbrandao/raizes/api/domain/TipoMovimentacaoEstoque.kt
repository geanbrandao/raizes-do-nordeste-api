package com.geanbrandao.raizes.api.domain

/**
 * Tipo de movimentação no estoque de uma unidade.
 *
 * Toda mudança de saldo passa por aqui, então da para reconstruir o saldo atual
 * somando o historico se precisar conferir.
 */
enum class TipoMovimentacaoEstoque {

    /** Chegou mercadoria. Soma no saldo. */
    ENTRADA,

    /** Saiu mercadoria, normalmente por venda. Subtrai do saldo. */
    SAIDA,

    /** Correção manual de inventario. Pode somar ou subtrair. */
    AJUSTE,
}
