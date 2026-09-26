package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.StatusPedido
import com.geanbrandao.raizes.api.entity.PedidoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Acesso aos pedidos. */
interface PedidoRepository : JpaRepository<PedidoEntity, UUID> {

    /**
     * Listagem com filtros opcionais.
     *
     * Os tres filtros são opcionais e se combinam: passando null o filtro
     * simplesmente não entra na clausula. Resolve numa consulta so o filtro por
     * canal exigido no roteiro e a regra de visibilidade (operador ve a unidade
     * dele, cliente ve os pedidos dele).
     *
     * @param unidadeId Filtra por unidade, ou null para todas.
     * @param clienteId Filtra por cliente, ou null para todos.
     * @param canalPedido Filtra por canal de origem, ou null para todos.
     * @param status Filtra por situação, ou null para todas.
     * @param pageable Pagina e tamanho da pagina.
     * @return Pagina de pedidos que casam com os filtros.
     */
    @Query(
        """
        SELECT p FROM PedidoEntity p
        WHERE (:unidadeId IS NULL OR p.unidadeId = :unidadeId)
          AND (:clienteId IS NULL OR p.clienteId = :clienteId)
          AND (:canalPedido IS NULL OR p.canalPedido = :canalPedido)
          AND (:status IS NULL OR p.status = :status)
        ORDER BY p.criadoEm DESC
        """
    )
    fun buscarComFiltros(
        @Param("unidadeId") unidadeId: UUID?,
        @Param("clienteId") clienteId: UUID?,
        @Param("canalPedido") canalPedido: CanalPedido?,
        @Param("status") status: StatusPedido?,
        pageable: Pageable,
    ): Page<PedidoEntity>

    fun countByCanalPedido(canalPedido: CanalPedido): Long
}
