package com.geanbrandao.raizes.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime
import java.util.UUID

/**
 * Registro de consentimento do titular.
 *
 * A finalidade e obrigatoria e fechada: consentimento dado para uma coisa não vale
 * para outra. A versão guarda qual texto a pessoa aceitou, porque se o documento
 * mudar ela precisa aceitar de novo.
 */
@Schema(description = "Consentimento para tratamento de dados")
data class RegistrarConsentimentoRequest(
    @field:NotBlank(message = "informe a finalidade")
    @field:Pattern(
        regexp = "^(FIDELIDADE|MARKETING|PERFILAMENTO)$",
        message = "finalidades aceitas: FIDELIDADE, MARKETING, PERFILAMENTO",
    )
    @field:Schema(
        description = "FIDELIDADE habilita o acumulo de pontos. MARKETING libera " +
            "campanha por e-mail. PERFILAMENTO permite segmentar por idade e perfil " +
            "de consumo.",
        example = "FIDELIDADE",
    )
    val finalidade: String,

    @field:NotBlank(message = "informe a versão do documento")
    @field:Schema(description = "Versão do texto que a pessoa aceitou", example = "1.0")
    val versaoDocumento: String,
)

/** Consentimento como a API devolve. */
@Schema(description = "Consentimento registrado")
data class ConsentimentoResponse(
    val id: UUID,
    val finalidade: String,
    val versaoDocumento: String,
    val aceitoEm: LocalDateTime,
    @field:Schema(description = "Preenchido na revogação. Nulo enquanto o consentimento vale.")
    val revogadoEm: LocalDateTime? = null,
    val ativo: Boolean,
)
