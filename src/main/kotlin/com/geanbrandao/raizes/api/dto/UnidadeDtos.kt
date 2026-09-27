package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.domain.TipoOperacaoUnidade
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

/** Cadastro ou atualização de uma unidade da rede. */
@Schema(description = "Dados de uma unidade")
data class UnidadeRequest(
    @field:NotBlank(message = "informe o nome")
    @field:Size(min = 3, max = 120, message = "o nome deve ter entre 3 e 120 caracteres")
    @field:Schema(example = "Raizes Boa Viagem")
    val nome: String,

    @field:NotBlank(message = "informe a cidade")
    @field:Size(max = 120, message = "cidade muito longa")
    @field:Schema(example = "Recife")
    val cidade: String,

    @field:NotBlank(message = "informe a UF")
    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "a UF tem 2 letras")
    @field:Schema(example = "PE")
    val uf: String,

    @field:NotNull(message = "informe o tipo de operação")
    @field:Schema(
        description = "COMPLETA prepara o cardapio inteiro; REDUZIDA prepara so parte dele",
        example = "COMPLETA",
    )
    val tipoOperacao: TipoOperacaoUnidade,
)

/** Unidade como a API devolve. */
@Schema(description = "Unidade da rede")
data class UnidadeResponse(
    val id: UUID,
    val nome: String,
    val cidade: String,
    val uf: String,
    val tipoOperacao: TipoOperacaoUnidade,
    val ativa: Boolean,
)
