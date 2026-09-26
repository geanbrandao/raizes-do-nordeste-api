package com.geanbrandao.raizes.api.domain

/**
 * Tipo de beneficio que a campanha da.
 *
 * O que muda de um tipo para outro e como o campo valor da campanha e lido.
 */
enum class TipoCampanha {

    /** valor e o percentual de desconto, de 0 a 100. */
    DESCONTO_PERCENTUAL,

    /** valor e o desconto em reais direto no total. */
    DESCONTO_FIXO,

    /** valor e o multiplicador de pontos de fidelidade, tipo 2 para pontos em dobro. */
    PONTOS_EXTRAS,
}
