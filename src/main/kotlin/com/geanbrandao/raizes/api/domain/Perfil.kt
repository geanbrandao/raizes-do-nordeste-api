package com.geanbrandao.raizes.api.domain

/**
 * Perfis de acesso do sistema.
 *
 * Cada perfil vira uma role do Spring Security (prefixo ROLE_) no momento em que
 * o token e validado. A regra geral e: quem e da rede (ADMIN) enxerga tudo, quem
 * e da unidade (GERENTE, ATENDENTE, COZINHA) so enxerga a propria unidade, e o
 * CLIENTE so enxerga o que e dele.
 */
enum class Perfil {

    /** Matriz da franqueadora. Cadastra unidades e produtos, le auditoria. */
    ADMIN,

    /** Gerente de uma unidade. Cuida do cardapio local, estoque e pedidos dela. */
    GERENTE,

    /** Atendente de balcão. Cria pedido para o cliente e movimenta estoque. */
    ATENDENTE,

    /** Cozinha. So mexe no status do pedido (em preparo, pronto). */
    COZINHA,

    /** Cliente final. Faz o proprio pedido e ve o proprio saldo de pontos. */
    CLIENTE;

    /** Nome da role como o Spring Security espera ver. */
    val role: String get() = "ROLE_$name"

    /** Perfis que trabalham dentro de uma unidade e por isso tem unidade vinculada. */
    val ehOperador: Boolean get() = this in setOf(GERENTE, ATENDENTE, COZINHA)
}
