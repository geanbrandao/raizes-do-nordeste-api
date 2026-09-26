package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.TipoOperacaoUnidade
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Unidade (loja) da rede.
 *
 * Quase tudo no sistema pendura em unidade: cardapio, estoque, pedidos e os
 * operadores. E o que permite a matriz olhar a rede inteira e cada loja olhar
 * so a parte dela.
 */
@Entity
@Table(name = "unidades")
class UnidadeEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "nome", nullable = false, length = 120)
    var nome: String,

    @Column(name = "cidade", nullable = false, length = 120)
    var cidade: String,

    @Column(name = "uf", nullable = false, length = 2)
    var uf: String,

    /** Define se a loja prepara o cardapio inteiro ou so parte dele. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false, length = 20)
    var tipoOperacao: TipoOperacaoUnidade,

    /** Unidade inativa não aparece na listagem publica nem aceita pedido novo. */
    @Column(name = "ativa", nullable = false)
    var ativa: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
)
