package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.StatusPagamento
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Tentativa de pagamento de um pedido no gateway externo.
 *
 * Fica em tabela propria porque a rede não processa pagamento: ela so pede e anota
 * o que voltou. Um pedido pode ter mais de um pagamento, ja que depois de uma
 * recusa o cliente pode tentar de novo.
 *
 * [chaveIdempotencia] tem unique no banco. E o que impede cobrar duas vezes quando
 * o app reenvia a mesma requisição por timeout ou toque duplo: a segunda chamada
 * esbarra na constraint e o service devolve o resultado que ja existia.
 */
@Entity
@Table(name = "pagamentos")
class PagamentoEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "pedido_id", nullable = false, updatable = false)
    val pedidoId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: StatusPagamento = StatusPagamento.PENDENTE,

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    val valor: BigDecimal,

    @Column(name = "metodo", nullable = false, length = 30)
    val metodo: String,

    /** Id que o gateway devolveu. So existe depois da resposta. */
    @Column(name = "id_transacao_externa", length = 100)
    var idTransacaoExterna: String? = null,

    /** Chave enviada pelo cliente para evitar cobrança duplicada. */
    @Column(name = "chave_idempotencia", nullable = false, updatable = false, length = 100)
    val chaveIdempotencia: String,

    /** Resposta crua do gateway, guardada para conferencia depois. */
    @Column(name = "payload_retorno", columnDefinition = "TEXT")
    var payloadRetorno: String? = null,

    /** Mensagem legivel do resultado, tipo o motivo da recusa. */
    @Column(name = "mensagem", length = 255)
    var mensagem: String? = null,

    @Column(name = "tentativas", nullable = false)
    var tentativas: Int = 1,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
)
