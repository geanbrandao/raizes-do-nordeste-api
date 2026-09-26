package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoPontos
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Lançamento no extrato de pontos do cliente.
 *
 * Mesma ideia da movimentação de estoque: so insere, nunca edita, e guarda o saldo
 * que ficou depois para o extrato ser conferivel linha a linha.
 */
@Entity
@Table(name = "movimentacoes_pontos")
class MovimentacaoPontosEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "conta_id", nullable = false, updatable = false)
    val contaId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    val tipo: TipoMovimentacaoPontos,

    /** Sempre positiva. Quem diz se soma ou subtrai e o [tipo]. */
    @Column(name = "pontos", nullable = false)
    val pontos: Int,

    @Column(name = "saldo_apos", nullable = false)
    val saldoApos: Int,

    /** Pedido que gerou o acumulo. Nulo em resgate. */
    @Column(name = "pedido_id")
    val pedidoId: UUID? = null,

    @Column(name = "descricao", length = 255)
    val descricao: String? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),
)
