package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * Linha de um pedido.
 *
 * [precoUnitario] e [nomeProduto] são copiados do cardapio no momento da criação de
 * proposito. Se a loja reajustar o preco ou renomear o produto amanhã, o pedido de
 * hoje continua mostrando o que foi realmente combinado com o cliente.
 */
@Entity
@Table(name = "itens_pedido")
class ItemPedidoEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "produto_id", nullable = false, updatable = false)
    val produtoId: UUID,

    /** Nome copiado na hora da compra, para o pedido antigo não mudar depois. */
    @Column(name = "nome_produto", nullable = false, length = 120)
    val nomeProduto: String,

    @Column(name = "quantidade", nullable = false)
    val quantidade: Int,

    /** Preco congelado no momento da compra. */
    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    val precoUnitario: BigDecimal,

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    val subtotal: BigDecimal = precoUnitario.multiply(BigDecimal(quantidade)),
)
