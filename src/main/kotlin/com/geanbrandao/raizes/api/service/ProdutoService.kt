package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.ProdutoRequest
import com.geanbrandao.raizes.api.dto.ProdutoResponse
import com.geanbrandao.raizes.api.entity.ProdutoEntity
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.repository.ProdutoRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/** Regras do catalogo de produtos da rede. */
@Service
class ProdutoService(
    private val produtoRepository: ProdutoRepository,
) {

    /**
     * Lista os produtos ativos do catalogo, com filtro opcional por categoria.
     *
     * @param categoria Categoria a filtrar, ou null para todas.
     * @param pageable Pagina e tamanho da pagina.
     * @return Pagina de produtos.
     */
    @Transactional(readOnly = true)
    fun listar(categoria: String?, pageable: Pageable): PaginaResponse<ProdutoResponse> {
        val pagina = if (categoria.isNullOrBlank()) {
            produtoRepository.findAllByAtivoTrue(pageable)
        } else {
            produtoRepository.findAllByCategoriaAndAtivoTrue(categoria.trim().uppercase(), pageable)
        }
        return PaginaResponse.de(pagina) { it.paraResponse() }
    }

    /**
     * Busca um produto pelo id.
     *
     * @param id Id do produto.
     * @return Dados do produto.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional(readOnly = true)
    fun buscarPorId(id: UUID): ProdutoResponse = buscarEntidade(id).paraResponse()

    /**
     * Cadastra um produto no catalogo.
     *
     * @param request Dados do produto.
     * @return Produto criado.
     */
    @Transactional
    fun criar(request: ProdutoRequest): ProdutoResponse = produtoRepository.save(
        ProdutoEntity(
            nome = request.nome.trim(),
            descricao = request.descricao?.trim(),
            categoria = request.categoria.trim().uppercase(),
            precoBase = request.precoBase,
            sazonal = request.sazonal,
        ),
    ).paraResponse()

    /**
     * Atualiza um produto do catalogo.
     *
     * @param id Id do produto.
     * @param request Dados novos.
     * @return Produto atualizado.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional
    fun atualizar(id: UUID, request: ProdutoRequest): ProdutoResponse {
        val produto = buscarEntidade(id)
        produto.nome = request.nome.trim()
        produto.descricao = request.descricao?.trim()
        produto.categoria = request.categoria.trim().uppercase()
        produto.precoBase = request.precoBase
        produto.sazonal = request.sazonal
        produto.atualizadoEm = LocalDateTime.now()
        return produtoRepository.save(produto).paraResponse()
    }

    /**
     * Inativa um produto.
     *
     * Não apaga a linha de proposito. Produto apagado quebraria todo pedido antigo
     * que aponta para ele, e o historico de venda deixaria de fechar.
     *
     * @param id Id do produto.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional
    fun inativar(id: UUID) {
        val produto = buscarEntidade(id)
        produto.ativo = false
        produto.atualizadoEm = LocalDateTime.now()
        produtoRepository.save(produto)
    }

    /**
     * Busca a entidade ou estoura 404.
     *
     * @param id Id do produto.
     * @return Entidade encontrada.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional(readOnly = true)
    fun buscarEntidade(id: UUID): ProdutoEntity = produtoRepository.findById(id)
        .orElseThrow {
            NaoEncontradoException(
                error = ErrorCodes.PRODUTO_NAO_ENCONTRADO,
                message = "Produto não encontrado.",
            )
        }

    private fun ProdutoEntity.paraResponse() = ProdutoResponse(
        id = id,
        nome = nome,
        descricao = descricao,
        categoria = categoria,
        precoBase = precoBase,
        sazonal = sazonal,
        ativo = ativo,
    )
}
