package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.MovimentacaoPontosEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso ao extrato de pontos. */
interface MovimentacaoPontosRepository : JpaRepository<MovimentacaoPontosEntity, UUID> {

    fun findAllByContaIdOrderByCriadoEmDesc(contaId: UUID, pageable: Pageable): Page<MovimentacaoPontosEntity>

    /** Evita creditar ponto duas vezes pelo mesmo pedido. */
    fun existsByPedidoId(pedidoId: UUID): Boolean
}
