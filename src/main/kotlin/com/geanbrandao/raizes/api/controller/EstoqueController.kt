package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.EstoqueResponse
import com.geanbrandao.raizes.api.dto.MovimentacaoEstoqueRequest
import com.geanbrandao.raizes.api.dto.MovimentacaoEstoqueResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.EstoqueService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas de estoque de uma unidade.
 *
 * Caminho base: /unidades/{unidadeId}/estoque
 *
 * Tudo aqui exige autenticação e e restrito a quem trabalha naquela loja. Cliente não
 * tem acesso nenhum: saldo de estoque e informação da operação, não da vitrine.
 */
@RestController
@RequestMapping("/unidades/{unidadeId}/estoque")
@Tag(name = "Estoque", description = "Saldo e movimentação por unidade")
class EstoqueController(
    private val estoqueService: EstoqueService,
) {

    /**
     * Lista o saldo de todos os produtos da unidade.
     *
     * GET /unidades/{unidadeId}/estoque
     *
     * @param unidadeId Unidade consultada.
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de saldos.
     */
    @GetMapping
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Consultar saldo da unidade",
        description = "Perfis ATENDENTE, COZINHA, GERENTE e ADMIN. Operador so enxerga a " +
            "propria unidade; admin enxerga qualquer uma.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Saldos da unidade"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou operador de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listarSaldos(
        @PathVariable unidadeId: UUID,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PaginaResponse<EstoqueResponse> = estoqueService.listarSaldos(
        unidadeId,
        ParametrosPaginacao.de(page, limit),
        solicitante,
    )

    /**
     * Registra uma movimentação de estoque.
     *
     * POST /unidades/{unidadeId}/estoque/movimentacoes
     *
     * @param unidadeId Unidade a movimentar.
     * @param request Produto, tipo, quantidade e motivo.
     * @param solicitante Quem esta registrando.
     * @return Movimentação registrada, com 201.
     */
    @PostMapping("/movimentacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Movimentar estoque",
        description = "Perfis ATENDENTE, GERENTE e ADMIN.\n\n" +
            "ENTRADA soma ao saldo, SAIDA subtrai e AJUSTE define o saldo absoluto " +
            "(caso da contagem de inventario).\n\n" +
            "Se o produto ainda não tiver saldo nesta unidade, a linha e criada na " +
            "primeira entrada.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Movimentação registrada"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou operador de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade ou produto não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Saida maior que o saldo disponivel",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun movimentar(
        @PathVariable unidadeId: UUID,
        @Valid @RequestBody request: MovimentacaoEstoqueRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): MovimentacaoEstoqueResponse = estoqueService.movimentar(unidadeId, request, solicitante)

    /**
     * Histórico de movimentações de um produto na unidade.
     *
     * GET /unidades/{unidadeId}/estoque/{produtoId}/movimentacoes
     *
     * @param unidadeId Unidade consultada.
     * @param produtoId Produto consultado.
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de movimentações, da mais recente para a mais antiga.
     */
    @GetMapping("/{produtoId}/movimentacoes")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Histórico de movimentações do produto",
        description = "Perfis GERENTE e ADMIN. Cada linha guarda o saldo que ficou " +
            "depois dela, então o histórico e conferivel sem recalcular nada.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Histórico do produto"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou operador de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Produto sem estoque nesta unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listarMovimentacoes(
        @PathVariable unidadeId: UUID,
        @PathVariable produtoId: UUID,
        @Parameter(description = "Pagina, começando em 1") @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PaginaResponse<MovimentacaoEstoqueResponse> = estoqueService.listarMovimentacoes(
        unidadeId,
        produtoId,
        ParametrosPaginacao.de(page, limit),
        solicitante,
    )
}
