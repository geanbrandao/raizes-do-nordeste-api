package com.geanbrandao.raizes.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Registro da trilha de auditoria.
 *
 * Guarda quem fez o que, em qual registro e como estava antes e depois. E usado nas
 * ações sensiveis que a franqueadora precisa conseguir rastrear: criação e
 * cancelamento de pedido, mudança de status, movimentação de estoque e resgate de
 * ponto.
 *
 * A tabela e so de inserção. Nenhuma rotina do sistema faz update ou delete aqui,
 * senão a trilha deixaria de servir como prova.
 */
@Entity
@Table(name = "logs_auditoria")
class LogAuditoriaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** Quem fez. Nulo quando a ação partiu do proprio sistema. */
    @Column(name = "usuario_id", updatable = false)
    val usuarioId: UUID? = null,

    /** O que foi feito, tipo PEDIDO_CRIADO ou STATUS_ALTERADO. */
    @Column(name = "acao", nullable = false, updatable = false, length = 60)
    val acao: String,

    /** Nome da entidade afetada, tipo PEDIDO ou ESTOQUE. */
    @Column(name = "entidade", nullable = false, updatable = false, length = 60)
    val entidade: String,

    @Column(name = "entidade_id", updatable = false)
    val entidadeId: UUID? = null,

    /** Estado anterior em JSON. Nulo em criação. */
    @Column(name = "dados_anteriores", updatable = false, columnDefinition = "TEXT")
    val dadosAnteriores: String? = null,

    /** Estado novo em JSON. Nulo em exclusão. */
    @Column(name = "dados_novos", updatable = false, columnDefinition = "TEXT")
    val dadosNovos: String? = null,

    @Column(name = "ip", updatable = false, length = 45)
    val ip: String? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),
)
