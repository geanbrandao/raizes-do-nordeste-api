package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.CadastroClienteRequest
import com.geanbrandao.raizes.api.dto.CadastroOperadorRequest
import com.geanbrandao.raizes.api.dto.UsuarioResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.UsuarioService
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Rotas de usuario.
 *
 * Caminho base: /usuarios
 */
@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuarios", description = "Cadastro de clientes e operadores")
class UsuarioController(
    private val usuarioService: UsuarioService,
) {

    /**
     * Cadastra um cliente. Rota publica, e por onde o app cria conta.
     *
     * POST /usuarios
     *
     * @param request Dados do cadastro.
     * @return Cliente criado, com 201.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(
        summary = "Cadastrar cliente",
        description = "Cria uma conta de cliente. A data de nascimento e opcional e " +
            "so e usada em campanha segmentada, mediante consentimento.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Cliente cadastrado"),
        ApiResponse(
            responseCode = "409",
            description = "E-mail ja cadastrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun cadastrarCliente(
        @Valid @RequestBody request: CadastroClienteRequest,
    ): UsuarioResponse = usuarioService.cadastrarCliente(request)

    /**
     * Cadastra um operador de unidade. So admin e gerente.
     *
     * POST /usuarios/operadores
     *
     * @param request Dados do operador.
     * @param solicitante Quem esta cadastrando, extraido do token.
     * @return Operador criado, com 201.
     */
    @PostMapping("/operadores")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Cadastrar operador",
        description = "Cria um operador (GERENTE, ATENDENTE ou COZINHA) vinculado a uma " +
            "unidade. Gerente so cadastra na propria unidade; admin cadastra em qualquer uma.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Operador cadastrado"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou gerente tentando cadastrar em outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun cadastrarOperador(
        @Valid @RequestBody request: CadastroOperadorRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): UsuarioResponse = usuarioService.cadastrarOperador(request, solicitante)

    /**
     * Devolve o perfil de quem esta autenticado.
     *
     * GET /usuarios/me
     *
     * @param usuario Usuario extraido do token.
     * @return Dados do proprio usuario.
     */
    @GetMapping("/me")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(summary = "Meu perfil", description = "Dados do usuario autenticado.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Perfil do usuario"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun meuPerfil(
        @AuthenticationPrincipal usuario: UsuarioAutenticado,
    ): UsuarioResponse = usuarioService.buscarPorId(usuario.id)
}
