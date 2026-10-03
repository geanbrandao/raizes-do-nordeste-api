package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoPontos
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

/** Saldo de pontos do cliente. */
@Schema(description = "Saldo do programa de fidelidade")
data class SaldoFidelidadeResponse(
    @field:Schema(example = "120") val saldoPontos: Int,
    @field:Schema(
        description = "false enquanto o cliente não der consentimento. Conta inativa " +
            "não acumula ponto.",
    )
    val ativa: Boolean,
)

/** Lançamento no extrato de pontos. */
@Schema(description = "Lançamento de pontos")
data class MovimentacaoPontosResponse(
    val id: UUID,
    val tipo: TipoMovimentacaoPontos,
    val pontos: Int,
    @field:Schema(description = "Saldo que ficou depois deste lançamento")
    val saldoApos: Int,
    val pedidoId: UUID? = null,
    val descricao: String? = null,
    val criadoEm: LocalDateTime,
)

/** Resgate de pontos. */
@Schema(description = "Resgate de pontos")
data class ResgatarPontosRequest(
    @field:NotNull(message = "informe os pontos")
    @field:Min(value = 1, message = "o resgate precisa ser de pelo menos 1 ponto")
    @field:Schema(example = "50")
    val pontos: Int,

    @field:Size(max = 255, message = "descrição muito longa")
    @field:Schema(example = "Troca por cuscuz")
    val descricao: String? = null,
)
