package com.geanbrandao.raizes.api.domain

/**
 * Tipo de movimentação na conta de fidelidade do cliente.
 */
enum class TipoMovimentacaoPontos {

    /** Cliente ganhou pontos por um pedido pago. */
    ACUMULO,

    /** Cliente gastou pontos. */
    RESGATE,
}
