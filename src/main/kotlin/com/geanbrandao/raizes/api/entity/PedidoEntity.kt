package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.StatusPedido
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Pedido feito numa unidade.
 *
 * O [canalPedido] e obrigatorio e nunca muda depois de criado: e por ele que a
 * matriz consegue comparar quanto cada canal vende.
 *
 * [clienteId] pode ser nulo porque pedido de balcão nem sempre tem cliente
 * identificado. Nesse caso simplesmente não acumula ponto de fidelidade.
 *
 * Os valores ficam gravados na linha em vez de serem recalculados na hora de ler,
 * senão um reajuste de preco no cardapio mudaria o valor de pedido antigo.
 */
@Entity
@Table(name = "pedidos")
class PedidoEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "unidade_id", nullable = false, updatable = false)
    val unidadeId: UUID,

    /** Nulo em pedido de balcão sem cliente identificado. */
    @Column(name = "cliente_id", updatable = false)
    val clienteId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_pedido", nullable = false, updatable = false, length = 20)
    val canalPedido: CanalPedido,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: StatusPedido = StatusPedido.AGUARDANDO_PAGAMENTO,

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    var subtotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "desconto", nullable = false, precision = 10, scale = 2)
    var desconto: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    var total: BigDecimal = BigDecimal.ZERO,

    /**
     * Itens do pedido.
     *
     * Cascade ALL porque item não existe sozinho, so faz sentido dentro do pedido.
     * orphanRemoval liga o ciclo de vida dos dois.
     *
     * O `nullable = false` no JoinColumn não e enfeite. Sem ele, o Hibernate insere a
     * linha do item com pedido_id nulo e so depois roda um UPDATE para preencher a
     * chave — o que estoura na hora, porque a coluna e NOT NULL no banco. Declarando
     * que a chave não aceita nulo, ele passa a incluir pedido_id no proprio INSERT.
     */
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id", nullable = false)
    val itens: MutableList<ItemPedidoEntity> = mutableListOf(),

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Recalcula subtotal e total a partir dos itens.
     *
     * Roda sempre no servidor. O que o cliente manda no request e so produto e
     * quantidade; preco e total quem decide e a API.
     */
    fun recalcularTotais() {
        subtotal = itens.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
        total = (subtotal - desconto).coerceAtLeast(BigDecimal.ZERO)
        atualizadoEm = LocalDateTime.now()
    }
}
