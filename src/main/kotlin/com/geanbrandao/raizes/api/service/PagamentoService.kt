package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.MetodoPagamento
import com.geanbrandao.raizes.api.domain.StatusPagamento
import com.geanbrandao.raizes.api.domain.StatusPedido
import com.geanbrandao.raizes.api.dto.CallbackPagamentoRequest
import com.geanbrandao.raizes.api.dto.PagamentoResponse
import com.geanbrandao.raizes.api.dto.SolicitarPagamentoRequest
import com.geanbrandao.raizes.api.entity.PagamentoEntity
import com.geanbrandao.raizes.api.entity.PedidoEntity
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorDetail
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.exception.ValidacaoException
import com.geanbrandao.raizes.api.repository.PagamentoRepository
import com.geanbrandao.raizes.api.repository.PedidoRepository
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Regras de pagamento.
 *
 * A rede não processa cobrança: ela pede ao gateway, guarda o que voltou e move o
 * pedido conforme o desfecho. Sao tres desfechos possiveis, e os tres precisam estar
 * resolvidos:
 *
 * - **aprovado**: pedido vai para PAGO;
 * - **recusado**: pedido vai para PAGAMENTO_RECUSADO, e o cliente pode tentar de novo;
 * - **sem resposta**: o pagamento fica PENDENTE e o pedido não se mexe, esperando o
 *   callback. E o caso que mais da trabalho e o que mais aparece em produção: o
 *   gateway pode ter cobrado mesmo sem responder, então dar como recusado seria
 *   arriscar cobrar o cliente duas vezes.
 */
@Service
class PagamentoService(
    private val pagamentoRepository: PagamentoRepository,
    private val pedidoRepository: PedidoRepository,
    private val pedidoService: PedidoService,
    private val gateway: GatewayPagamento,
) {
    private val logger = LoggerFactory.getLogger(PagamentoService::class.java)

    /**
     * Solicita o pagamento de um pedido.
     *
     * O valor cobrado e sempre o total do pedido, calculado pelo servidor. Nada do que
     * o cliente manda influencia quanto ele vai pagar.
     *
     * @param pedidoId Pedido a pagar.
     * @param request Metodo, token e chave de idempotencia.
     * @param solicitante Quem esta pagando.
     * @return Pagamento registrado, com o status do pedido depois dele.
     * @throws NaoEncontradoException se o pedido não existir ou não for visivel.
     * @throws ConflitoException se o pedido ja estiver pago, ou não aceitar mais pagamento.
     */
    @Transactional
    fun solicitar(
        pedidoId: UUID,
        request: SolicitarPagamentoRequest,
        solicitante: UsuarioAutenticado,
    ): PagamentoResponse {
        val pedido = pedidoService.buscarEntidadeVisivel(pedidoId, solicitante)

        // Idempotencia: a mesma chave nunca gera cobrança nova. Protege contra toque
        // duplo no app e contra reenvio depois de um timeout de rede.
        request.chaveIdempotencia?.let { chave ->
            pagamentoRepository.findByChaveIdempotencia(chave)?.let { existente ->
                if (existente.pedidoId != pedidoId) {
                    throw ValidacaoException(
                        message = "Esta chave de idempotencia ja foi usada em outro pedido.",
                        details = listOf(ErrorDetail("chaveIdempotencia", "ja usada")),
                    )
                }
                logger.info("Chave de idempotencia reaproveitada; devolvendo pagamento {}", existente.id)
                return existente.paraResponse(pedido.status)
            }
        }

        exigirPedidoPagavel(pedido)

        val pagamento = pagamentoRepository.save(
            PagamentoEntity(
                pedidoId = pedido.id,
                status = StatusPagamento.PENDENTE,
                valor = pedido.total,
                metodo = request.metodo.name,
                chaveIdempotencia = request.chaveIdempotencia ?: UUID.randomUUID().toString(),
                tentativas = pagamentoRepository.findAllByPedidoIdOrderByCriadoEmDesc(pedido.id).size + 1,
            ),
        )

        val resposta = gateway.solicitar(
            RequisicaoGateway(
                pedidoId = pedido.id,
                valor = pedido.total,
                metodo = request.metodo,
                tokenPagamento = request.tokenPagamento,
            ),
        )

        pagamento.idTransacaoExterna = resposta.idTransacaoExterna
        pagamento.payloadRetorno = resposta.payload
        pagamento.mensagem = resposta.mensagem
        pagamento.atualizadoEm = LocalDateTime.now()

        val statusPedido = when (resposta.resultado) {
            ResultadoGateway.APROVADO -> {
                pagamento.status = StatusPagamento.APROVADO
                pedidoService.aplicarResultadoDePagamento(pedido, aprovado = true).status
            }
            ResultadoGateway.RECUSADO -> {
                pagamento.status = StatusPagamento.RECUSADO
                pedidoService.aplicarResultadoDePagamento(pedido, aprovado = false).status
            }
            ResultadoGateway.SEM_RESPOSTA -> {
                // Fica pendente de proposito: pode ter sido cobrado do outro lado.
                pagamento.status = StatusPagamento.PENDENTE
                logger.warn("Gateway não respondeu para o pedido {}; aguardando callback", pedido.id)
                pedido.status
            }
        }

        return pagamentoRepository.save(pagamento).paraResponse(statusPedido)
    }

    /**
     * Consulta um pagamento.
     *
     * @param pagamentoId Id do pagamento.
     * @param solicitante Quem esta consultando.
     * @return Pagamento.
     * @throws NaoEncontradoException se não existir, ou se o pedido dele não for visivel.
     */
    @Transactional(readOnly = true)
    fun buscarPorId(pagamentoId: UUID, solicitante: UsuarioAutenticado): PagamentoResponse {
        val pagamento = pagamentoRepository.findById(pagamentoId)
            .orElseThrow { pagamentoNaoEncontrado() }

        // A visibilidade do pagamento e a mesma do pedido: quem não pode ver o pedido
        // não pode ver quanto foi cobrado nele.
        val pedido = pedidoService.buscarEntidadeVisivel(pagamento.pedidoId, solicitante)
        return pagamento.paraResponse(pedido.status)
    }

    /**
     * Processa o retorno assincrono do gateway.
     *
     * So age sobre pagamento que ainda esta pendente. Callback repetido para um
     * pagamento ja resolvido e ignorado em silencio, porque gateway reenvia webhook
     * quando não recebe confirmação, e reprocessar mudaria o pedido duas vezes.
     *
     * @param request Pagamento, resultado e dados da transação.
     * @return Pagamento atualizado.
     * @throws NaoEncontradoException se o pagamento não existir.
     */
    @Transactional
    fun processarCallback(request: CallbackPagamentoRequest): PagamentoResponse {
        val pagamento = pagamentoRepository.findById(request.pagamentoId)
            .orElseThrow { pagamentoNaoEncontrado() }

        val pedido = pedidoRepository.findById(pagamento.pedidoId)
            .orElseThrow { pagamentoNaoEncontrado() }

        if (pagamento.status != StatusPagamento.PENDENTE) {
            logger.info("Callback ignorado: pagamento {} ja estava {}", pagamento.id, pagamento.status)
            return pagamento.paraResponse(pedido.status)
        }

        if (request.resultado !in setOf(StatusPagamento.APROVADO, StatusPagamento.RECUSADO)) {
            throw ValidacaoException(
                message = "O callback so aceita APROVADO ou RECUSADO.",
                details = listOf(ErrorDetail("resultado", "aceitos: APROVADO, RECUSADO")),
            )
        }

        pagamento.status = request.resultado
        pagamento.idTransacaoExterna = request.idTransacaoExterna ?: pagamento.idTransacaoExterna
        pagamento.mensagem = request.mensagem ?: pagamento.mensagem
        pagamento.atualizadoEm = LocalDateTime.now()
        pagamentoRepository.save(pagamento)

        val aprovado = request.resultado == StatusPagamento.APROVADO
        val statusPedido = pedidoService.aplicarResultadoDePagamento(pedido, aprovado).status

        logger.info("Callback resolveu o pagamento {} como {}", pagamento.id, request.resultado)
        return pagamento.paraResponse(statusPedido)
    }

    /** Pedido so aceita pagamento enquanto esta esperando por um. */
    private fun exigirPedidoPagavel(pedido: PedidoEntity) {
        when (pedido.status) {
            StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGAMENTO_RECUSADO -> Unit
            StatusPedido.PAGO, StatusPedido.EM_PREPARO, StatusPedido.PRONTO, StatusPedido.ENTREGUE ->
                throw ConflitoException(
                    error = ErrorCodes.PEDIDO_JA_PAGO,
                    message = "Este pedido ja foi pago.",
                )
            StatusPedido.CANCELADO ->
                throw ConflitoException(
                    error = ErrorCodes.CONFLITO,
                    message = "Pedido cancelado não pode ser pago.",
                )
        }
    }

    private fun pagamentoNaoEncontrado() = NaoEncontradoException(
        error = ErrorCodes.PAGAMENTO_NAO_ENCONTRADO,
        message = "Pagamento não encontrado.",
    )

    private fun PagamentoEntity.paraResponse(statusPedido: StatusPedido) = PagamentoResponse(
        id = id,
        pedidoId = pedidoId,
        status = status,
        valor = valor,
        metodo = MetodoPagamento.valueOf(metodo),
        idTransacaoExterna = idTransacaoExterna,
        mensagem = mensagem,
        statusPedido = statusPedido,
        tentativas = tentativas,
        criadoEm = criadoEm,
    )
}
