package com.geanbrandao.raizes.api.domain

/**
 * Situação do pedido e as transições permitidas entre elas.
 *
 * A maquina de estados mora aqui e não espalhada pelos services. Quem quiser mudar
 * o status pergunta antes com [podeIrPara], e se a resposta for falsa o service
 * devolve conflito. Isso evita coisa como pedido voltar de ENTREGUE para EM_PREPARO
 * ou ser cancelado depois de pronto.
 *
 * Caminho feliz:
 * ```
 * AGUARDANDO_PAGAMENTO -> PAGO -> EM_PREPARO -> PRONTO -> ENTREGUE
 * ```
 */
enum class StatusPedido {

    /** Pedido criado, esperando o cliente pagar. E aqui que todo pedido nasce. */
    AGUARDANDO_PAGAMENTO,

    /** Gateway aprovou o pagamento. Libera a cozinha e credita os pontos. */
    PAGO,

    /** Gateway recusou. O cliente pode tentar pagar de novo. */
    PAGAMENTO_RECUSADO,

    /** Cozinha pegou o pedido. */
    EM_PREPARO,

    /** Pedido pronto esperando o cliente. Daqui para frente não da mais para cancelar. */
    PRONTO,

    /** Cliente recebeu. Fim do fluxo. */
    ENTREGUE,

    /** Pedido cancelado. O estoque volta. */
    CANCELADO;

    /**
     * Diz se a transição deste status para [destino] e permitida.
     *
     * @param destino Status para onde se quer mover o pedido.
     * @return true se a transição faz sentido no fluxo.
     */
    fun podeIrPara(destino: StatusPedido): Boolean = destino in transicoesPermitidas()

    /**
     * Lista os status alcançaveis a partir deste.
     *
     * @return Conjunto de status validos como proximo passo.
     */
    fun transicoesPermitidas(): Set<StatusPedido> = when (this) {
        AGUARDANDO_PAGAMENTO -> setOf(PAGO, PAGAMENTO_RECUSADO, CANCELADO)
        PAGAMENTO_RECUSADO -> setOf(PAGO, CANCELADO)
        PAGO -> setOf(EM_PREPARO, CANCELADO)
        EM_PREPARO -> setOf(PRONTO, CANCELADO)
        PRONTO -> setOf(ENTREGUE)
        ENTREGUE, CANCELADO -> emptySet()
    }

    /** Status final, de onde o pedido não sai mais. */
    val ehFinal: Boolean get() = transicoesPermitidas().isEmpty()

    /** Se o cliente ainda pode cancelar o pedido neste ponto. */
    val podeCancelar: Boolean get() = CANCELADO in transicoesPermitidas()

    /**
     * Se o estoque ja foi debitado e precisa voltar caso o pedido seja cancelado.
     *
     * O estoque sai na criação do pedido, então qualquer status antes do cancelamento
     * tem estoque reservado para devolver.
     */
    val temEstoqueReservado: Boolean get() = this != CANCELADO
}
