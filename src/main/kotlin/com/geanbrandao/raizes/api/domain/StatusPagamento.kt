package com.geanbrandao.raizes.api.domain

/**
 * Situação de uma tentativa de pagamento no gateway externo simulado.
 *
 * O pagamento fica numa tabela separada do pedido de proposito: o sistema da rede
 * so pede o pagamento e guarda o que voltou, quem processa de verdade e outro
 * servico.
 */
enum class StatusPagamento {

    /** Pedido de pagamento enviado, ainda sem resposta do gateway. */
    PENDENTE,

    /** Gateway aprovou. */
    APROVADO,

    /** Gateway recusou. */
    RECUSADO,

    /** Valor devolvido depois de aprovado. */
    ESTORNADO,
}
