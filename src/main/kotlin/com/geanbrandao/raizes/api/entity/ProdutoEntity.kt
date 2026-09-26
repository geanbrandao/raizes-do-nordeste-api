package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Produto do catalogo da rede.
 *
 * Isso aqui e o catalogo da matriz. O preco que o cliente paga não vem daqui, vem
 * do cardapio da unidade: [precoBase] e so a referencia que a franqueadora sugere.
 */
@Entity
@Table(name = "produtos")
class ProdutoEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "nome", nullable = false, length = 120)
    var nome: String,

    @Column(name = "descricao", length = 500)
    var descricao: String? = null,

    @Column(name = "categoria", nullable = false, length = 60)
    var categoria: String,

    /** Preco sugerido pela matriz. A unidade pode praticar outro. */
    @Column(name = "preco_base", nullable = false, precision = 10, scale = 2)
    var precoBase: BigDecimal,

    /** Produto que so vende em epoca especifica, tipo a canjica no periodo junino. */
    @Column(name = "sazonal", nullable = false)
    var sazonal: Boolean = false,

    /** Produto inativo sai do catalogo mas continua existindo nos pedidos antigos. */
    @Column(name = "ativo", nullable = false)
    var ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
)
