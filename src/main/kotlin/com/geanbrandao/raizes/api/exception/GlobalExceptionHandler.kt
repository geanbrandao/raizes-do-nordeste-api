package com.geanbrandao.raizes.api.exception

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.geanbrandao.raizes.api.config.RequestIdFilter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

/**
 * Traduz qualquer exceção que escape dos controllers em resposta HTTP no formato
 * padrão de erro.
 *
 * Sem isso o Spring devolveria um corpo generico com stack trace, que vaza detalhe
 * interno e ainda obriga o cliente a tratar cada erro de um jeito. Aqui toda falha
 * sai no mesmo [ErrorResponse], com codigo, mensagem, path e requestId.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Erros de negocio que os services lançam de proposito. */
    @ExceptionHandler(ApiException::class)
    fun tratarApiException(
        ex: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Erro de negocio [{}]: {}", ex.error, ex.message)
        return montar(ex.status, ex.error, ex.message, request, ex.details)
    }

    /**
     * Falha do Bean Validation nos DTOs de entrada.
     *
     * Cada campo reprovado vira um item de details, para o cliente conseguir marcar
     * exatamente o que esta errado no formulario.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun tratarValidacao(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.map {
            ErrorDetail(field = it.field, issue = it.defaultMessage ?: "valor invalido")
        } + ex.bindingResult.globalErrors.map {
            ErrorDetail(field = it.objectName, issue = it.defaultMessage ?: "valor invalido")
        }
        logger.warn("Validação reprovou em {} campo(s)", details.size)
        return montar(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCodes.VALIDACAO,
            "Alguns campos estão invalidos.",
            request,
            details,
        )
    }

    /**
     * JSON que o Jackson não conseguiu ler.
     *
     * O caso mais comum e enum com valor que não existe, tipo canalPedido igual a
     * "IFOOD". A mensagem crua do Jackson vaza nome de classe Java, então ela nunca
     * vai para o cliente: fica so no log.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun tratarJsonInvalido(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Corpo da requisição ilegivel: {}", ex.mostSpecificCause.message)
        val detalhe = extrairCampoDoJson(ex)
        return montar(
            HttpStatus.BAD_REQUEST,
            ErrorCodes.REQUISICAO_INVALIDA,
            "O corpo da requisição esta invalido ou mal formatado.",
            request,
            listOfNotNull(detalhe),
        )
    }

    /** Query param obrigatorio que não veio. */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun tratarParametroAusente(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = montar(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.REQUISICAO_INVALIDA,
        "Faltou um parametro obrigatorio na requisição.",
        request,
        listOf(ErrorDetail(ex.parameterName, "parametro obrigatorio")),
    )

    /** Parametro com tipo errado, tipo UUID mal formado ou enum invalido na query. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun tratarTipoInvalido(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val esperado = ex.requiredType?.let { tipo ->
            if (tipo.isEnum) tipo.enumConstants.joinToString(", ") else tipo.simpleName
        } ?: "valor valido"
        return montar(
            HttpStatus.BAD_REQUEST,
            ErrorCodes.REQUISICAO_INVALIDA,
            "Um parametro veio com formato invalido.",
            request,
            listOf(ErrorDetail(ex.name, "esperado: $esperado")),
        )
    }

    /** Usuario autenticado tentando fazer algo que o perfil dele não permite. */
    @ExceptionHandler(AccessDeniedException::class)
    fun tratarAcessoNegado(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Acesso negado em {}", request.requestURI)
        return montar(
            HttpStatus.FORBIDDEN,
            ErrorCodes.SEM_PERMISSAO,
            "Seu perfil não tem permissão para esta operação.",
            request,
        )
    }

    /**
     * Duas requisições mexeram na mesma linha ao mesmo tempo.
     *
     * Acontece no estoque em horario de pico: dois pedidos baixando o mesmo item no
     * mesmo instante. O lock otimista das entidades faz a segunda gravação falhar em
     * vez de sobrescrever a primeira, e e isso que impede o saldo de ficar errado.
     *
     * Devolve 409 porque não e erro de quem chamou: a requisição estava correta e
     * repetir tem boa chance de funcionar.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun tratarConflitoDeConcorrencia(
        ex: ObjectOptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Conflito de concorrencia em {}: {}", request.requestURI, ex.message)
        return montar(
            HttpStatus.CONFLICT,
            ErrorCodes.CONFLITO_DE_CONCORRENCIA,
            "Outra operação alterou este registro ao mesmo tempo. Tente novamente.",
            request,
        )
    }

    /** Constraint do banco que o codigo deixou passar. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun tratarViolacaoDeIntegridade(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Violação de integridade no banco", ex)
        return montar(
            HttpStatus.CONFLICT,
            ErrorCodes.CONFLITO,
            "A operação conflita com dados que ja existem.",
            request,
        )
    }

    /**
     * Metodo HTTP que a rota não aceita.
     *
     * Sem este tratamento a exceção caia no catch generico e virava 500, dando a
     * entender que a API quebrou quando na verdade a requisição e que estava errada.
     * A resposta inclui os metodos aceitos, para quem esta integrando se corrigir sem
     * precisar abrir a documentação.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun tratarMetodoNaoPermitido(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val aceitos = ex.supportedMethods?.joinToString(", ").orEmpty()
        return montar(
            HttpStatus.METHOD_NOT_ALLOWED,
            ErrorCodes.METODO_NAO_PERMITIDO,
            "Esta rota não aceita o metodo ${ex.method}.",
            request,
            if (aceitos.isBlank()) emptyList() else listOf(ErrorDetail("method", "aceitos: $aceitos")),
        )
    }

    /** Corpo enviado num formato que a API não le, tipo XML ou form-data. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun tratarTipoDeConteudoNaoSuportado(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = montar(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorCodes.TIPO_DE_CONTEUDO_NAO_SUPORTADO,
        "A API espera application/json.",
        request,
    )

    /** Rota que não existe. Sem isso o Spring devolveria um HTML de erro. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun tratarRotaInexistente(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = montar(
        HttpStatus.NOT_FOUND,
        ErrorCodes.NAO_ENCONTRADO,
        "Rota não encontrada.",
        request,
    )

    /**
     * Rede de segurança para o que não foi previsto.
     *
     * Loga a exceção inteira mas devolve mensagem generica, porque detalhe de
     * exceção interna não e assunto do cliente.
     */
    @ExceptionHandler(Exception::class)
    fun tratarErroInesperado(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Erro inesperado em ${request.requestURI}", ex)
        return montar(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCodes.ERRO_INTERNO,
            "Erro interno. Tente novamente em instantes.",
            request,
        )
    }

    /**
     * Monta o corpo do erro no formato padrão.
     *
     * @param status Status HTTP da resposta.
     * @param error Codigo de [ErrorCodes].
     * @param message Mensagem para o usuario final.
     * @param request Requisição que falhou, usada para pegar path e requestId.
     * @param details Problemas por campo, quando houver.
     * @return Resposta pronta para devolver.
     */
    private fun montar(
        status: HttpStatus,
        error: String,
        message: String,
        request: HttpServletRequest,
        details: List<ErrorDetail> = emptyList(),
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(
        ErrorResponse(
            error = error,
            message = message,
            details = details,
            timestamp = Instant.now(),
            path = request.requestURI,
            requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String,
        ),
    )

    /**
     * Tenta descobrir qual campo do JSON causou o erro de leitura.
     *
     * Cobre os dois casos que mais aparecem:
     *
     * - **valor que não existe no enum**, tipo canalPedido igual a "IFOOD";
     * - **campo obrigatorio ausente**. Em Kotlin, propriedade não anulavel que não
     *   vem no JSON estoura no Jackson antes de chegar no Bean Validation, então
     *   ela nunca vira 422. Sem este tratamento, a resposta seria um 400 seco,
     *   sem dizer qual campo faltou.
     */
    private fun extrairCampoDoJson(ex: HttpMessageNotReadableException): ErrorDetail? {
        val causa = ex.cause

        if (causa is InvalidFormatException) {
            val campo = causa.caminho()
            val aceitos = causa.targetType?.takeIf { it.isEnum }
                ?.enumConstants?.joinToString(", ")
            if (campo.isNotBlank()) {
                return ErrorDetail(
                    field = campo,
                    issue = aceitos?.let { "valor invalido, aceitos: $it" } ?: "valor invalido",
                )
            }
        }

        // InvalidFormatException tambem e MismatchedInputException, por isso este
        // ramo vem depois.
        if (causa is MismatchedInputException) {
            val campo = causa.caminho()
            if (campo.isNotBlank()) {
                return ErrorDetail(field = campo, issue = "campo obrigatorio")
            }
        }

        return null
    }

    /** Monta o caminho do campo como ele apareceu no JSON, ex.: itens[0].quantidade. */
    private fun MismatchedInputException.caminho(): String = path.joinToString("") { referencia ->
        referencia.fieldName?.let { nome -> if (path.first() === referencia) nome else ".$nome" }
            ?: "[${referencia.index}]"
    }
}
