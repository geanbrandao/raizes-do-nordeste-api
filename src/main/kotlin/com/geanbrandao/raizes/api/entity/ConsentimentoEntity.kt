package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Consentimento do titular para uso de dado pessoal, na linha da LGPD.
 *
 * Duas decisões importantes aqui:
 *
 * 1. E versionado. Se a finalidade ou o texto mudar, sobe a versão e o cliente
 *    precisa aceitar de novo. Consentimento dado para uma coisa não vale para outra.
 * 2. Revogar não apaga a linha, so preenche [revogadoEm]. Se apagasse, a empresa
 *    perderia a prova de que o tratamento foi legitimo na epoca em que aconteceu.
 */
@Entity
@Table(name = "consentimentos")
class ConsentimentoEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "usuario_id", nullable = false, updatable = false)
    val usuarioId: UUID,

    /** Para que o dado vai ser usado: FIDELIDADE, MARKETING ou PERFILAMENTO. */
    @Column(name = "finalidade", nullable = false, updatable = false, length = 60)
    val finalidade: String,

    /** Versão do documento que a pessoa aceitou. */
    @Column(name = "versao_documento", nullable = false, updatable = false, length = 20)
    val versaoDocumento: String,

    @Column(name = "aceito_em", nullable = false, updatable = false)
    val aceitoEm: LocalDateTime = LocalDateTime.now(),

    /** Preenchido na revogação. Enquanto nulo, o consentimento esta valendo. */
    @Column(name = "revogado_em")
    var revogadoEm: LocalDateTime? = null,

    /** Guardado como evidencia de onde veio o aceite. */
    @Column(name = "ip", length = 45)
    val ip: String? = null,
) {
    /** Consentimento ainda valendo. */
    val estaAtivo: Boolean get() = revogadoEm == null
}
