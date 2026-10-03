package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.CardapioItemRequest
import com.geanbrandao.raizes.api.dto.CardapioItemResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import com.geanbrandao.raizes.api.service.CardapioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas do cardapio de uma unidade.
 *
 * Caminho base: /unidades/{unidadeId}/cardapio
 *
 * A rota de consulta e publica porque e por ela que o cliente escolhe o que pedir,
 * ainda sem estar logado. As de alteração são da operação da loja.
 */
@RestController
@RequestMapping("/unidades/{unidadeId}/cardapio")
@Tag(name = "Cardapio", description = "O que cada unidade vende e por quanto")
class CardapioController(
    private val cardapioService: CardapioService,
) {

    /**
     * Devolve o cardapio de uma unidade.
     *
     * GET /unidades/{unidadeId}/cardapio
     *
     * @param unidadeId Unidade consultada.
     * @param incluirIndisponiveis Se true, traz tambem o que esta fora do ar.
     * @return Itens do cardapio.
     */
    @GetMapping
    @SecurityRequirements
    @Operation(
        summary = "Consultar cardapio da unidade",
        description = "Devolve o que a unidade vende, com o preço praticado nela. " +
            "Por padrão traz so os itens disponiveis.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Cardapio da unidade"),
        ApiResponse(
            responseCode = "404",
            description = "Unidade não encontrada",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listar(
        @PathVariable unidadeId: UUID,
        @Parameter(description = "Inclui os itens fora do ar, para a operação da loja")
        @RequestParam(defaultValue = "false") incluirIndisponiveis: Boolean,
    ): List<CardapioItemResponse> =
        cardapioService.listar(unidadeId, apenasDisponiveis = !incluirIndisponiveis)

    /**
     * Inclui ou atualiza um produto no cardapio da unidade.
     *
     * PUT /unidades/{unidadeId}/cardapio/{produtoId}
     *
     * @param unidadeId Unidade a ajustar.
     * @param produtoId Produto a incluir ou atualizar.
     * @param request Preço e disponibilidade.
     * @param solicitante Quem esta pedindo a mudança.
     * @return Item do cardapio como ficou.
     */
    @PutMapping("/{produtoId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Definir item do cardapio",
        description = "Coloca um produto a venda na unidade, reajusta o preço local ou " +
            "tira do ar. Gerente so mexe na propria unidade; admin mexe em qualquer uma.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Item definido"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou gerente de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Unidade ou produto não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun definirItem(
        @PathVariable unidadeId: UUID,
        @PathVariable produtoId: UUID,
        @Valid @RequestBody request: CardapioItemRequest,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ): CardapioItemResponse = cardapioService.definirItem(unidadeId, produtoId, request, solicitante)

    /**
     * Tira um produto do cardapio da unidade.
     *
     * DELETE /unidades/{unidadeId}/cardapio/{produtoId}
     *
     * @param unidadeId Unidade a ajustar.
     * @param produtoId Produto a remover.
     * @param solicitante Quem esta pedindo a remoção.
     */
    @DeleteMapping("/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Remover item do cardapio",
        description = "Gerente so mexe na propria unidade; admin mexe em qualquer uma.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Item removido"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão, ou gerente de outra unidade",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun removerItem(
        @PathVariable unidadeId: UUID,
        @PathVariable produtoId: UUID,
        @AuthenticationPrincipal solicitante: UsuarioAutenticado,
    ) = cardapioService.removerItem(unidadeId, produtoId, solicitante)
}
