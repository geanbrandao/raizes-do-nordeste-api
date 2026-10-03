package com.geanbrandao.raizes.api.domain

/**
 * Forma de pagamento escolhida pelo cliente.
 *
 * O sistema da rede não processa nenhuma delas: ele so informa ao gateway externo
 * qual foi a escolha e registra o que voltou.
 */
enum class MetodoPagamento {
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO,

    /** Pagamento em especie no balcão. Não passa pelo gateway. */
    DINHEIRO,
}
