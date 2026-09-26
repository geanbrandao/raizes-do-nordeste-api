package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime
import java.util.UUID

/**
 * Saldo de estoque de um produto numa unidade.
 *
 * Tem uma linha por combinação unidade + produto, garantido por unique no banco.
 *
 * O campo [versao] e controle de concorrencia otimista: em horario de pico dois
 * pedidos podem tentar baixar o mesmo item ao mesmo tempo, e sem isso os dois
 * leriam o mesmo saldo e o estoque ficaria negativo. Com a versão, o segundo
 * update falha e o service trata.
 */
@Entity
@Table(name = "estoque")
class EstoqueEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "unidade_id", nullable = false, updatable = false)
    val unidadeId: UUID,

    @Column(name = "produto_id", nullable = false, updatable = false)
    val produtoId: UUID,

    @Column(name = "saldo_atual", nullable = false)
    var saldoAtual: Int = 0,

    /** Abaixo disso a loja deveria repor. Hoje e so informativo. */
    @Column(name = "saldo_minimo", nullable = false)
    var saldoMinimo: Int = 0,

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(name = "versao", nullable = false)
    var versao: Long = 0,
) {
    /**
     * Diz se da para tirar [quantidade] do saldo sem ficar negativo.
     *
     * @param quantidade Quantidade que se quer baixar.
     * @return true se tem saldo suficiente.
     */
    fun temSaldoPara(quantidade: Int): Boolean = saldoAtual >= quantidade
}
