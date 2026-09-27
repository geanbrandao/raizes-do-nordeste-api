package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Codigo de verificação de e-mail de um cadastro.
 *
 * A linha continua no banco depois de usada, com [usado] em true, porque ela e a
 * prova de quando aquela conta foi confirmada. Apagar economizaria espaço e
 * destruiria a trilha.
 */
@Entity
@Table(name = "tokens_verificacao_email")
class TokenVerificacaoEmailEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "usuario_id", nullable = false, updatable = false)
    val usuarioId: UUID,

    @Column(name = "codigo", nullable = false, updatable = false, length = 6)
    val codigo: String,

    @Column(name = "expira_em", nullable = false, updatable = false)
    val expiraEm: LocalDateTime,

    @Column(name = "usado", nullable = false)
    var usado: Boolean = false,

    /** Quantas vezes erraram o codigo. Serve de freio contra força bruta. */
    @Column(name = "tentativas_falhas", nullable = false)
    var tentativasFalhas: Int = 0,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Diz se o codigo ainda pode ser usado.
     *
     * @param maxTentativas Limite de erros antes de queimar o codigo.
     * @param agora Momento de referencia.
     * @return true se não foi usado, não expirou e ainda tem tentativa sobrando.
     */
    fun estaUtilizavel(maxTentativas: Int, agora: LocalDateTime = LocalDateTime.now()): Boolean =
        !usado && expiraEm.isAfter(agora) && tentativasFalhas < maxTentativas
}
