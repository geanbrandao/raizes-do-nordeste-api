package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Refresh token de uma sessão.
 *
 * E opaco (um UUID qualquer) e fica no banco, diferente do access token, que e JWT
 * e não e guardado. A vantagem de ter esse aqui persistido e conseguir revogar
 * sessão: basta marcar [revogado].
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "usuario_id", nullable = false, updatable = false)
    val usuarioId: UUID,

    @Column(name = "token", nullable = false, length = 255)
    val token: String,

    @Column(name = "expira_em", nullable = false)
    val expiraEm: LocalDateTime,

    @Column(name = "revogado", nullable = false)
    var revogado: Boolean = false,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),
) {
    /** Token vencido ou revogado não serve mais para renovar acesso. */
    fun estaValido(agora: LocalDateTime = LocalDateTime.now()): Boolean =
        !revogado && expiraEm.isAfter(agora)
}
