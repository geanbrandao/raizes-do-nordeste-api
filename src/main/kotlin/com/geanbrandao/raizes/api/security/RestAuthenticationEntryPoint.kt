package com.geanbrandao.raizes.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.exception.ErrorCodes
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Responde 401 quando a requisição chega sem autenticação valida.
 *
 * O padrão do Spring aqui seria devolver uma pagina de login ou um desafio de HTTP
 * Basic, o que não faz sentido numa API REST. Este componente troca isso pelo JSON
 * de erro padrão.
 */
@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    /**
     * Responde 401 no mesmo formato de erro do resto da API.
     *
     * Vale tambem para rota que não existe, quando a requisição vem sem token: a
     * resposta e 401, não 404. Devolver 404 ali diria a quem não se autenticou quais
     * caminhos existem e quais não, o que e um mapa da API de graca.
     *
     * @param request Requisição sem autenticação valida.
     * @param response Resposta a ser escrita.
     * @param authException Motivo da recusa, vindo do Spring Security.
     */
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = escreverErro(
        objectMapper = objectMapper,
        response = response,
        request = request,
        status = HttpStatus.UNAUTHORIZED,
        error = ErrorCodes.NAO_AUTENTICADO,
        message = "E preciso estar autenticado para acessar este recurso.",
    )
}
