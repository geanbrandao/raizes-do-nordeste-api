package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.LogAuditoriaResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.repository.LogAuditoriaRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Consulta da trilha de auditoria.
 *
 * Caminho base: /auditoria
 *
 * So ADMIN. A trilha mostra o que cada pessoa fez na rede, e por isso ela propria e
 * dado sensivel: liberar para gerente daria a ele visao da operação das outras lojas.
 *
 * Note que não existe rota de escrita nem de exclusão aqui, de proposito. A trilha e
 * so de inserção, feita pelos proprios services, e sem endpoint que a altere não ha
 * como adulterar a prova pela API.
 */
@RestController
@RequestMapping("/auditoria")
@Tag(name = "Auditoria", description = "Trilha das ações sensiveis (somente ADMIN)")
class AuditoriaController(
    private val logRepository: LogAuditoriaRepository,
) {

    /**
     * Consulta a trilha, com filtros opcionais.
     *
     * GET /auditoria?entidade=PEDIDO&usuarioId=...&page=1&limit=10
     *
     * @param usuarioId Filtra por quem fez a ação.
     * @param entidade Filtra pelo tipo de registro afetado.
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @return Pagina de registros, do mais recente para o mais antigo.
     */
    @GetMapping
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Consultar trilha de auditoria",
        description = "Exclusivo do perfil ADMIN.\n\n" +
            "Ações rastreadas: criação, cancelamento e mudança de status de pedido, " +
            "movimentação de estoque, solicitação de pagamento, acúmulo e resgate de " +
            "pontos, registro e revogação de consentimento.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Registros da trilha"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun consultar(
        @Parameter(description = "Filtra por quem fez a ação")
        @RequestParam(required = false) usuarioId: UUID?,
        @Parameter(description = "PEDIDO, ESTOQUE, PAGAMENTO, FIDELIDADE ou CONSENTIMENTO")
        @RequestParam(required = false) entidade: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
    ): PaginaResponse<LogAuditoriaResponse> = PaginaResponse.de(
        logRepository.buscarComFiltros(
            usuarioId = usuarioId,
            entidade = entidade?.trim()?.uppercase(),
            pageable = ParametrosPaginacao.de(page, limit),
        ),
    ) {
        LogAuditoriaResponse(
            id = it.id,
            usuarioId = it.usuarioId,
            acao = it.acao,
            entidade = it.entidade,
            entidadeId = it.entidadeId,
            dadosAnteriores = it.dadosAnteriores,
            dadosNovos = it.dadosNovos,
            ip = it.ip,
            criadoEm = it.criadoEm,
        )
    }
}
