package com.geanbrandao.raizes.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.exception.ErrorCodes
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * Responde 403 quando a pessoa esta autenticada mas o perfil não permite a operação.
 *
 * A diferença entre 401 e 403 e proposital e e cobrada no plano de testes: 401 e
 * "não sei quem voce e", 403 e "sei quem voce e e voce não pode".
 */
@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    /**
     * Responde 403 no mesmo formato de erro do resto da API.
     *
     * Sem isto o Spring Security devolveria a pagina de erro padrao do container, que
     * não tem `error`, `requestId` nem `path` — e o cliente teria dois formatos de erro
     * para tratar.
     *
     * @param request Requisição recusada.
     * @param response Resposta a ser escrita.
     * @param accessDeniedException Motivo da recusa, vindo do Spring Security.
     */
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = escreverErro(
        objectMapper = objectMapper,
        response = response,
        request = request,
        status = HttpStatus.FORBIDDEN,
        error = ErrorCodes.SEM_PERMISSAO,
        message = "Seu perfil não tem permissão para esta operação.",
    )
}
