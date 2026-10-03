package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.CallbackPagamentoRequest
import com.geanbrandao.raizes.api.dto.PagamentoResponse
import com.geanbrandao.raizes.api.dto.SolicitarPagamentoRequest
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.exception.NaoAutenticadoException
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.PagamentoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

/**
 * Rotas de pagamento.
 *
 * Caminho base: /pagamentos, mais a solicitação pendurada no pedido.
 */
@RestController
@Tag(name = "Pagamentos", description = "Solicitação ao gateway simulado e retorno")
class PagamentoController(
    private val pagamentoService: PagamentoService,
    @Value("\${app.pagamento.segredo-callback:}") private val segredoCallback: String,
) {

    /**
     * Solicita o pagamento de um pedido.
     *
     * POST /pedidos/{pedidoId}/pagamentos
     *
     * @param pedidoId Pedido a pagar.
     * @param request Metodo, token e chave de idempotencia.
     * @param solicitante Quem esta pagando.
     * @return Pagamento registrado, com 201.
     */
    @PostMapping("/pedidos/{pedidoId}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Solicitar pagamento",
        description = "Pede a cobrança ao gateway externo e registra o que voltou.\n\n" +
            "O valor cobrado e sempre o total do pedido, calculado pelo servidor.\n\n" +
            "**Desfechos no gateway simulado**, controlados pelo `tokenPagamento`:\n" +
            "- `tok_recusa...` → pagamento RECUSADO, pedido vai para PAGAMENTO_RECUSADO\n" +
            "- `tok_timeout...` → gateway não responde, pagamento fica PENDENTE e o " +
            "pedido não se mexe, aguardando o callback\n" +
            "- qualquer outro valor, ou nenhum → pagamento APROVADO, pedido vai para PAGO\n\n" +
            "Mandar a mesma `chaveIdempotencia` duas vezes devolve o resultado que ja " +
            "existe, em vez de cobrar de novo.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Pagamento processado"),
        ApiResponse(
            responseCode = "404",
            description = "Pedido não encontrado ou não visivel",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Pedido ja pago, ou cancelado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun solicitar(
        @PathVariable pedidoId: UUID,
        @Valid @RequestBody request: SolicitarPagamentoRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PagamentoResponse = pagamentoService.solicitar(pedidoId, request, solicitante)

    /**
     * Consulta um pagamento.
     *
     * GET /pagamentos/{pagamentoId}
     *
     * @param pagamentoId Id do pagamento.
     * @param solicitante Quem esta consultando.
     * @return Pagamento.
     */
    @GetMapping("/pagamentos/{pagamentoId}")
    @SecurityRequirement(name = "Bearer Auth")
    @Operation(
        summary = "Consultar pagamento",
        description = "A visibilidade e a mesma do pedido: quem não ve o pedido não ve " +
            "quanto foi cobrado nele.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pagamento encontrado"),
        ApiResponse(
            responseCode = "404",
            description = "Pagamento não encontrado ou não visivel",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun detalhar(
        @PathVariable pagamentoId: UUID,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PagamentoResponse = pagamentoService.buscarPorId(pagamentoId, solicitante)

    /**
     * Recebe o retorno assincrono do gateway.
     *
     * POST /pagamentos/callback
     *
     * A rota e publica porque quem chama e o gateway, que não tem conta nesta API. Em
     * vez de token, ela e protegida por um segredo combinado, enviado no header
     * `X-Gateway-Assinatura`. A comparação usa [MessageDigest.isEqual], que gasta o
     * mesmo tempo acertando ou errando: comparar string com `==` sai mais cedo no
     * primeiro caractere diferente e entrega o segredo aos poucos.
     *
     * @param assinatura Segredo combinado com o gateway.
     * @param request Pagamento, resultado e dados da transação.
     * @return Pagamento atualizado.
     */
    @PostMapping("/pagamentos/callback")
    @SecurityRequirements
    @Operation(
        summary = "Callback do gateway",
        description = "Resolve um pagamento que ficou pendente porque o gateway não " +
            "respondeu na hora.\n\n" +
            "Protegido pelo header `X-Gateway-Assinatura`, não por token de usuario: " +
            "quem chama e o gateway.\n\n" +
            "Callback repetido para pagamento ja resolvido e ignorado, porque gateway " +
            "reenvia webhook quando não recebe confirmação.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Callback processado ou ignorado"),
        ApiResponse(
            responseCode = "401",
            description = "Assinatura ausente ou invalida",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Pagamento não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun callback(
        @RequestHeader(value = "X-Gateway-Assinatura", required = false) assinatura: String?,
        @Valid @RequestBody request: CallbackPagamentoRequest,
    ): PagamentoResponse {
        exigirAssinaturaValida(assinatura)
        return pagamentoService.processarCallback(request)
    }

    /** Confere o segredo do webhook em tempo constante. */
    private fun exigirAssinaturaValida(assinatura: String?) {
        val esperado = segredoCallback
        if (esperado.isBlank()) {
            throw NaoAutenticadoException(
                error = ErrorCodes.NAO_AUTENTICADO,
                message = "Callback de pagamento não esta configurado neste ambiente.",
            )
        }
        val recebido = assinatura.orEmpty()
        val confere = MessageDigest.isEqual(
            recebido.toByteArray(Charsets.UTF_8),
            esperado.toByteArray(Charsets.UTF_8),
        )
        if (!confere) {
            throw NaoAutenticadoException(
                error = ErrorCodes.NAO_AUTENTICADO,
                message = "Assinatura do gateway invalida.",
            )
        }
    }
}
