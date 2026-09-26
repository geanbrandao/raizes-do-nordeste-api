package com.geanbrandao.raizes.api.security

import com.geanbrandao.raizes.api.domain.Perfil
import java.util.UUID

/**
 * Quem esta fazendo a requisição.
 *
 * Vira o principal do Spring Security e chega nos controllers via
 * @AuthenticationPrincipal. Os tres dados vem do proprio JWT, então saber quem e a
 * pessoa e qual a unidade dela não custa uma ida ao banco a cada requisição.
 *
 * @param id Id do usuario.
 * @param perfil Perfil de acesso.
 * @param unidadeId Unidade do operador. Nulo para cliente e admin.
 */
data class UsuarioAutenticado(
    val id: UUID,
    val perfil: Perfil,
    val unidadeId: UUID? = null,
) {
    /** Admin enxerga a rede inteira, sem ficar preso a uma unidade. */
    val ehAdmin: Boolean get() = perfil == Perfil.ADMIN

    /**
     * Diz se a pessoa pode mexer em dados de uma unidade.
     *
     * Admin pode em qualquer uma. Operador so na dele. Cliente em nenhuma.
     *
     * @param unidade Unidade que se quer acessar.
     * @return true se o acesso e permitido.
     */
    fun podeAcessarUnidade(unidade: UUID): Boolean = when {
        ehAdmin -> true
        perfil.ehOperador -> unidadeId == unidade
        else -> false
    }
}
