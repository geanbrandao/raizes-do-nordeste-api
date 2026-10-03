package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.MetodoPagamento
import com.geanbrandao.raizes.api.domain.StatusPagamento
import com.geanbrandao.raizes.api.domain.StatusPedido
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Solicitação de pagamento de um pedido.
 *
 * O valor **não** vem aqui: quem decide quanto cobrar e o servidor, a partir do total
 * do pedido. Aceitar valor do cliente seria deixar o preço na mão de quem paga.
 */
@Schema(description = "Solicitação de pagamento")
data class SolicitarPagamentoRequest(
    @field:NotNull(message = "informe o metodo de pagamento")
    @field:Schema(example = "PIX")
    val metodo: MetodoPagamento,

    @field:Size(max = 100, message = "token muito longo")
    @field:Schema(
        description = "Token opaco do gateway. A API nunca recebe dado de cartão.\n\n" +
            "No gateway simulado o token decide o desfecho: `tok_recusa...` recusa, " +
            "`tok_timeout...` simula o gateway sem responder, e qualquer outro valor " +
            "(ou nenhum) aprova.",
        example = "tok_ok_123",
    )
    val tokenPagamento: String? = null,

    @field:Size(max = 100, message = "chave muito longa")
    @field:Schema(
        description = "Chave de idempotencia. Reenviar a mesma chave devolve o resultado " +
            "que ja existe, em vez de cobrar de novo. Opcional, mas recomendada: e o que " +
            "protege o cliente de toque duplo ou reenvio por timeout.",
        example = "pedido-9001-tentativa-1",
    )
    val chaveIdempotencia: String? = null,
)

/** Pagamento como a API devolve. */
@Schema(description = "Pagamento")
data class PagamentoResponse(
    val id: UUID,
    val pedidoId: UUID,
    val status: StatusPagamento,
    val valor: BigDecimal,
    val metodo: MetodoPagamento,
    @field:Schema(description = "Id da transação no gateway. So existe depois da resposta.")
    val idTransacaoExterna: String? = null,
    @field:Schema(example = "Pagamento aprovado.")
    val mensagem: String? = null,
    @field:Schema(description = "Situação do pedido depois deste pagamento")
    val statusPedido: StatusPedido,
    val tentativas: Int,
    val criadoEm: LocalDateTime,
)

/**
 * Retorno assincrono do gateway.
 *
 * Usado quando a cobrança ficou pendente porque o gateway não respondeu na hora. E o
 * que fecha o fluxo sem deixar o pedido preso para sempre.
 */
@Schema(description = "Callback do gateway de pagamento")
data class CallbackPagamentoRequest(
    @field:NotNull(message = "informe o pagamento")
    val pagamentoId: UUID,

    @field:NotNull(message = "informe o resultado")
    @field:Schema(description = "APROVADO ou RECUSADO", example = "APROVADO")
    val resultado: StatusPagamento,

    @field:Size(max = 100)
    val idTransacaoExterna: String? = null,

    @field:Size(max = 255)
    val mensagem: String? = null,
)
