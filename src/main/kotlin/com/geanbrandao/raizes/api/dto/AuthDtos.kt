package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.Perfil
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

/**
 * Credenciais do login.
 *
 * @param email E-mail cadastrado.
 * @param senha Senha em texto puro, conferida contra o hash. Nunca e logada nem guardada.
 */
@Schema(description = "Credenciais de acesso")
data class LoginRequest(
    @field:NotBlank(message = "informe o e-mail")
    @field:Email(message = "e-mail invalido")
    @field:Schema(example = "cliente@exemplo.com")
    val email: String,

    @field:NotBlank(message = "informe a senha")
    @field:Schema(example = "Senha@123")
    val senha: String,
)

/**
 * Resumo do usuario devolvido junto com o token.
 *
 * Nunca traz hash de senha nem dado pessoal alem do necessario para a tela inicial.
 */
@Schema(description = "Dados basicos do usuario autenticado")
data class UsuarioResumoResponse(
    val id: UUID,
    val nome: String,
    val email: String,
    val perfil: Perfil,
    @field:Schema(description = "Unidade do operador. Nulo para cliente e admin.")
    val unidadeId: UUID? = null,
)

/**
 * Resposta do login e do refresh.
 *
 * @param accessToken JWT de vida curta, usado no header Authorization.
 * @param refreshToken Token opaco de vida longa, usado so para renovar o access token.
 * @param tokenType Sempre Bearer. Vai explicito para o cliente não precisar supor.
 * @param expiresIn Quantos segundos o access token ainda vale.
 * @param usuario Dados basicos de quem autenticou.
 */
@Schema(description = "Tokens de acesso")
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    @field:Schema(example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(example = "900")
    val expiresIn: Long,
    val usuario: UsuarioResumoResponse,
)

/** Pedido de renovação do access token. */
@Schema(description = "Refresh token para renovar o acesso")
data class RefreshRequest(
    @field:NotBlank(message = "informe o refreshToken")
    val refreshToken: String,
)
