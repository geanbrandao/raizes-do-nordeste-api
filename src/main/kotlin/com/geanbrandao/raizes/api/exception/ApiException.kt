package com.geanbrandao.raizes.api.exception

import org.springframework.http.HttpStatus

/**
 * Exceção base de todo erro de negocio da API.
 *
 * Os services lançam uma das subclasses em vez de exceção generica. Quem traduz
 * isso em resposta HTTP e o [GlobalExceptionHandler], então o service não precisa
 * saber nada de HTTP.
 *
 * @param error Codigo de [ErrorCodes].
 * @param message Mensagem em português para o usuario final.
 * @param status Status HTTP que a API deve devolver.
 * @param details Problemas por campo, quando fizer sentido.
 */
open class ApiException(
    val error: String,
    override val message: String,
    val status: HttpStatus,
    val details: List<ErrorDetail> = emptyList(),
) : RuntimeException(message)

/** 400 - a requisição em si esta malformada. */
class BadRequestException(
    error: String = ErrorCodes.REQUISICAO_INVALIDA,
    message: String,
    details: List<ErrorDetail> = emptyList(),
) : ApiException(error, message, HttpStatus.BAD_REQUEST, details)

/** 422 - a requisição esta bem formada, mas o conteudo não passa nas regras. */
class ValidacaoException(
    error: String = ErrorCodes.VALIDACAO,
    message: String,
    details: List<ErrorDetail> = emptyList(),
) : ApiException(error, message, HttpStatus.UNPROCESSABLE_ENTITY, details)

/** 401 - não da para saber quem esta chamando. */
class NaoAutenticadoException(
    error: String = ErrorCodes.NAO_AUTENTICADO,
    message: String = "E preciso estar autenticado para acessar este recurso.",
) : ApiException(error, message, HttpStatus.UNAUTHORIZED)

/**
 * 403 - sabe-se quem esta chamando, mas o perfil não permite.
 *
 * Usar so quando o recurso existe e o problema e permissão. Se o recurso não
 * existe, o certo e [NaoEncontradoException], senão a API vira um oraculo que
 * conta o que existe no banco para quem não deveria saber.
 */
class SemPermissaoException(
    error: String = ErrorCodes.SEM_PERMISSAO,
    message: String = "Seu perfil não tem permissão para esta operação.",
) : ApiException(error, message, HttpStatus.FORBIDDEN)

/** 404 - o recurso não existe. */
class NaoEncontradoException(
    error: String = ErrorCodes.NAO_ENCONTRADO,
    message: String,
) : ApiException(error, message, HttpStatus.NOT_FOUND)

/** 409 - existe, esta valido, mas bate numa regra de negocio. */
class ConflitoException(
    error: String = ErrorCodes.CONFLITO,
    message: String,
    details: List<ErrorDetail> = emptyList(),
) : ApiException(error, message, HttpStatus.CONFLICT, details)
