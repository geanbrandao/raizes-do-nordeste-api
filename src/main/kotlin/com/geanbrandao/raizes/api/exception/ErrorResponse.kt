package com.geanbrandao.raizes.api.exception

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Detalhe de um campo que reprovou na validação.
 *
 * @param field Nome do campo, no mesmo caminho que veio no request (ex.: itens[0].quantidade).
 * @param issue Explicação curta do problema naquele campo.
 */
@Schema(description = "Detalhe de um campo invalido")
data class ErrorDetail(
    @field:Schema(example = "itens[0].quantidade")
    val field: String,
    @field:Schema(example = "deve ser maior que zero")
    val issue: String,
)

/**
 * Corpo devolvido em toda resposta de erro da API.
 *
 * O formato e sempre o mesmo, em qualquer rota e qualquer status. Isso evita que o
 * cliente precise tratar cada endpoint de um jeito, e e o padrão exigido no roteiro
 * do projeto.
 *
 * @param error Codigo legivel por maquina, de [ErrorCodes]. E por ele que o cliente decide o que fazer.
 * @param message Mensagem em português, para mostrar para uma pessoa.
 * @param details Lista de problemas por campo. Vem vazia quando o erro não e de validação.
 * @param timestamp Momento em que o erro aconteceu, em UTC.
 * @param path Rota que foi chamada.
 * @param requestId Identificador da requisição, para casar a reclamação do usuario com o log.
 */
@Schema(description = "Formato padrão de erro da API")
data class ErrorResponse(
    @field:Schema(example = "ESTOQUE_INSUFICIENTE")
    val error: String,
    @field:Schema(example = "Não ha quantidade suficiente para um ou mais itens.")
    val message: String,
    val details: List<ErrorDetail> = emptyList(),
    @field:Schema(example = "2026-02-05T12:00:00Z")
    val timestamp: Instant = Instant.now(),
    @field:Schema(example = "/pedidos")
    val path: String? = null,
    @field:Schema(example = "3f1a2b4c-5d6e-7f80-91a2-b3c4d5e6f708")
    val requestId: String? = null,
)
