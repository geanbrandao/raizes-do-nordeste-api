package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.CampanhaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

/** Acesso as campanhas e promoções. */
interface CampanhaRepository : JpaRepository<CampanhaEntity, UUID> {

    /**
     * Campanhas que podem ser aplicadas a um pedido.
     *
     * Traz as vigentes que sejam da rede toda ou da unidade do pedido, e que sejam
     * de qualquer canal ou justamente do canal usado. A checagem de faixa etaria e
     * de consentimento fica fora daqui, no service, porque depende do cliente.
     *
     * @param unidadeId Unidade onde o pedido esta sendo feito.
     * @param canalPedido Canal de origem do pedido, como texto do enum.
     * @param data Data de referencia para a vigencia.
     * @return Campanhas candidatas, ainda sem o filtro de perfil do cliente.
     */
    @Query(
        """
        SELECT c FROM CampanhaEntity c
        WHERE c.ativa = true
          AND c.vigenciaInicio <= :data
          AND c.vigenciaFim >= :data
          AND (c.unidadeId IS NULL OR c.unidadeId = :unidadeId)
          AND (c.canalPedido IS NULL OR c.canalPedido = :canalPedido)
        """
    )
    fun buscarCandidatas(
        @Param("unidadeId") unidadeId: UUID,
        @Param("canalPedido") canalPedido: com.geanbrandao.raizes.api.domain.CanalPedido,
        @Param("data") data: LocalDate,
    ): List<CampanhaEntity>

    fun findAllByAtivaTrue(): List<CampanhaEntity>
}
