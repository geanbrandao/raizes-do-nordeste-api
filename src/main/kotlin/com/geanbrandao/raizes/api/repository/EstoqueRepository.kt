package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.EstoqueEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso ao saldo de estoque por unidade. */
interface EstoqueRepository : JpaRepository<EstoqueEntity, UUID> {

    fun findAllByUnidadeId(unidadeId: UUID, pageable: Pageable): Page<EstoqueEntity>

    fun findByUnidadeIdAndProdutoId(unidadeId: UUID, produtoId: UUID): EstoqueEntity?

    fun findAllByUnidadeIdAndProdutoIdIn(
        unidadeId: UUID,
        produtoIds: Collection<UUID>,
    ): List<EstoqueEntity>
}
