package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Item do cardapio de uma unidade.
 *
 * E a ponte entre produto e unidade, e resolve aquele detalhe do caso: nem toda loja
 * vende tudo, e o preco pode variar de uma para outra. Se não existe linha aqui, a
 * unidade simplesmente não vende aquele produto.
 */
@Entity
@Table(name = "cardapio_unidade")
class CardapioUnidadeEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "unidade_id", nullable = false, updatable = false)
    val unidadeId: UUID,

    @Column(name = "produto_id", nullable = false, updatable = false)
    val produtoId: UUID,

    /** Preco praticado nesta unidade. E este que entra no pedido. */
    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    var preco: BigDecimal,

    /** Da para tirar o item do ar sem apagar a linha, por exemplo quando acaba na loja. */
    @Column(name = "disponivel", nullable = false)
    var disponivel: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
)
