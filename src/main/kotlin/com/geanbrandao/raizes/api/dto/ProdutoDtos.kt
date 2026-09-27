package com.geanbrandao.raizes.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

/** Cadastro ou atualização de um produto do catalogo da rede. */
@Schema(description = "Dados de um produto")
data class ProdutoRequest(
    @field:NotBlank(message = "informe o nome")
    @field:Size(min = 2, max = 120, message = "o nome deve ter entre 2 e 120 caracteres")
    @field:Schema(example = "Tapioca de queijo coalho")
    val nome: String,

    @field:Size(max = 500, message = "descrição muito longa")
    val descricao: String? = null,

    @field:NotBlank(message = "informe a categoria")
    @field:Size(max = 60, message = "categoria muito longa")
    @field:Schema(example = "TAPIOCA")
    val categoria: String,

    @field:NotNull(message = "informe o preço base")
    @field:DecimalMin(value = "0.0", message = "o preço não pode ser negativo")
    @field:Digits(integer = 8, fraction = 2, message = "o preço aceita no maximo 2 casas decimais")
    @field:Schema(
        description = "Preço sugerido pela matriz. Cada unidade pode praticar outro no cardapio.",
        example = "12.90",
    )
    val precoBase: BigDecimal,

    @field:Schema(description = "Produto vendido so em epoca especifica, tipo o periodo junino")
    val sazonal: Boolean = false,
)

/** Produto como a API devolve. */
@Schema(description = "Produto do catalogo da rede")
data class ProdutoResponse(
    val id: UUID,
    val nome: String,
    val descricao: String? = null,
    val categoria: String,
    val precoBase: BigDecimal,
    val sazonal: Boolean,
    val ativo: Boolean,
)
