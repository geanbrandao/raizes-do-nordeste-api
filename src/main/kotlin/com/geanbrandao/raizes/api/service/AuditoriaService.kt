package com.geanbrandao.raizes.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.config.ContextoRequisicao
import com.geanbrandao.raizes.api.dto.LogAuditoriaResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.entity.LogAuditoriaEntity
import com.geanbrandao.raizes.api.repository.LogAuditoriaRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Trilha de auditoria das ações sensiveis.
 *
 * A franqueadora exige rastreabilidade de cancelamento, desconto e ajuste, e e isso
 * que esta tabela entrega: quem fez, o que fez, em qual registro, como estava antes e
 * como ficou.
 *
 * **O registro roda na mesma transação da ação.** Se gravar a trilha falhar, a ação
 * tambem volta atras. E deliberado: trilha que pode falhar em silencio não serve como
 * prova, e um pedido cancelado sem registro de quem cancelou e exatamente o buraco que
 * a auditoria existe para fechar.
 *
 * A tabela e so de inserção. Nenhuma rotina do sistema edita ou apaga linha aqui.
 */
@Service
class AuditoriaService(
    private val logRepository: LogAuditoriaRepository,
    private val contexto: ContextoRequisicao,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(AuditoriaService::class.java)

    /**
     * Registra uma ação na trilha.
     *
     * @param acao O que foi feito, de [Acoes].
     * @param entidade Tipo do registro afetado, de [Entidades].
     * @param entidadeId Id do registro afetado.
     * @param usuarioId Quem fez. Nulo quando foi o proprio sistema.
     * @param antes Estado anterior, serializado em JSON. Nulo em criação.
     * @param depois Estado novo, serializado em JSON. Nulo em exclusão.
     */
    @Transactional
    fun registrar(
        acao: String,
        entidade: String,
        entidadeId: UUID?,
        usuarioId: UUID?,
        antes: Map<String, Any?>? = null,
        depois: Map<String, Any?>? = null,
    ) {
        val registro = LogAuditoriaEntity(
            usuarioId = usuarioId,
            acao = acao,
            entidade = entidade,
            entidadeId = entidadeId,
            dadosAnteriores = antes?.let(::paraJson),
            dadosNovos = depois?.let(::paraJson),
            ip = contexto.ip(),
        )
        logRepository.save(registro)
        logger.debug("Auditoria: {} em {} {}", acao, entidade, entidadeId)
    }

    /**
     * Consulta a trilha, do registro mais recente para o mais antigo.
     *
     * @param usuarioId Filtra por quem fez a ação. Nulo traz todos.
     * @param entidade Tipo de registro afetado. Nulo traz todos. Aceita minuscula.
     * @param pageable Pagina e tamanho, montados pelo controller.
     * @return Pagina de registros da trilha.
     */
    @Transactional(readOnly = true)
    fun consultar(
        usuarioId: UUID?,
        entidade: String?,
        pageable: Pageable,
    ): PaginaResponse<LogAuditoriaResponse> = PaginaResponse.de(
        logRepository.buscarComFiltros(
            usuarioId = usuarioId,
            entidade = entidade?.trim()?.uppercase(),
            pageable = pageable,
        ),
    ) { it.paraResponse() }

    private fun LogAuditoriaEntity.paraResponse() = LogAuditoriaResponse(
        id = id,
        usuarioId = usuarioId,
        acao = acao,
        entidade = entidade,
        entidadeId = entidadeId,
        dadosAnteriores = dadosAnteriores,
        dadosNovos = dadosNovos,
        ip = ip,
        criadoEm = criadoEm,
    )

    private fun paraJson(dados: Map<String, Any?>): String = runCatching {
        objectMapper.writeValueAsString(dados)
    }.getOrElse {
        logger.warn("Não consegui serializar dados de auditoria: {}", it.message)
        """{"erro":"falha ao serializar"}"""
    }

    /** Ações rastreadas. Constante em vez de texto solto, para a consulta não depender de grafia. */
    object Acoes {
        const val PEDIDO_CRIADO = "PEDIDO_CRIADO"
        const val PEDIDO_CANCELADO = "PEDIDO_CANCELADO"
        const val STATUS_ALTERADO = "STATUS_ALTERADO"
        const val ESTOQUE_MOVIMENTADO = "ESTOQUE_MOVIMENTADO"
        const val PAGAMENTO_SOLICITADO = "PAGAMENTO_SOLICITADO"
        const val PONTOS_ACUMULADOS = "PONTOS_ACUMULADOS"
        const val PONTOS_RESGATADOS = "PONTOS_RESGATADOS"
        const val CONSENTIMENTO_REGISTRADO = "CONSENTIMENTO_REGISTRADO"
        const val CONSENTIMENTO_REVOGADO = "CONSENTIMENTO_REVOGADO"
    }

    /** Tipos de registro que aparecem na trilha. */
    object Entidades {
        const val PEDIDO = "PEDIDO"
        const val ESTOQUE = "ESTOQUE"
        const val PAGAMENTO = "PAGAMENTO"
        const val FIDELIDADE = "FIDELIDADE"
        const val CONSENTIMENTO = "CONSENTIMENTO"
    }
}
