package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoEstoque
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Registro de uma mudança no saldo de estoque.
 *
 * A tabela e so de inserção: nada aqui e editado ou apagado depois. Guardar
 * [saldoApos] em cada linha deixa o historico auditavel sem precisar recalcular
 * tudo do começo.
 */
@Entity
@Table(name = "movimentacoes_estoque")
class MovimentacaoEstoqueEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "estoque_id", nullable = false, updatable = false)
    val estoqueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    val tipo: TipoMovimentacaoEstoque,

    /** Sempre positiva. Quem diz se soma ou subtrai e o [tipo]. */
    @Column(name = "quantidade", nullable = false)
    val quantidade: Int,

    /** Saldo que ficou depois desta movimentação. */
    @Column(name = "saldo_apos", nullable = false)
    val saldoApos: Int,

    @Column(name = "motivo", length = 255)
    val motivo: String? = null,

    /** Preenchido quando a movimentação veio de um pedido. */
    @Column(name = "pedido_id")
    val pedidoId: UUID? = null,

    /** Quem fez. Nulo quando foi o proprio sistema. */
    @Column(name = "usuario_id")
    val usuarioId: UUID? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),
)
