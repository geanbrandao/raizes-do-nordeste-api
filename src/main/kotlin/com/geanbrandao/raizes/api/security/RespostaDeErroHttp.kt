package com.geanbrandao.raizes.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.config.RequestIdFilter
import com.geanbrandao.raizes.api.exception.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant

/**
 * Escreve o erro padrão direto na resposta HTTP.
 *
 * Os handlers de segurança rodam na cadeia de filtros, antes do
 * GlobalExceptionHandler entrar em cena, então eles não conseguem aproveitar o
 * @RestControllerAdvice. Sem este utilitario, o 401 e o 403 sairiam no formato
 * padrão do Spring e quebrariam a promessa de que todo erro da API tem o mesmo
 * corpo.
 *
 * @param objectMapper Serializador ja configurado pelo Spring.
 * @param response Resposta onde o JSON sera escrito.
 * @param request Requisição que falhou, usada para path e requestId.
 * @param status Status HTTP a devolver.
 * @param error Codigo de erro.
 * @param message Mensagem para o usuario final.
 */
fun escreverErro(
    objectMapper: ObjectMapper,
    response: HttpServletResponse,
    request: HttpServletRequest,
    status: HttpStatus,
    error: String,
    message: String,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()
    val corpo = ErrorResponse(
        error = error,
        message = message,
        details = emptyList(),
        timestamp = Instant.now(),
        path = request.requestURI,
        requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String,
    )
    objectMapper.writeValue(response.outputStream, corpo)
}
