package com.geanbrandao.raizes.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

/**
 * Item do cardapio de uma unidade.
 *
 * O [preco] e o da unidade, não o preço base do catalogo. E este valor que entra no
 * pedido.
 */
@Schema(description = "Item do cardapio de uma unidade")
data class CardapioItemResponse(
    val produtoId: UUID,
    val nome: String,
    val descricao: String? = null,
    val categoria: String,
    @field:Schema(description = "Preço praticado nesta unidade", example = "12.90")
    val preco: BigDecimal,
    val sazonal: Boolean,
    @field:Schema(description = "Se o item esta sendo vendido agora nesta unidade")
    val disponivel: Boolean,
)

/**
 * Ajuste de um item do cardapio de uma unidade.
 *
 * Serve tanto para incluir um produto no cardapio da loja quanto para mudar o preço
 * ou tirar do ar temporariamente.
 */
@Schema(description = "Preço e disponibilidade de um produto numa unidade")
data class CardapioItemRequest(
    @field:NotNull(message = "informe o preço")
    @field:DecimalMin(value = "0.0", message = "o preço não pode ser negativo")
    @field:Digits(integer = 8, fraction = 2, message = "o preço aceita no maximo 2 casas decimais")
    @field:Schema(example = "12.90")
    val preco: BigDecimal,

    @field:Schema(description = "false tira o item do cardapio sem apagar o cadastro")
    val disponivel: Boolean = true,
)
