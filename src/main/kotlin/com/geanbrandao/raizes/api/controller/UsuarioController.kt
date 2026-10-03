package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.CadastroAceitoResponse
import com.geanbrandao.raizes.api.dto.CadastroClienteRequest
import com.geanbrandao.raizes.api.dto.CadastroOperadorRequest
import com.geanbrandao.raizes.api.dto.ConfirmacaoEmailRequest
import com.geanbrandao.raizes.api.dto.ReenvioCodigoRequest
import com.geanbrandao.raizes.api.dto.UsuarioResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.UsuarioService
import com.geanbrandao.raizes.api.service.VerificacaoEmailService
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
    private val verificacaoEmailService: VerificacaoEmailService,
) {

    /**
     * Cadastra um cliente. Rota publica, e por onde o app cria conta.
     *
     * POST /usuarios
     *
     * Devolve sempre 202 com a mesma mensagem, exista ou não o e-mail. Quem chama
     * não consegue distinguir os dois casos, o que impede usar este endpoint para
     * descobrir quem tem conta na rede. A conta so passa a funcionar depois que o
     * dono do endereco confirmar o codigo.
     *
     * @param request Dados do cadastro.
     * @return Mensagem generica, com 202.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(
        summary = "Cadastrar cliente",
        description = "Cria uma conta de cliente e envia um codigo de verificação.\n\n" +
            "A resposta e sempre a mesma, exista ou não o e-mail informado: isso evita " +
            "que o endpoint seja usado para descobrir quais enderecos tem conta.\n\n" +
            "A conta so consegue fazer login depois de confirmar o codigo em " +
            "POST /usuarios/verificacao.\n\n" +
            "**Em ambiente de desenvolvimento o codigo e sempre `258369`.** " +
            "Ele tambem aparece no log da aplicação.\n\n" +
            "A data de nascimento e opcional e so e usada em campanha segmentada, " +
            "mediante consentimento.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Cadastro recebido"),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun cadastrarCliente(
        @Valid @RequestBody request: CadastroClienteRequest,
    ): CadastroAceitoResponse {
        usuarioService.cadastrarCliente(request)
        return CadastroAceitoResponse()
    }

    /**
     * Confirma o e-mail com o codigo recebido no cadastro.
     *
     * POST /usuarios/verificacao
     *
     * @param request E-mail e codigo de 6 digitos.
     */
    @PostMapping("/verificacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(
        summary = "Confirmar e-mail",
        description = "Valida o codigo de verificação e libera o login da conta.\n\n" +
            "**Em ambiente de desenvolvimento o codigo e sempre `258369`.**\n\n" +
            "Codigo errado e e-mail inexistente devolvem exatamente o mesmo erro, " +
            "pelo mesmo motivo do cadastro.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "E-mail confirmado"),
        ApiResponse(
            responseCode = "400",
            description = "Codigo invalido, expirado ou com tentativas esgotadas",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun confirmarEmail(@Valid @RequestBody request: ConfirmacaoEmailRequest) {
        verificacaoEmailService.confirmar(request.email, request.codigo)
    }

    /**
     * Reenvia o codigo de verificação.
     *
     * POST /usuarios/verificacao/reenvio
     *
     * @param request E-mail da conta.
     * @return Mensagem generica, com 202.
     */
    @PostMapping("/verificacao/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(
        summary = "Reenviar codigo de verificação",
        description = "Emite um codigo novo, se a conta existir e ainda não estiver " +
            "verificada. A resposta e sempre a mesma, nos dois casos.",
    )
    @ApiResponses(ApiResponse(responseCode = "202", description = "Pedido recebido"))
    fun reenviarCodigo(@Valid @RequestBody request: ReenvioCodigoRequest): CadastroAceitoResponse {
        verificacaoEmailService.reenviar(request.email)
        return CadastroAceitoResponse()
    }

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
    @SecurityRequirement(name = "BearerAuth")
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
    @SecurityRequirement(name = "BearerAuth")
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
