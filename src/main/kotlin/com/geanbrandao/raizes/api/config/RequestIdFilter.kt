package com.geanbrandao.raizes.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Da um identificador unico para cada requisição.
 *
 * O id vai para tres lugares: o log (via MDC), o header X-Request-Id da resposta e
 * o corpo do erro padrão. Com isso, quando alguem reclama de um erro, da para achar
 * exatamente aquela requisição no log em vez de caçar por horario.
 *
 * Se o cliente ja mandar um X-Request-Id, a gente aproveita o dele, o que ajuda a
 * rastrear a chamada de ponta a ponta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Request-Id"
        const val ATTRIBUTE = "requestId"
        private const val MDC_KEY = "requestId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put(MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            // Precisa limpar: a thread volta para o pool e atenderia a proxima
            // requisição carregando o id da anterior.
            MDC.remove(MDC_KEY)
        }
    }
}
