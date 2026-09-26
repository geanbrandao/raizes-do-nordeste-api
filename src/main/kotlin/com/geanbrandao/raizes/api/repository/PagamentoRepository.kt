package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.PagamentoEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso aos pagamentos solicitados ao gateway. */
interface PagamentoRepository : JpaRepository<PagamentoEntity, UUID> {

    fun findAllByPedidoIdOrderByCriadoEmDesc(pedidoId: UUID): List<PagamentoEntity>

    /** Usado para garantir idempotencia: se ja existe, devolve o mesmo resultado. */
    fun findByChaveIdempotencia(chaveIdempotencia: String): PagamentoEntity?
}
