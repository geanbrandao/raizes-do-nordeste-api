package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.UnidadeEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso as unidades da rede. */
interface UnidadeRepository : JpaRepository<UnidadeEntity, UUID> {

    /** Lista so as unidades ativas, que e o que a listagem publica mostra. */
    fun findAllByAtivaTrue(pageable: Pageable): Page<UnidadeEntity>

    fun existsByIdAndAtivaTrue(id: UUID): Boolean
}
