package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.dto.UnidadeRequest
import com.geanbrandao.raizes.api.dto.UnidadeResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.service.UnidadeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas das unidades da rede.
 *
 * Caminho base: /unidades
 */
@RestController
@RequestMapping("/unidades")
@Tag(name = "Unidades", description = "Lojas da rede")
class UnidadeController(
    private val unidadeService: UnidadeService,
) {

    /**
     * Lista as unidades ativas. Rota publica: o cliente precisa escolher a loja antes de logar.
     *
     * GET /unidades?page=1&limit=10
     *
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @return Pagina de unidades ativas.
     */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Listar unidades", description = "Devolve as unidades ativas da rede.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de unidades"),
        ApiResponse(
            responseCode = "400",
            description = "Parametros de paginação invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listar(
        @Parameter(description = "Pagina, começando em 1") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "Itens por pagina, no maximo 100")
        @RequestParam(defaultValue = "10") limit: Int,
    ): PaginaResponse<UnidadeResponse> = unidadeService.listarAtivas(
        ParametrosPaginacao.de(page, limit, Sort.by("nome")),
    )

    /**
     * Detalhe de uma unidade.
     *
     * GET /unidades/{unidadeId}
     *
     * @param unidadeId Id da unidade.
     * @return Dados da unidade.
     */
    @GetMapping("/{unidadeId}")
    @SecurityRequirements
    @Operation(
        summary = "Detalhar unidade",
        description = "Dados de uma unidade pelo id. Rota publica: o cliente precisa saber onde a loja fica antes de pedir.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Unidade encontrada"),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun detalhar(@PathVariable unidadeId: UUID): UnidadeResponse =
        unidadeService.buscarPorId(unidadeId)

    /**
     * Cadastra uma unidade. So a matriz.
     *
     * POST /unidades
     *
     * @param request Dados da unidade.
     * @return Unidade criada, com 201.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cadastrar unidade", description = "Exclusivo do perfil ADMIN.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Unidade cadastrada"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun criar(@Valid @RequestBody request: UnidadeRequest): UnidadeResponse =
        unidadeService.criar(request)

    /**
     * Atualiza uma unidade. So a matriz.
     *
     * PUT /unidades/{unidadeId}
     *
     * @param unidadeId Id da unidade.
     * @param request Dados novos.
     * @return Unidade atualizada.
     */
    @PutMapping("/{unidadeId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Atualizar unidade", description = "Exclusivo do perfil ADMIN.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Unidade atualizada"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun atualizar(
        @PathVariable unidadeId: UUID,
        @Valid @RequestBody request: UnidadeRequest,
    ): UnidadeResponse = unidadeService.atualizar(unidadeId, request)
}
