package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.CardapioItemRequest
import com.geanbrandao.raizes.api.dto.CardapioItemResponse
import com.geanbrandao.raizes.api.entity.CardapioUnidadeEntity
import com.geanbrandao.raizes.api.entity.ProdutoEntity
import com.geanbrandao.raizes.api.exception.SemPermissaoException
import com.geanbrandao.raizes.api.repository.CardapioUnidadeRepository
import com.geanbrandao.raizes.api.repository.ProdutoRepository
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Regras do cardapio de cada unidade.
 *
 * O cardapio e o que amarra produto a unidade. Se não existe linha aqui, aquela loja
 * simplesmente não vende aquele produto, e o preço praticado e o desta tabela, não o
 * preço base do catalogo. E isso que sustenta a parte do caso que diz que nem toda
 * unidade e igual.
 */
@Service
class CardapioService(
    private val cardapioRepository: CardapioUnidadeRepository,
    private val produtoRepository: ProdutoRepository,
    private val unidadeService: UnidadeService,
    private val produtoService: ProdutoService,
) {

    /**
     * Devolve o cardapio de uma unidade.
     *
     * Por padrão traz so o que esta a venda agora, que e o que interessa ao cliente.
     * A operação da loja pode pedir a lista completa para gerenciar o que esta fora
     * do ar.
     *
     * @param unidadeId Unidade consultada.
     * @param apenasDisponiveis Se false, inclui tambem os itens fora do ar.
     * @return Itens do cardapio, ordenados por categoria e nome.
     * @throws com.geanbrandao.raizes.api.exception.NaoEncontradoException se a unidade não existir.
     */
    @Transactional(readOnly = true)
    fun listar(unidadeId: UUID, apenasDisponiveis: Boolean = true): List<CardapioItemResponse> {
        unidadeService.buscarEntidade(unidadeId)

        val itens = if (apenasDisponiveis) {
            cardapioRepository.findAllByUnidadeIdAndDisponivelTrue(unidadeId)
        } else {
            cardapioRepository.findAllByUnidadeId(unidadeId)
        }
        if (itens.isEmpty()) return emptyList()

        // Uma consulta so para todos os produtos, em vez de uma por item. Com o
        // cardapio inteiro numa tela, o jeito ingenuo viraria dezenas de queries.
        val produtos = produtoRepository.findAllByIdIn(itens.map { it.produtoId })
            .associateBy { it.id }

        return itens
            .mapNotNull { item -> produtos[item.produtoId]?.let { montar(item, it) } }
            .sortedWith(compareBy({ it.categoria }, { it.nome }))
    }

    /**
     * Inclui ou atualiza um produto no cardapio de uma unidade.
     *
     * Serve para os tres casos do dia a dia da loja: colocar um produto novo a venda,
     * reajustar o preço local e tirar do ar o que acabou.
     *
     * @param unidadeId Unidade a ajustar.
     * @param produtoId Produto a incluir ou atualizar.
     * @param request Preço e disponibilidade.
     * @param solicitante Quem esta pedindo a mudança.
     * @return Item do cardapio como ficou.
     * @throws SemPermissaoException se um gerente tentar mexer em unidade que não e a dele.
     * @throws com.geanbrandao.raizes.api.exception.NaoEncontradoException se unidade ou produto não existirem.
     */
    @Transactional
    fun definirItem(
        unidadeId: UUID,
        produtoId: UUID,
        request: CardapioItemRequest,
        solicitante: UsuarioAutenticado,
    ): CardapioItemResponse {
        // A permissão vem antes da busca de proposito: confirmar a existencia da
        // unidade para quem não pode mexer nela ja seria informação demais.
        if (!solicitante.podeAcessarUnidade(unidadeId)) {
            throw SemPermissaoException(
                message = "Voce so pode alterar o cardapio da sua unidade.",
            )
        }

        unidadeService.buscarEntidade(unidadeId)
        val produto = produtoService.buscarEntidade(produtoId)

        val item = cardapioRepository.findByUnidadeIdAndProdutoId(unidadeId, produtoId)
            ?.apply {
                preco = request.preco
                disponivel = request.disponivel
                atualizadoEm = LocalDateTime.now()
            }
            ?: CardapioUnidadeEntity(
                unidadeId = unidadeId,
                produtoId = produtoId,
                preco = request.preco,
                disponivel = request.disponivel,
            )

        return montar(cardapioRepository.save(item), produto)
    }

    /**
     * Tira um produto do cardapio de uma unidade.
     *
     * @param unidadeId Unidade a ajustar.
     * @param produtoId Produto a remover.
     * @param solicitante Quem esta pedindo a remoção.
     * @throws SemPermissaoException se um gerente tentar mexer em unidade que não e a dele.
     */
    @Transactional
    fun removerItem(unidadeId: UUID, produtoId: UUID, solicitante: UsuarioAutenticado) {
        if (!solicitante.podeAcessarUnidade(unidadeId)) {
            throw SemPermissaoException(
                message = "Voce so pode alterar o cardapio da sua unidade.",
            )
        }
        cardapioRepository.findByUnidadeIdAndProdutoId(unidadeId, produtoId)
            ?.let { cardapioRepository.delete(it) }
    }

    private fun montar(item: CardapioUnidadeEntity, produto: ProdutoEntity) = CardapioItemResponse(
        produtoId = produto.id,
        nome = produto.nome,
        descricao = produto.descricao,
        categoria = produto.categoria,
        preco = item.preco,
        sazonal = produto.sazonal,
        disponivel = item.disponivel,
    )
}
