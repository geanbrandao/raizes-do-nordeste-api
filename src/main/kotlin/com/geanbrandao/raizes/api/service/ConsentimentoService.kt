package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.config.ContextoRequisicao
import com.geanbrandao.raizes.api.dto.ConsentimentoResponse
import com.geanbrandao.raizes.api.dto.RegistrarConsentimentoRequest
import com.geanbrandao.raizes.api.entity.ConsentimentoEntity
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.repository.ConsentimentoRepository
import com.geanbrandao.raizes.api.repository.ContaFidelidadeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Consentimento do titular para tratamento de dado pessoal.
 *
 * Duas decisões que valem explicar:
 *
 * 1. **Revogar não apaga a linha**, so preenche a data. Apagar destruiria a prova de
 *    que o tratamento foi legitimo enquanto durou, que e justamente o que a empresa
 *    precisa guardar.
 * 2. **Consentir de novo cria registro novo.** O historico fica completo: da para
 *    mostrar quando a pessoa aceitou, quando revogou e quando voltou atras.
 */
@Service
class ConsentimentoService(
    private val consentimentoRepository: ConsentimentoRepository,
    private val contaFidelidadeRepository: ContaFidelidadeRepository,
    private val auditoriaService: AuditoriaService,
    private val contexto: ContextoRequisicao,
) {
    private val logger = LoggerFactory.getLogger(ConsentimentoService::class.java)

    /**
     * Registra o aceite do titular.
     *
     * Aceitar FIDELIDADE liga a conta de pontos: sem base legal, o saldo nem chega a
     * ser creditado.
     *
     * @param usuarioId Titular.
     * @param request Finalidade e versão do documento.
     * @return Consentimento registrado.
     */
    @Transactional
    fun registrar(usuarioId: UUID, request: RegistrarConsentimentoRequest): ConsentimentoResponse {
        val finalidade = request.finalidade.uppercase()

        // Aceitar de novo o que ja vale não cria duplicata.
        consentimentoRepository
            .findByUsuarioIdAndFinalidadeAndRevogadoEmIsNull(usuarioId, finalidade)
            ?.let { return it.paraResponse() }

        val consentimento = consentimentoRepository.save(
            ConsentimentoEntity(
                usuarioId = usuarioId,
                finalidade = finalidade,
                versaoDocumento = request.versaoDocumento.trim(),
                ip = contexto.ip(),
            ),
        )

        if (finalidade == FINALIDADE_FIDELIDADE) {
            contaFidelidadeRepository.findByClienteId(usuarioId)?.let { conta ->
                conta.ativa = true
                conta.atualizadoEm = LocalDateTime.now()
                contaFidelidadeRepository.save(conta)
            }
        }

        auditoriaService.registrar(
            acao = AuditoriaService.Acoes.CONSENTIMENTO_REGISTRADO,
            entidade = AuditoriaService.Entidades.CONSENTIMENTO,
            entidadeId = consentimento.id,
            usuarioId = usuarioId,
            depois = mapOf("finalidade" to finalidade, "versao" to consentimento.versaoDocumento),
        )

        logger.info("Consentimento de {} registrado para o usuario {}", finalidade, usuarioId)
        return consentimento.paraResponse()
    }

    /**
     * Lista os consentimentos do titular, ativos e revogados.
     *
     * @param usuarioId Titular.
     * @return Historico completo.
     */
    @Transactional(readOnly = true)
    fun listar(usuarioId: UUID): List<ConsentimentoResponse> =
        consentimentoRepository.findAllByUsuarioId(usuarioId)
            .sortedByDescending { it.aceitoEm }
            .map { it.paraResponse() }

    /**
     * Revoga um consentimento.
     *
     * Revogar FIDELIDADE desliga a conta de pontos na hora: o saldo ja acumulado
     * continua la, mas nada novo e creditado enquanto não houver base legal.
     *
     * @param usuarioId Titular.
     * @param consentimentoId Consentimento a revogar.
     * @return Consentimento revogado.
     * @throws NaoEncontradoException se não existir ou não for do titular.
     */
    @Transactional
    fun revogar(usuarioId: UUID, consentimentoId: UUID): ConsentimentoResponse {
        val consentimento = consentimentoRepository.findById(consentimentoId)
            .filter { it.usuarioId == usuarioId }
            .orElseThrow {
                NaoEncontradoException(
                    error = ErrorCodes.CONSENTIMENTO_NAO_ENCONTRADO,
                    message = "Consentimento não encontrado.",
                )
            }

        if (consentimento.estaAtivo) {
            consentimento.revogadoEm = LocalDateTime.now()
            consentimentoRepository.save(consentimento)

            if (consentimento.finalidade == FINALIDADE_FIDELIDADE) {
                contaFidelidadeRepository.findByClienteId(usuarioId)?.let { conta ->
                    conta.ativa = false
                    conta.atualizadoEm = LocalDateTime.now()
                    contaFidelidadeRepository.save(conta)
                }
            }

            auditoriaService.registrar(
                acao = AuditoriaService.Acoes.CONSENTIMENTO_REVOGADO,
                entidade = AuditoriaService.Entidades.CONSENTIMENTO,
                entidadeId = consentimento.id,
                usuarioId = usuarioId,
                antes = mapOf("finalidade" to consentimento.finalidade, "ativo" to true),
                depois = mapOf("finalidade" to consentimento.finalidade, "ativo" to false),
            )
            logger.info("Consentimento {} revogado", consentimentoId)
        }

        return consentimento.paraResponse()
    }

    /**
     * Diz se o titular tem consentimento ativo para uma finalidade.
     *
     * @param usuarioId Titular.
     * @param finalidade Finalidade a conferir.
     * @return true se ha consentimento valendo.
     */
    @Transactional(readOnly = true)
    fun temConsentimentoAtivo(usuarioId: UUID, finalidade: String): Boolean =
        consentimentoRepository.existsByUsuarioIdAndFinalidadeAndRevogadoEmIsNull(usuarioId, finalidade)

    private fun ConsentimentoEntity.paraResponse() = ConsentimentoResponse(
        id = id,
        finalidade = finalidade,
        versaoDocumento = versaoDocumento,
        aceitoEm = aceitoEm,
        revogadoEm = revogadoEm,
        ativo = estaAtivo,
    )

    companion object {
        const val FINALIDADE_FIDELIDADE = "FIDELIDADE"
    }
}
