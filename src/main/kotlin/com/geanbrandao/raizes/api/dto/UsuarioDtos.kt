package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.Perfil
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/**
 * Cadastro de cliente.
 *
 * [dataNascimento] e opcional de proposito. E dado pessoal usado so para segmentar
 * campanha, e a LGPD pede minimização: quem não quiser participar disso não precisa
 * informar, e o cadastro funciona do mesmo jeito.
 */
@Schema(description = "Cadastro de um novo cliente")
data class CadastroClienteRequest(
    @field:NotBlank(message = "informe o nome")
    @field:Size(min = 3, max = 120, message = "o nome deve ter entre 3 e 120 caracteres")
    @field:Schema(example = "Maria Cliente")
    val nome: String,

    @field:NotBlank(message = "informe o e-mail")
    @field:Email(message = "e-mail invalido")
    @field:Size(max = 254, message = "e-mail muito longo")
    @field:Schema(example = "maria@exemplo.com")
    val email: String,

    @field:NotBlank(message = "informe a senha")
    @field:Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres")
    @field:Pattern(
        // Exige maiuscula, minuscula e numero. O limite de 72 em cima vem do BCrypt,
        // que ignora silenciosamente o que passar disso.
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "a senha precisa ter ao menos uma letra maiuscula, uma minuscula e um numero",
    )
    @field:Schema(example = "Senha@123")
    val senha: String,

    @field:Past(message = "a data de nascimento deve estar no passado")
    @field:Schema(description = "Opcional. So usado em campanha segmentada, com consentimento.")
    val dataNascimento: LocalDate? = null,
)

/**
 * Cadastro de operador, feito por admin ou gerente.
 *
 * Diferente do cliente, operador nasce amarrado a uma unidade e com perfil definido
 * por quem cadastrou.
 */
@Schema(description = "Cadastro de um operador de unidade")
data class CadastroOperadorRequest(
    @field:NotBlank(message = "informe o nome")
    @field:Size(min = 3, max = 120, message = "o nome deve ter entre 3 e 120 caracteres")
    val nome: String,

    @field:NotBlank(message = "informe o e-mail")
    @field:Email(message = "e-mail invalido")
    val email: String,

    @field:NotBlank(message = "informe a senha")
    @field:Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres")
    val senha: String,

    @field:NotNull(message = "informe o perfil")
    @field:Schema(description = "GERENTE, ATENDENTE ou COZINHA", example = "ATENDENTE")
    val perfil: Perfil,

    @field:NotNull(message = "informe a unidade")
    val unidadeId: UUID,
)

/** Perfil completo do usuario autenticado. */
@Schema(description = "Perfil do usuario autenticado")
data class UsuarioResponse(
    val id: UUID,
    val nome: String,
    val email: String,
    val perfil: Perfil,
    val unidadeId: UUID? = null,
    val dataNascimento: LocalDate? = null,
    val ativo: Boolean,
)
