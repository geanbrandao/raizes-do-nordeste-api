package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.MovimentacaoPontosResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.dto.ResgatarPontosRequest
import com.geanbrandao.raizes.api.dto.SaldoFidelidadeResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.FidelidadeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Rotas do programa de fidelidade.
 *
 * Caminho base: /fidelidade
 *
 * Todas operam sobre a conta de quem esta autenticado. Ninguem consulta nem resgata
 * ponto de outra pessoa: o id vem do token, nunca da URL.
 */
@RestController
@RequestMapping("/fidelidade")
@Tag(name = "Fidelidade", description = "Pontos, extrato e resgate")
class FidelidadeController(
    private val fidelidadeService: FidelidadeService,
) {

    /**
     * Saldo de pontos do cliente autenticado.
     *
     * GET /fidelidade/saldo
     *
     * @param cliente Cliente extraido do token.
     * @return Saldo e situação da conta.
     */
    @GetMapping("/saldo")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Consultar saldo",
        description = "Conta inativa significa que falta consentimento de FIDELIDADE: " +
            "o saldo continua visivel, mas nada novo e creditado.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Saldo do cliente"),
        ApiResponse(
            responseCode = "404",
            description = "Usuario sem conta no programa",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun saldo(
        @AuthenticationPrincipal cliente: UsuarioAutenticado,
    ): SaldoFidelidadeResponse = fidelidadeService.consultarSaldo(cliente.id)

    /**
     * Extrato de pontos do cliente autenticado.
     *
     * GET /fidelidade/extrato
     *
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @param cliente Cliente extraido do token.
     * @return Pagina de lançamentos.
     */
    @GetMapping("/extrato")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Consultar extrato",
        description = "Cada lançamento guarda o saldo que ficou depois dele, então o " +
            "extrato e conferivel linha a linha.",
    )
    @ApiResponses(ApiResponse(responseCode = "200", description = "Extrato do cliente"))
    fun extrato(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @AuthenticationPrincipal cliente: UsuarioAutenticado,
    ): PaginaResponse<MovimentacaoPontosResponse> =
        fidelidadeService.consultarExtrato(cliente.id, ParametrosPaginacao.de(page, limit))

    /**
     * Resgata pontos do saldo.
     *
     * POST /fidelidade/resgates
     *
     * @param request Quantos pontos e para quê.
     * @param cliente Cliente extraido do token.
     * @return Saldo depois do resgate.
     */
    @PostMapping("/resgates")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(summary = "Resgatar pontos")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pontos resgatados"),
        ApiResponse(
            responseCode = "409",
            description = "Saldo insuficiente",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun resgatar(
        @Valid @RequestBody request: ResgatarPontosRequest,
        @AuthenticationPrincipal cliente: UsuarioAutenticado,
    ): SaldoFidelidadeResponse = fidelidadeService.resgatar(cliente.id, request)
}
