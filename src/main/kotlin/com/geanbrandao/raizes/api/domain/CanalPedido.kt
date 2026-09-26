package com.geanbrandao.raizes.api.domain

/**
 * Canal por onde o pedido entrou.
 *
 * E requisito obrigatorio do projeto: todo pedido nasce com um canal e da para
 * filtrar a listagem por ele, para a matriz conseguir acompanhar como cada canal
 * esta vendendo.
 */
enum class CanalPedido {

    /** Aplicativo oficial. */
    APP,

    /** Totem de auto-atendimento dentro da loja. */
    TOTEM,

    /** Atendimento humano no balcão. */
    BALCAO,

    /** Pedido para retirada rapida. */
    PICKUP,

    /** Site da rede. */
    WEB,
}
