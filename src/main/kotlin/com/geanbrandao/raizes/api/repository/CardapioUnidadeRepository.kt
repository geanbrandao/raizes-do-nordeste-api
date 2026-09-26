package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.CardapioUnidadeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso ao cardapio de cada unidade. */
interface CardapioUnidadeRepository : JpaRepository<CardapioUnidadeEntity, UUID> {

    fun findAllByUnidadeId(unidadeId: UUID): List<CardapioUnidadeEntity>

    /** So o que a unidade esta vendendo agora, que e o que o cliente ve. */
    fun findAllByUnidadeIdAndDisponivelTrue(unidadeId: UUID): List<CardapioUnidadeEntity>

    /** Usado na criação do pedido para saber o preco e se o item esta no ar. */
    fun findByUnidadeIdAndProdutoId(unidadeId: UUID, produtoId: UUID): CardapioUnidadeEntity?

    fun findAllByUnidadeIdAndProdutoIdIn(
        unidadeId: UUID,
        produtoIds: Collection<UUID>,
    ): List<CardapioUnidadeEntity>
}
