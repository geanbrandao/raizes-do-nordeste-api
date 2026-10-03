package com.geanbrandao.raizes.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

/** Registro da trilha de auditoria. */
@Schema(description = "Registro de auditoria")
data class LogAuditoriaResponse(
    val id: UUID,
    @field:Schema(description = "Quem fez. Nulo quando a ação partiu do sistema.")
    val usuarioId: UUID? = null,
    @field:Schema(example = "PEDIDO_CANCELADO") val acao: String,
    @field:Schema(example = "PEDIDO") val entidade: String,
    val entidadeId: UUID? = null,
    @field:Schema(description = "Estado anterior em JSON. Nulo em criação.")
    val dadosAnteriores: String? = null,
    @field:Schema(description = "Estado novo em JSON. Nulo em exclusão.")
    val dadosNovos: String? = null,
    val ip: String? = null,
    val criadoEm: LocalDateTime,
)
