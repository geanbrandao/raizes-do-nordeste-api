package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.LoginRequest
import com.geanbrandao.raizes.api.dto.LoginResponse
import com.geanbrandao.raizes.api.dto.RefreshRequest
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Rotas de autenticação.
 *
 * Caminho base: /auth
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Login, renovação de token e logout")
class AuthController(
    private val authService: AuthService,
) {

    /**
     * Autentica e devolve os tokens de acesso.
     *
     * POST /auth/login
     *
     * @param request E-mail e senha.
     * @return Access token, refresh token e dados basicos do usuario.
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
        summary = "Autenticar",
        description = "Valida as credenciais e devolve o access token e o refresh token.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Autenticado"),
        ApiResponse(
            responseCode = "401",
            description = "Credenciais invalidas ou conta desativada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.login(request)

    /**
     * Renova o access token a partir de um refresh token valido.
     *
     * POST /auth/refresh
     *
     * @param request Refresh token recebido no login.
     * @return Par novo de tokens. O refresh antigo deixa de valer.
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
        summary = "Renovar acesso",
        description = "Troca um refresh token valido por um par novo. O token antigo e revogado.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Token renovado"),
        ApiResponse(
            responseCode = "401",
            description = "Refresh token invalido, revogado ou expirado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun refresh(@Valid @RequestBody request: RefreshRequest): LoginResponse =
        authService.renovar(request.refreshToken)

    /**
     * Encerra as sessões do usuario autenticado.
     *
     * POST /auth/logout
     *
     * @param usuario Usuario extraido do token.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Encerrar sessão",
        description = "Revoga os refresh tokens do usuario. O access token atual " +
            "continua valido ate expirar.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Sessão encerrada"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun logout(@AuthenticationPrincipal usuario: UsuarioAutenticado) {
        authService.logout(usuario.id)
    }
}
