package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.MovimentacaoEstoqueEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso ao historico de movimentação de estoque. */
interface MovimentacaoEstoqueRepository : JpaRepository<MovimentacaoEstoqueEntity, UUID> {

    fun findAllByEstoqueIdOrderByCriadoEmDesc(estoqueId: UUID, pageable: Pageable): Page<MovimentacaoEstoqueEntity>

    fun findAllByPedidoId(pedidoId: UUID): List<MovimentacaoEstoqueEntity>
}
