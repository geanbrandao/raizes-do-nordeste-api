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
