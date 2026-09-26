package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.ProdutoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso ao catalogo de produtos da rede. */
interface ProdutoRepository : JpaRepository<ProdutoEntity, UUID> {

    fun findAllByAtivoTrue(pageable: Pageable): Page<ProdutoEntity>

    fun findAllByCategoriaAndAtivoTrue(categoria: String, pageable: Pageable): Page<ProdutoEntity>

    fun findAllByIdIn(ids: Collection<UUID>): List<ProdutoEntity>
}
