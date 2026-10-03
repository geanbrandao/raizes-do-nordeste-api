package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoPontos
import com.geanbrandao.raizes.api.dto.MovimentacaoPontosResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ResgatarPontosRequest
import com.geanbrandao.raizes.api.dto.SaldoFidelidadeResponse
import com.geanbrandao.raizes.api.entity.ContaFidelidadeEntity
import com.geanbrandao.raizes.api.entity.MovimentacaoPontosEntity
import com.geanbrandao.raizes.api.entity.PedidoEntity
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorDetail
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.repository.ContaFidelidadeRepository
import com.geanbrandao.raizes.api.repository.MovimentacaoPontosRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.UUID

/**
 * Programa de fidelidade.
 *
 * O ponto central e que **nada e creditado sem base legal**. Pontuar depende de saber
 * quem e o cliente e do que ele consome, e isso e tratamento de dado pessoal. Sem
 * consentimento ativo de FIDELIDADE, a conta fica inativa e o pedido pago passa sem
 * gerar ponto. Não e detalhe de implementação: e a diferença entre tratar dado com
 * base legal e sem.
 */
@Service
class FidelidadeService(
    private val contaRepository: ContaFidelidadeRepository,
    private val movimentacaoRepository: MovimentacaoPontosRepository,
    private val consentimentoService: ConsentimentoService,
    private val auditoriaService: AuditoriaService,
    @Value("\${app.fidelidade.reais-por-ponto:1}") private val reaisPorPonto: BigDecimal,
) {
    private val logger = LoggerFactory.getLogger(FidelidadeService::class.java)

    /**
     * Credita pontos por um pedido pago.
     *
     * Silenciosa de proposito: se o pedido não tem cliente identificado, ou se falta
     * consentimento, simplesmente não pontua. Estourar erro aqui faria um pagamento
     * aprovado falhar por causa do programa de fidelidade, o que seria pior para o
     * cliente do que ficar sem o ponto.
     *
     * @param pedido Pedido recem-pago.
     * @param multiplicador Multiplicador de campanha de pontos extras.
     */
    @Transactional
    fun acumularPorPedido(pedido: PedidoEntity, multiplicador: Int = 1) {
        val clienteId = pedido.clienteId ?: return

        if (!consentimentoService.temConsentimentoAtivo(
                clienteId,
                ConsentimentoService.FINALIDADE_FIDELIDADE,
            )
        ) {
            logger.debug("Pedido {} não pontua: cliente sem consentimento de fidelidade", pedido.id)
            return
        }

        // Nunca credita duas vezes pelo mesmo pedido, mesmo se o fluxo for reexecutado.
        if (movimentacaoRepository.existsByPedidoId(pedido.id)) {
            logger.debug("Pedido {} ja havia pontuado", pedido.id)
            return
        }

        val conta = contaRepository.findByClienteId(clienteId) ?: return
        if (!conta.ativa) return

        val pontos = pedido.total
            .divide(reaisPorPonto, 0, RoundingMode.DOWN)
            .toInt() * multiplicador
        if (pontos <= 0) return

        conta.saldoPontos += pontos
        conta.atualizadoEm = LocalDateTime.now()
        contaRepository.save(conta)

        movimentacaoRepository.save(
            MovimentacaoPontosEntity(
                contaId = conta.id,
                tipo = TipoMovimentacaoPontos.ACUMULO,
                pontos = pontos,
                saldoApos = conta.saldoPontos,
                pedidoId = pedido.id,
                descricao = if (multiplicador > 1) "Pontos em dobro por campanha" else "Pedido pago",
            ),
        )

        auditoriaService.registrar(
            acao = AuditoriaService.Acoes.PONTOS_ACUMULADOS,
            entidade = AuditoriaService.Entidades.FIDELIDADE,
            entidadeId = conta.id,
            usuarioId = clienteId,
            depois = mapOf("pontos" to pontos, "saldo" to conta.saldoPontos, "pedido" to pedido.id),
        )

        logger.info("Pedido {} creditou {} ponto(s) ao cliente {}", pedido.id, pontos, clienteId)
    }

    /**
     * Saldo de pontos do cliente.
     *
     * @param clienteId Dono da conta.
     * @return Saldo e se a conta esta ativa.
     */
    @Transactional(readOnly = true)
    fun consultarSaldo(clienteId: UUID): SaldoFidelidadeResponse {
        val conta = buscarConta(clienteId)
        return SaldoFidelidadeResponse(saldoPontos = conta.saldoPontos, ativa = conta.ativa)
    }

    /**
     * Extrato de pontos do cliente.
     *
     * @param clienteId Dono da conta.
     * @param pageable Pagina e tamanho da pagina.
     * @return Pagina de lançamentos, do mais recente para o mais antigo.
     */
    @Transactional(readOnly = true)
    fun consultarExtrato(clienteId: UUID, pageable: Pageable): PaginaResponse<MovimentacaoPontosResponse> {
        val conta = buscarConta(clienteId)
        return PaginaResponse.de(
            movimentacaoRepository.findAllByContaIdOrderByCriadoEmDesc(conta.id, pageable),
        ) {
            MovimentacaoPontosResponse(
                id = it.id,
                tipo = it.tipo,
                pontos = it.pontos,
                saldoApos = it.saldoApos,
                pedidoId = it.pedidoId,
                descricao = it.descricao,
                criadoEm = it.criadoEm,
            )
        }
    }

    /**
     * Resgata pontos do saldo.
     *
     * @param clienteId Dono da conta.
     * @param request Quantos pontos e para quê.
     * @return Saldo depois do resgate.
     * @throws ConflitoException se o saldo não cobrir o resgate.
     */
    @Transactional
    fun resgatar(clienteId: UUID, request: ResgatarPontosRequest): SaldoFidelidadeResponse {
        val conta = buscarConta(clienteId)

        if (!conta.temSaldoPara(request.pontos)) {
            throw ConflitoException(
                error = ErrorCodes.PONTOS_INSUFICIENTES,
                message = "Saldo de pontos insuficiente para este resgate.",
                details = listOf(
                    ErrorDetail("pontos", "pedido: ${request.pontos}, disponivel: ${conta.saldoPontos}"),
                ),
            )
        }

        conta.saldoPontos -= request.pontos
        conta.atualizadoEm = LocalDateTime.now()
        contaRepository.save(conta)

        movimentacaoRepository.save(
            MovimentacaoPontosEntity(
                contaId = conta.id,
                tipo = TipoMovimentacaoPontos.RESGATE,
                pontos = request.pontos,
                saldoApos = conta.saldoPontos,
                descricao = request.descricao?.trim() ?: "Resgate de pontos",
            ),
        )

        auditoriaService.registrar(
            acao = AuditoriaService.Acoes.PONTOS_RESGATADOS,
            entidade = AuditoriaService.Entidades.FIDELIDADE,
            entidadeId = conta.id,
            usuarioId = clienteId,
            antes = mapOf("saldo" to conta.saldoPontos + request.pontos),
            depois = mapOf("saldo" to conta.saldoPontos, "resgatado" to request.pontos),
        )

        logger.info("Cliente {} resgatou {} ponto(s)", clienteId, request.pontos)
        return SaldoFidelidadeResponse(saldoPontos = conta.saldoPontos, ativa = conta.ativa)
    }

    private fun buscarConta(clienteId: UUID): ContaFidelidadeEntity =
        contaRepository.findByClienteId(clienteId)
            ?: throw NaoEncontradoException(
                error = ErrorCodes.CONTA_FIDELIDADE_NAO_ENCONTRADA,
                message = "Este usuario não tem conta no programa de fidelidade.",
            )
}
