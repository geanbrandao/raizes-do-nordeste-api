package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.StatusPedido
import com.geanbrandao.raizes.api.dto.AtualizarStatusRequest
import com.geanbrandao.raizes.api.dto.CancelarPedidoRequest
import com.geanbrandao.raizes.api.dto.CriarPedidoRequest
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.dto.PedidoResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.PedidoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas de pedido. E o fluxo critico do sistema.
 *
 * Caminho base: /pedidos
 */
@RestController
@RequestMapping("/pedidos")
@Tag(name = "Pedidos", description = "Fluxo critico: criação, status e cancelamento")
class PedidoController(
    private val pedidoService: PedidoService,
) {

    /**
     * Cria um pedido.
     *
     * POST /pedidos
     *
     * @param request Unidade, canal, itens e, opcionalmente, o cliente.
     * @param solicitante Quem esta criando.
     * @return Pedido criado, com 201.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Criar pedido",
        description = "Perfis CLIENTE e ATENDENTE.\n\n" +
            "O `canalPedido` e obrigatorio: e por ele que a matriz acompanha a venda " +
            "por canal.\n\n" +
            "O preço **não** vem no request. O servidor le o preço do cardapio da " +
            "unidade e congela no item, então reajuste posterior não muda pedido antigo.\n\n" +
            "Na criação o estoque ja e baixado. Se faltar saldo em algum item, nada e " +
            "baixado e a resposta lista todos os itens em falta de uma vez.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Pedido criado em AGUARDANDO_PAGAMENTO"),
        ApiResponse(
            responseCode = "400",
            description = "canalPedido invalido ou corpo mal formado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Operador tentando registrar pedido de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Estoque insuficiente, ou unidade inativa",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos, ou item fora do cardapio da unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun criar(
        @Valid @RequestBody request: CriarPedidoRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PedidoResponse = pedidoService.criar(request, solicitante)

    /**
     * Lista pedidos com filtros.
     *
     * GET /pedidos?canalPedido=TOTEM&status=PAGO&page=1&limit=10
     *
     * @param canalPedido Filtro por canal.
     * @param status Filtro por situação.
     * @param unidadeId Filtro por unidade. Só surte efeito para ADMIN.
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de pedidos visiveis a essa pessoa.
     */
    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Listar pedidos",
        description = "O que cada um enxerga depende do perfil: cliente ve os pedidos " +
            "dele, operador ve os da unidade dele, admin ve a rede toda. O filtro de " +
            "unidade nunca amplia essa visibilidade.\n\n" +
            "O filtro por canal atende a rastreabilidade por canal exigida no projeto.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de pedidos"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listar(
        @Parameter(description = "APP, TOTEM, BALCAO, PICKUP ou WEB")
        @RequestParam(required = false) canalPedido: CanalPedido?,
        @RequestParam(required = false) status: StatusPedido?,
        @Parameter(description = "So surte efeito para ADMIN")
        @RequestParam(required = false) unidadeId: UUID?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PaginaResponse<PedidoResponse> = pedidoService.listar(
        canalPedido = canalPedido,
        status = status,
        unidadeId = unidadeId,
        pageable = ParametrosPaginacao.de(page, limit),
        solicitante = solicitante,
    )

    /**
     * Detalhe de um pedido.
     *
     * GET /pedidos/{pedidoId}
     *
     * @param pedidoId Id do pedido.
     * @param solicitante Quem esta consultando.
     * @return Pedido.
     */
    @GetMapping("/{pedidoId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Detalhar pedido",
        description = "Pedido que não pertence a quem esta consultando devolve 404, não " +
            "403: dizer 'existe mas não e seu' ja confirmaria a existencia dele.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pedido encontrado"),
        ApiResponse(
            responseCode = "404",
            description = "Pedido não encontrado ou não visivel a esse perfil",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun detalhar(
        @PathVariable pedidoId: UUID,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PedidoResponse = pedidoService.buscarPorId(pedidoId, solicitante)

    /**
     * Avança o status do pedido.
     *
     * PATCH /pedidos/{pedidoId}/status
     *
     * @param pedidoId Id do pedido.
     * @param request Status desejado.
     * @param solicitante Quem esta mudando.
     * @return Pedido atualizado.
     */
    @PatchMapping("/{pedidoId}/status")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Avançar status do pedido",
        description = "Caminho normal: AGUARDANDO_PAGAMENTO → PAGO → EM_PREPARO → " +
            "PRONTO → ENTREGUE.\n\n" +
            "Transição fora do fluxo devolve 409 e a resposta lista quais status são " +
            "possiveis a partir do atual. Cada perfil so move o pedido para os status " +
            "que são trabalho dele: cozinha prepara e marca pronto, atendente entrega.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Status atualizado"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil não pode fazer essa transição",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Pedido não encontrado ou não visivel",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Transição invalida no fluxo",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun atualizarStatus(
        @PathVariable pedidoId: UUID,
        @Valid @RequestBody request: AtualizarStatusRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PedidoResponse = pedidoService.atualizarStatus(pedidoId, request.status, solicitante)

    /**
     * Cancela o pedido e devolve o estoque.
     *
     * POST /pedidos/{pedidoId}/cancelamento
     *
     * @param pedidoId Id do pedido.
     * @param request Motivo do cancelamento, opcional.
     * @param solicitante Quem esta cancelando.
     * @return Pedido cancelado.
     */
    @PostMapping("/{pedidoId}/cancelamento")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Cancelar pedido",
        description = "Devolve ao estoque exatamente o que o pedido tinha baixado.\n\n" +
            "So vale ate PRONTO: depois disso a comida ja foi preparada, e devolver o " +
            "estoque seria mentira contabil.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pedido cancelado e estoque devolvido"),
        ApiResponse(
            responseCode = "404",
            description = "Pedido não encontrado ou não visivel",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Pedido ja passou do ponto de cancelamento",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun cancelar(
        @PathVariable pedidoId: UUID,
        @Valid @RequestBody(required = false) request: CancelarPedidoRequest?,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): PedidoResponse = pedidoService.cancelar(pedidoId, solicitante)
}
