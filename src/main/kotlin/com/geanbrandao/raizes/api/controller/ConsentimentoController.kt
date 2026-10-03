package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.ConsentimentoResponse
import com.geanbrandao.raizes.api.dto.RegistrarConsentimentoRequest
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.ConsentimentoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas de consentimento, na linha da LGPD.
 *
 * Caminho base: /consentimentos
 *
 * O titular e sempre quem esta autenticado: ninguem consente nem revoga por outra
 * pessoa.
 */
@RestController
@RequestMapping("/consentimentos")
@Tag(name = "Consentimentos", description = "LGPD: aceite e revogação pelo titular")
class ConsentimentoController(
    private val consentimentoService: ConsentimentoService,
) {

    /**
     * Registra o aceite do titular.
     *
     * POST /consentimentos
     *
     * @param request Finalidade e versão do documento.
     * @param titular Titular extraido do token.
     * @return Consentimento registrado, com 201.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Registrar consentimento",
        description = "Aceitar `FIDELIDADE` liga a conta de pontos. Sem esse aceite, " +
            "pedido pago não gera ponto nenhum.\n\n" +
            "`PERFILAMENTO` e o que permite campanha segmentada por idade.\n\n" +
            "Aceitar de novo o que ja vale não cria registro duplicado.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Consentimento registrado"),
        ApiResponse(
            responseCode = "422",
            description = "Finalidade invalida",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun registrar(
        @Valid @RequestBody request: RegistrarConsentimentoRequest,
        @AuthenticationPrincipal titular: UsuarioAutenticado,
    ): ConsentimentoResponse = consentimentoService.registrar(titular.id, request)

    /**
     * Lista os consentimentos do titular.
     *
     * GET /consentimentos
     *
     * @param titular Titular extraido do token.
     * @return Historico completo, ativos e revogados.
     */
    @GetMapping
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Listar meus consentimentos",
        description = "Traz tambem os revogados: o historico e a prova de que o " +
            "tratamento foi legitimo enquanto durou.",
    )
    @ApiResponses(ApiResponse(responseCode = "200", description = "Consentimentos do titular"))
    fun listar(
        @AuthenticationPrincipal titular: UsuarioAutenticado,
    ): List<ConsentimentoResponse> = consentimentoService.listar(titular.id)

    /**
     * Revoga um consentimento.
     *
     * DELETE /consentimentos/{consentimentoId}
     *
     * @param consentimentoId Consentimento a revogar.
     * @param titular Titular extraido do token.
     * @return Consentimento revogado.
     */
    @DeleteMapping("/{consentimentoId}")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Revogar consentimento",
        description = "Não apaga a linha: marca a data de revogação. Revogar " +
            "`FIDELIDADE` desliga a conta na hora — o saldo ja acumulado continua, mas " +
            "nada novo e creditado.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Consentimento revogado"),
        ApiResponse(
            responseCode = "404",
            description = "Consentimento não encontrado ou de outro titular",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun revogar(
        @PathVariable consentimentoId: UUID,
        @AuthenticationPrincipal titular: UsuarioAutenticado,
    ): ConsentimentoResponse = consentimentoService.revogar(titular.id, consentimentoId)
}
