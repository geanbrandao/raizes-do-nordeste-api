package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.StatusPedido
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/** Um item do pedido: o que o cliente escolheu e quanto. */
@Schema(description = "Item do pedido")
data class ItemPedidoRequest(
    @field:NotNull(message = "informe o produto")
    val produtoId: UUID,

    @field:NotNull(message = "informe a quantidade")
    @field:Min(value = 1, message = "a quantidade deve ser maior que zero")
    @field:Schema(example = "2")
    val quantidade: Int,
)

/**
 * Criação de um pedido.
 *
 * Repare no que **não** está aqui: preço e total. O cliente manda produto e
 * quantidade; quanto custa é decisão do servidor, que lê o preço do cardápio da
 * unidade. Aceitar preço vindo do cliente seria deixar o desconto na mão de quem
 * paga.
 */
@Schema(description = "Novo pedido")
data class CriarPedidoRequest(
    @field:NotNull(message = "informe a unidade")
    val unidadeId: UUID,

    @field:NotNull(message = "informe o canal do pedido")
    @field:Schema(
        description = "Por onde o pedido entrou. Obrigatorio: e o que permite a matriz " +
            "acompanhar a venda por canal.",
        example = "APP",
    )
    val canalPedido: CanalPedido,

    @field:NotEmpty(message = "o pedido precisa de pelo menos um item")
    @field:Size(max = 50, message = "no maximo 50 itens por pedido")
    @field:Valid
    val itens: List<ItemPedidoRequest>,

    @field:Schema(
        description = "So usado quando um atendente registra pedido de balcão para um " +
            "cliente identificado. Cliente logado nunca precisa informar: o pedido sai " +
            "no nome dele.",
    )
    val clienteId: UUID? = null,
)

/** Item do pedido como a API devolve, com o preço congelado na compra. */
@Schema(description = "Item do pedido")
data class ItemPedidoResponse(
    val produtoId: UUID,
    val nome: String,
    val quantidade: Int,
    @field:Schema(description = "Preço praticado no momento da compra", example = "12.90")
    val precoUnitario: BigDecimal,
    val subtotal: BigDecimal,
)

/** Pedido como a API devolve. */
@Schema(description = "Pedido")
data class PedidoResponse(
    val id: UUID,
    val unidadeId: UUID,
    val clienteId: UUID? = null,
    val canalPedido: CanalPedido,
    val status: StatusPedido,
    val itens: List<ItemPedidoResponse>,
    val subtotal: BigDecimal,
    @field:Schema(description = "Desconto de campanha aplicado pelo servidor", example = "3.00")
    val desconto: BigDecimal,
    val total: BigDecimal,
    @field:Schema(description = "Campanha que gerou o desconto, quando houve")
    val campanhaAplicada: String? = null,
    @field:Schema(description = "Status para onde este pedido ainda pode ir")
    val proximosStatus: List<StatusPedido>,
    val criadoEm: LocalDateTime,
    val atualizadoEm: LocalDateTime,
)

/** Avanço de status do pedido. */
@Schema(description = "Novo status do pedido")
data class AtualizarStatusRequest(
    @field:NotNull(message = "informe o status")
    @field:Schema(example = "EM_PREPARO")
    val status: StatusPedido,
)

/** Cancelamento de pedido. */
@Schema(description = "Cancelamento")
data class CancelarPedidoRequest(
    @field:Size(max = 255, message = "motivo muito longo")
    @field:Schema(example = "Cliente desistiu")
    val motivo: String? = null,
)
