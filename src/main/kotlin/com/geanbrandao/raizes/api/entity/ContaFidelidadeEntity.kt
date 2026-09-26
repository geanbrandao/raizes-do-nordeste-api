package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime
import java.util.UUID

/**
 * Conta do programa de fidelidade de um cliente.
 *
 * So existe se o cliente tiver dado consentimento. Sem consentimento ativo o saldo
 * nem chega a ser creditado, o que atende a exigencia da LGPD de tratar perfil de
 * consumo so com base legal.
 */
@Entity
@Table(name = "contas_fidelidade")
class ContaFidelidadeEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "cliente_id", nullable = false, updatable = false)
    val clienteId: UUID,

    @Column(name = "saldo_pontos", nullable = false)
    var saldoPontos: Int = 0,

    /** Vira false quando o cliente revoga o consentimento. */
    @Column(name = "ativa", nullable = false)
    var ativa: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(name = "versao", nullable = false)
    var versao: Long = 0,
) {
    /** Diz se da para resgatar [pontos] sem estourar o saldo. */
    fun temSaldoPara(pontos: Int): Boolean = saldoPontos >= pontos
}
