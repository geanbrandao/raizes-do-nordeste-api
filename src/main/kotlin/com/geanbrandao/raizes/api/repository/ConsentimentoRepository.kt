package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.ConsentimentoEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso aos consentimentos de LGPD. */
interface ConsentimentoRepository : JpaRepository<ConsentimentoEntity, UUID> {

    fun findAllByUsuarioId(usuarioId: UUID): List<ConsentimentoEntity>

    /** Consentimento ainda valendo para uma finalidade. Revogado tem revogadoEm preenchido. */
    fun findByUsuarioIdAndFinalidadeAndRevogadoEmIsNull(
        usuarioId: UUID,
        finalidade: String,
    ): ConsentimentoEntity?

    fun existsByUsuarioIdAndFinalidadeAndRevogadoEmIsNull(usuarioId: UUID, finalidade: String): Boolean
}
