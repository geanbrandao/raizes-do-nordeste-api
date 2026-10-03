package com.geanbrandao.raizes.api.controller

import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.dto.ProdutoRequest
import com.geanbrandao.raizes.api.dto.ProdutoResponse
import com.geanbrandao.raizes.api.exception.ErrorResponse
import com.geanbrandao.raizes.api.service.ProdutoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Rotas do catalogo de produtos da rede.
 *
 * Caminho base: /produtos
 *
 * Aqui mora o catalogo da matriz. O que o cliente ve e compra vem do cardapio da
 * unidade, em /unidades/{id}/cardapio.
 */
@RestController
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Catalogo da rede")
class ProdutoController(
    private val produtoService: ProdutoService,
) {

    /**
     * Lista os produtos ativos do catalogo.
     *
     * GET /produtos?categoria=TAPIOCA&page=1&limit=10
     *
     * @param categoria Filtro opcional por categoria.
     * @param page Pagina, começando em 1.
     * @param limit Itens por pagina.
     * @return Pagina de produtos.
     */
    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Listar produtos", description = "Catalogo da rede, com filtro opcional por categoria.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de produtos"),
        ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun listar(
        @Parameter(description = "Filtra por categoria, ex.: TAPIOCA") @RequestParam(required = false) categoria: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
    ): PaginaResponse<ProdutoResponse> = produtoService.listar(
        categoria,
        ParametrosPaginacao.de(page, limit, Sort.by("categoria", "nome")),
    )

    /**
     * Detalhe de um produto.
     *
     * GET /produtos/{produtoId}
     *
     * @param produtoId Id do produto.
     * @return Dados do produto.
     */
    @GetMapping("/{produtoId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Detalhar produto",
        description = "Dados de um produto do catalogo da rede. Traz tambem o que foi inativado, com ativo = false, porque pedido antigo aponta para ele.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Produto encontrado"),
        ApiResponse(
            responseCode = "404",
            description = "Produto não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun detalhar(@PathVariable produtoId: UUID): ProdutoResponse =
        produtoService.buscarPorId(produtoId)

    /**
     * Cadastra um produto no catalogo.
     *
     * POST /produtos
     *
     * @param request Dados do produto.
     * @return Produto criado, com 201.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cadastrar produto", description = "Perfis ADMIN e GERENTE.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Produto cadastrado"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Campos invalidos",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun criar(@Valid @RequestBody request: ProdutoRequest): ProdutoResponse =
        produtoService.criar(request)

    /**
     * Atualiza um produto do catalogo.
     *
     * PUT /produtos/{produtoId}
     *
     * @param produtoId Id do produto.
     * @param request Dados novos.
     * @return Produto atualizado.
     */
    @PutMapping("/{produtoId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Atualizar produto", description = "Perfis ADMIN e GERENTE.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Produto atualizado"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Produto não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun atualizar(
        @PathVariable produtoId: UUID,
        @Valid @RequestBody request: ProdutoRequest,
    ): ProdutoResponse = produtoService.atualizar(produtoId, request)

    /**
     * Inativa um produto.
     *
     * DELETE /produtos/{produtoId}
     *
     * Nunca apaga a linha: pedido antigo aponta para o produto, e apagar quebraria o
     * historico de venda.
     *
     * @param produtoId Id do produto.
     */
    @DeleteMapping("/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
        summary = "Inativar produto",
        description = "Exclusivo do perfil ADMIN. Marca como inativo em vez de apagar, " +
            "para não quebrar os pedidos antigos que apontam para o produto.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Produto inativado"),
        ApiResponse(
            responseCode = "403",
            description = "Perfil sem permissão",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Produto não encontrado",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    fun inativar(@PathVariable produtoId: UUID) = produtoService.inativar(produtoId)
}
