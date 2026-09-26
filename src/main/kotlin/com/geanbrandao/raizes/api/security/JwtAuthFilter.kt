package com.geanbrandao.raizes.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Le o token do header Authorization e coloca o usuario no contexto de segurança.
 *
 * O filtro nunca bloqueia nada por conta propria: se não tem token, ou se o token e
 * invalido, ele so segue em frente sem autenticar. Quem decide se aquela rota podia
 * ser acessada sem autenticação e o SecurityConfig, e quem devolve o 401 e o
 * [RestAuthenticationEntryPoint]. Misturar essas responsabilidades e o caminho mais
 * curto para uma rota publica começar a exigir token sem ninguem perceber.
 */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeadersCustom.AUTHORIZATION)
        if (header != null && header.startsWith(PREFIXO_BEARER)) {
            val token = header.removePrefix(PREFIXO_BEARER).trim()
            jwtService.extrairUsuario(token)?.let { usuario ->
                val authentication = UsernamePasswordAuthenticationToken(
                    usuario,
                    null,
                    listOf(SimpleGrantedAuthority(usuario.perfil.role)),
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val PREFIXO_BEARER = "Bearer "
    }
}

/** Nome de header usado no filtro, isolado para não espalhar string solta pelo codigo. */
object HttpHeadersCustom {
    const val AUTHORIZATION = "Authorization"
}
