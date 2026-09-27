package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoEstoque
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

/** Saldo de um produto numa unidade. */
@Schema(description = "Saldo de estoque de um produto na unidade")
data class EstoqueResponse(
    val produtoId: UUID,
    val nome: String,
    val categoria: String,
    @field:Schema(example = "50") val saldoAtual: Int,
    @field:Schema(description = "Abaixo disso a loja deveria repor", example = "10")
    val saldoMinimo: Int,
    @field:Schema(description = "true quando o saldo esta no minimo ou abaixo dele")
    val abaixoDoMinimo: Boolean,
)

/**
 * Movimentação a registrar no estoque.
 *
 * O significado de [quantidade] muda conforme o [tipo], e isso e proposital:
 *
 * - `ENTRADA` e `SAIDA`: quanto somar ou subtrair do saldo.
 * - `AJUSTE`: qual passa a ser o saldo. E o caso da contagem de inventario, em que a
 *   loja conta a prateleira e informa o que realmente tem, sem precisar calcular a
 *   diferença na mão.
 */
@Schema(description = "Movimentação de estoque")
data class MovimentacaoEstoqueRequest(
    @field:NotNull(message = "informe o produto")
    val produtoId: UUID,

    @field:NotNull(message = "informe o tipo")
    @field:Schema(
        description = "ENTRADA soma, SAIDA subtrai, AJUSTE define o saldo absoluto",
        example = "ENTRADA",
    )
    val tipo: TipoMovimentacaoEstoque,

    @field:NotNull(message = "informe a quantidade")
    @field:Min(value = 0, message = "a quantidade não pode ser negativa")
    @field:Schema(
        description = "Em ENTRADA e SAIDA, quanto movimentar (maior que zero). " +
            "Em AJUSTE, o saldo que passa a valer, e ai zero e aceito: a contagem de " +
            "inventario pode dar zero.",
        example = "20",
    )
    val quantidade: Int,

    @field:Size(max = 255, message = "motivo muito longo")
    @field:Schema(example = "Recebimento do fornecedor")
    val motivo: String? = null,
)

/** Movimentação como a API devolve. */
@Schema(description = "Movimentação registrada")
data class MovimentacaoEstoqueResponse(
    val id: UUID,
    val produtoId: UUID,
    val nome: String,
    val tipo: TipoMovimentacaoEstoque,
    val quantidade: Int,
    @field:Schema(description = "Saldo que ficou depois desta movimentação", example = "70")
    val saldoApos: Int,
    val motivo: String? = null,
    @field:Schema(description = "Preenchido quando a movimentação veio de um pedido")
    val pedidoId: UUID? = null,
    val criadoEm: LocalDateTime,
)
